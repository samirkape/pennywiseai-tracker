package com.spendly.tracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.spendly.tracker.data.repository.PrepaidExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Fires once a day at 9 AM to notify the user about prepaid plans that
 * are expiring within 7 days, giving them time to decide on renewal.
 */
@HiltWorker
class PrepaidRenewalReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prepaidExpenseRepository: PrepaidExpenseRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME_PERIODIC = "prepaid_renewal_reminder_periodic"
        private const val CHANNEL_ID = "prepaid_renewal_channel"
        private const val CHANNEL_NAME = "Prepaid Plan Renewals"
        private const val REMINDER_HOUR = 9
        // Offset prevents collision with other notification IDs in the app
        private const val NOTIFICATION_ID_OFFSET = 50_000
        private const val RENEWAL_WINDOW_DAYS = 7

        fun enqueuePeriodic(context: Context) {
            val now = LocalDateTime.now()
            var nextRun = now.toLocalDate().atTime(LocalTime.of(REMINDER_HOUR, 0))
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelay = Duration.between(now, nextRun).toMillis()

            val request = PeriodicWorkRequestBuilder<PrepaidRenewalReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val plans = prepaidExpenseRepository.getUpcomingRenewals(withinDays = RENEWAL_WINDOW_DAYS)
            val today = LocalDate.now()
            for (plan in plans) {
                val daysLeft = ChronoUnit.DAYS.between(today, plan.endDate)
                showRenewalNotification(
                    planId = plan.id,
                    merchantName = plan.merchantName,
                    daysLeft = daysLeft
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun showRenewalNotification(planId: Long, merchantName: String, daysLeft: Long) {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when prepaid plans are about to expire"
            }
        )

        val title = when {
            daysLeft <= 0L -> "$merchantName plan expires today"
            daysLeft == 1L -> "$merchantName plan expires tomorrow"
            else -> "$merchantName plan expires in $daysLeft days"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_SUBSCRIPTIONS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_OFFSET + planId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Tap to renew and avoid service interruption.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_OFFSET + planId.toInt(), notification)
    }
}

