package com.spendly.tracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendly.tracker.MainActivity
import com.spendly.tracker.R
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.receiver.SavingsNotificationActionReceiver
import com.spendly.tracker.ui.screens.savings.SavingsReminderSchedule
import com.spendly.tracker.ui.screens.savings.nextSavingsReminderRun
import com.spendly.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Recurring nudge that reports progress toward the percentage savings goal and, when the user is
 * behind, the monthly amount needed to get back on track. Runs on every day the user picked.
 */
@HiltWorker
class SavingsGoalReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val goalLoader: SavingsGoalLoader,
    private val userPreferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SavingsGoalReminder"
        private const val WORK_NAME_LEGACY = "savings_goal_reminder_periodic"
        private const val WORK_NAME_PREFIX = "savings_goal_reminder_periodic_day"
        private const val CHANNEL_ID = "savings_goal_reminder_channel"
        private val NOTIFICATION_ID = WORK_NAME_LEGACY.hashCode()

        private fun workName(day: DayOfWeek): String = "${WORK_NAME_PREFIX}_${day.value}"

        /** Schedules one weekly reminder per selected day at the chosen hour. */
        fun enqueuePeriodic(
            context: Context,
            schedule: SavingsReminderSchedule = SavingsReminderSchedule(),
        ) {
            val workManager = WorkManager.getInstance(context)
            // Drop every previously scheduled day (including the old single-day job) so
            // deselecting a day actually stops its reminder.
            workManager.cancelUniqueWork(WORK_NAME_LEGACY)
            DayOfWeek.values().forEach { workManager.cancelUniqueWork(workName(it)) }

            val now = LocalDateTime.now()
            val hour = schedule.normalizedHour
            schedule.normalizedDays.forEach { day ->
                val nextRun = nextSavingsReminderRun(
                    now = now,
                    schedule = SavingsReminderSchedule(daysOfWeek = setOf(day), hour = hour),
                )
                val initialDelay = Duration.between(now, nextRun).toMillis()

                val request = PeriodicWorkRequestBuilder<SavingsGoalReminderWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .build()
                    )
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    workName(day),
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    request,
                )
            }
        }

        fun cancelPeriodic(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(WORK_NAME_LEGACY)
            DayOfWeek.values().forEach { workManager.cancelUniqueWork(workName(it)) }
        }
    }

    override suspend fun doWork(): Result = try {
        showNotification()
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Reminder notification failed", e)
        Result.failure()
    }

    private suspend fun showNotification() {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (userPreferencesRepository.getSavingsNotificationSnoozeUntil().first() > System.currentTimeMillis()) {
            return
        }

        val snapshot = goalLoader.load()
        val plan = snapshot.plan
        val percent = snapshot.percent
        val currency = snapshot.currency
        val endMonth = snapshot.endMonth

        val title = context.getString(R.string.savings_reminder_notification_title)
        val content = if (plan.isFixedAmount) {
            val saved = CurrencyFormatter.formatCurrencyWhole(plan.actualSaved, currency)
            val target = CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency)
            when {
                plan.remainingToSave.signum() <= 0 ->
                    context.getString(R.string.savings_reminder_fixed_complete, saved)
                plan.monthlySavingsNeeded.signum() > 0 -> context.getString(
                    R.string.savings_reminder_fixed_behind,
                    saved,
                    target,
                    CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
                    formatMonth(endMonth.atEndOfMonth()),
                )
                else -> context.getString(
                    R.string.savings_reminder_fixed_progress,
                    saved,
                    target,
                )
            }
        } else {
            when {
                !plan.hasIncome -> context.getString(
                    R.string.savings_reminder_no_income,
                    percent,
                )
                plan.currentPercent == null -> context.getString(
                    R.string.savings_reminder_no_rate,
                    percent,
                )
                plan.currentPercent >= percent -> context.getString(
                    R.string.savings_reminder_ahead,
                    plan.currentPercent.roundToInt(),
                    percent,
                )
                else -> context.getString(
                    R.string.savings_reminder_behind,
                    plan.currentPercent.roundToInt(),
                    CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
                    percent,
                    formatMonth(endMonth.atEndOfMonth()),
                )
            }
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.savings_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.savings_reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_SAVINGS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                context.getString(R.string.savings_notification_action_log),
                actionPendingIntent(
                    context = context,
                    action = SavingsNotificationActionReceiver.ACTION_LOG_SAVINGS,
                    requestCode = NOTIFICATION_ID + 1,
                ),
            )
            .addAction(
                0,
                context.getString(R.string.savings_notification_action_adjust),
                actionPendingIntent(
                    context = context,
                    action = SavingsNotificationActionReceiver.ACTION_ADJUST_GOAL,
                    requestCode = NOTIFICATION_ID + 2,
                ),
            )
            .addAction(
                0,
                context.getString(R.string.savings_notification_action_snooze),
                actionPendingIntent(
                    context = context,
                    action = SavingsNotificationActionReceiver.ACTION_SNOOZE,
                    requestCode = NOTIFICATION_ID + 3,
                ),
            )
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, SavingsNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(SavingsNotificationActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatMonth(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
}
