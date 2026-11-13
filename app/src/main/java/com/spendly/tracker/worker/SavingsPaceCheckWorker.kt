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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendly.tracker.MainActivity
import com.spendly.tracker.R
import com.spendly.tracker.core.Constants
import com.spendly.tracker.data.preferences.SavingsPaceAlertState
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.receiver.SavingsNotificationActionReceiver
import com.spendly.tracker.ui.screens.savings.SavingsPace
import com.spendly.tracker.ui.screens.savings.SavingsPaceStatus
import com.spendly.tracker.ui.screens.savings.evaluateSavingsPace
import com.spendly.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Evaluates the savings goal after a new transaction lands and raises at most one smart alert:
 * a milestone celebration, a deadline reminder, or a "you're behind pace" nudge.
 *
 * Runs as a debounced one-shot so bursts of transactions collapse into a single check. The
 * behind-pace alert has a cooldown and milestones/deadlines are de-duplicated, so the user
 * is never spammed by repeated evaluations.
 */
@HiltWorker
class SavingsPaceCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val goalLoader: SavingsGoalLoader,
    private val userPreferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    private data class Alert(
        val id: Int,
        val title: String,
        val text: String,
    )

    companion object {
        private const val WORK_NAME = "savings_pace_check"
        private const val CHANNEL_ID = "savings_pace_alerts_channel"
        private const val BASE_NOTIFICATION_ID = 76130

        /** Minimum gap between two behind-pace alerts. */
        private const val BEHIND_COOLDOWN_MS = 12 * 60 * 60 * 1000L

        /** How close to the deadline the one-time reminder fires. */
        private const val DEADLINE_WINDOW_DAYS = 7L

        /**
         * Enqueues a debounced pace evaluation after transaction ingestion. Replacing the
         * previous request keeps a burst of transactions from running the check many times.
         */
        fun enqueueOneShot(context: Context) {
            if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) return
            val request = OneTimeWorkRequestBuilder<SavingsPaceCheckWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result = try {
        if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) {
            return Result.success()
        }
        // Reuse the reminder opt-in so savings notifications stay a single user choice.
        if (!userPreferencesRepository.getSavingsGoalReminderEnabled().first()) {
            return Result.success()
        }
        if (!canPostNotifications()) {
            return Result.success()
        }
        if (userPreferencesRepository.getSavingsNotificationSnoozeUntil().first() >
            System.currentTimeMillis()
        ) {
            return Result.success()
        }

        val snapshot = goalLoader.load()
        val state = userPreferencesRepository.getSavingsPaceAlertState()
        val pace = evaluateSavingsPace(
            plan = snapshot.plan,
            lastMilestoneNotified = state.lastMilestone,
        )
        val now = System.currentTimeMillis()

        val alert = when {
            pace.milestoneCrossed != null ->
                milestoneAlert(
                    pace = pace,
                    targetPercent = snapshot.percent,
                    currency = snapshot.currency,
                    isFixedGoal = snapshot.plan.isFixedAmount,
                    targetAmount = snapshot.plan.targetAmount,
                )
            snapshot.plan.remainingToSave.signum() > 0 &&
                shouldNotifyDeadline(pace, state, snapshot.endMonth.toString()) ->
                deadlineAlert(
                    pace = pace,
                    targetPercent = snapshot.percent,
                    currency = snapshot.currency,
                    endMonth = snapshot.endMonth.toString(),
                    monthlySavingsNeeded = snapshot.plan.monthlySavingsNeeded,
                    isFixedGoal = snapshot.plan.isFixedAmount,
                    targetAmount = snapshot.plan.targetAmount,
                )
            shouldNotifyBehind(pace, state, now) ->
                behindAlert(
                    pace = pace,
                    targetPercent = snapshot.percent,
                    currency = snapshot.currency,
                    isFixedGoal = snapshot.plan.isFixedAmount,
                    targetAmount = snapshot.plan.targetAmount,
                )
            else -> null
        }

        val updated = state.copy(
            lastAlertAtMillis = if (alert != null) now else state.lastAlertAtMillis,
            lastMilestone = pace.milestoneCrossed?.coerceAtLeast(state.lastMilestone)
                ?: state.lastMilestone,
            deadlineNotifiedMonth = if (alert?.id == ID_DEADLINE) {
                snapshot.endMonth.toString()
            } else {
                state.deadlineNotifiedMonth
            },
        )
        if (updated != state) {
            userPreferencesRepository.saveSavingsPaceAlertState(updated)
        }

        alert?.let(::showNotification)
        Result.success()
    } catch (e: Exception) {
        Log.e("SavingsPaceCheck", "Pace check failed", e)
        Result.retry()
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun shouldNotifyDeadline(
        pace: SavingsPace,
        state: SavingsPaceAlertState,
        endMonth: String,
    ): Boolean =
        pace.daysToDeadline in 0..DEADLINE_WINDOW_DAYS &&
            state.deadlineNotifiedMonth != endMonth

    private fun shouldNotifyBehind(
        pace: SavingsPace,
        state: SavingsPaceAlertState,
        now: Long,
    ): Boolean =
        pace.isBehind && now - state.lastAlertAtMillis >= BEHIND_COOLDOWN_MS

    private fun milestoneAlert(
        pace: SavingsPace,
        targetPercent: Int,
        currency: String,
        isFixedGoal: Boolean,
        targetAmount: BigDecimal,
    ): Alert {
        val milestone = pace.milestoneCrossed ?: 0
        val saved = CurrencyFormatter.formatCurrencyWhole(pace.actualSaved, currency)
        val text = if (isFixedGoal) {
            val target = CurrencyFormatter.formatCurrencyWhole(targetAmount, currency)
            if (milestone >= 100) {
                applicationContext.getString(
                    R.string.savings_pace_milestone_complete_fixed,
                    saved,
                    target,
                )
            } else {
                applicationContext.getString(
                    R.string.savings_pace_milestone_progress_fixed,
                    saved,
                    milestone,
                    target,
                )
            }
        } else if (milestone >= 100) {
            applicationContext.getString(
                R.string.savings_pace_milestone_complete,
                saved,
                targetPercent,
            )
        } else {
            applicationContext.getString(
                R.string.savings_pace_milestone_progress,
                saved,
                milestone,
                targetPercent,
            )
        }
        return Alert(
            id = ID_MILESTONE,
            title = applicationContext.getString(R.string.savings_pace_milestone_title),
            text = text,
        )
    }

    private fun deadlineAlert(
        pace: SavingsPace,
        targetPercent: Int,
        currency: String,
        endMonth: String,
        monthlySavingsNeeded: BigDecimal,
        isFixedGoal: Boolean,
        targetAmount: BigDecimal,
    ): Alert {
        val days = pace.daysToDeadline.coerceAtLeast(0).toInt()
        val dayText = applicationContext.resources.getQuantityString(
            R.plurals.savings_pace_days_left,
            days,
            days,
        )
        val perMonth = CurrencyFormatter.formatCurrencyWhole(monthlySavingsNeeded, currency)
        val text = if (isFixedGoal) {
            applicationContext.getString(
                R.string.savings_pace_deadline_text_fixed,
                dayText,
                CurrencyFormatter.formatCurrencyWhole(targetAmount, currency),
                formatGoalMonth(endMonth),
                perMonth,
            )
        } else {
            applicationContext.getString(
                R.string.savings_pace_deadline_text,
                dayText,
                targetPercent,
                formatGoalMonth(endMonth),
                perMonth,
            )
        }
        return Alert(
            id = ID_DEADLINE,
            title = applicationContext.getString(R.string.savings_pace_deadline_title),
            text = text,
        )
    }

    private fun behindAlert(
        pace: SavingsPace,
        targetPercent: Int,
        currency: String,
        isFixedGoal: Boolean,
        targetAmount: BigDecimal,
    ): Alert {
        val gap = CurrencyFormatter.formatCurrencyWhole(pace.gap.abs(), currency)
        val status = applicationContext.getString(
            if (pace.status == SavingsPaceStatus.CRITICAL) {
                R.string.savings_pace_behind_critical
            } else {
                R.string.savings_pace_behind
            }
        )
        val text = if (isFixedGoal) {
            applicationContext.getString(
                R.string.savings_pace_behind_text_fixed,
                status,
                CurrencyFormatter.formatCurrencyWhole(targetAmount, currency),
                gap,
            )
        } else {
            applicationContext.getString(
                R.string.savings_pace_behind_text,
                status,
                targetPercent,
                gap,
            )
        }
        return Alert(
            id = ID_BEHIND,
            title = applicationContext.getString(R.string.savings_pace_behind_title),
            text = text,
        )
    }

    private fun showNotification(alert: Alert) {
        val context = applicationContext
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.savings_pace_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.savings_pace_channel_description)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_SAVINGS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                context.getString(R.string.savings_notification_action_log),
                actionPendingIntent(
                    context,
                    alert.id,
                    SavingsNotificationActionReceiver.ACTION_LOG_SAVINGS,
                ),
            )
            .addAction(
                0,
                context.getString(R.string.savings_notification_action_adjust),
                actionPendingIntent(
                    context,
                    alert.id,
                    SavingsNotificationActionReceiver.ACTION_ADJUST_GOAL,
                ),
            )
            .addAction(
                0,
                context.getString(R.string.savings_notification_action_snooze),
                actionPendingIntent(
                    context,
                    alert.id,
                    SavingsNotificationActionReceiver.ACTION_SNOOZE,
                ),
            )
            .addAction(0, context.getString(R.string.savings_pace_view_plan), pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BASE_NOTIFICATION_ID + alert.id, notification)
    }

    private fun actionPendingIntent(
        context: Context,
        alertId: Int,
        action: String,
    ): PendingIntent {
        val notificationId = BASE_NOTIFICATION_ID + alertId
        val intent = Intent(context, SavingsNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(SavingsNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatGoalMonth(raw: String): String {
        val month = runCatching { YearMonth.parse(raw) }.getOrNull() ?: return raw
        return month.atEndOfMonth()
            .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
    }
}

private const val ID_MILESTONE = 1
private const val ID_DEADLINE = 2
private const val ID_BEHIND = 3
