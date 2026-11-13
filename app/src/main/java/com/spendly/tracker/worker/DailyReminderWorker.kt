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
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Fires once a day around 22:00 (EOD) to nudge the user to review the day's transactions.
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /**
         * v2 of the unique work name. The original "daily_reminder_periodic" work was created
         * without a flex window, so WorkManager could (and did) run it at any time of the day.
         * The 22:00 initial delay only positioned the 24h period, not the run itself. Bumping
         * the unique name lets this re-anchored schedule take effect for existing installs;
         * canceling the legacy name stops the old, un-anchored work.
         */
        private const val WORK_NAME_PERIODIC = "daily_reminder_periodic_v2"
        private const val WORK_NAME_LEGACY = "daily_reminder_periodic"
        private const val CHANNEL_ID = "daily_reminder_channel"
        private const val CHANNEL_NAME = "Daily Reminder"
        private const val REMINDER_HOUR = 22

        /**
         * Flex window (in minutes) for the periodic run. WorkManager runs periodic work inside
         * the flex window at the END of each period; with no flex set, that window is the entire
         * 24h period — the reason the notification fired at seemingly random times. Pinning the
         * window to the last [FLEX_MINUTES] keeps each run inside [21:30, 22:00] local time.
         */
        private const val FLEX_MINUTES = 30L

        fun enqueuePeriodic(context: Context) {
            val workManager = WorkManager.getInstance(context)
            // One-time migration: drop the legacy un-anchored schedule if it is still around.
            workManager.cancelUniqueWork(WORK_NAME_LEGACY)

            val now = LocalDateTime.now()
            val nextRun = now.toLocalDate().atTime(LocalTime.of(REMINDER_HOUR, 0))
                .let { if (it.isAfter(now)) it else it.plusDays(1) }
            val initialDelay = Duration.between(now, nextRun).toMillis()

            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(
                24, TimeUnit.HOURS,
                FLEX_MINUTES, TimeUnit.MINUTES
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            workManager
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
            val count = transactionRepository.getTodayTransactionCount()
            showNotification(count)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun showNotification(count: Int) {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminder to review today's transactions"
        }
        notificationManager.createNotificationChannel(channel)

        val title = "Time to review today's spending"
        val content = if (count > 0) {
            "You've logged $count transaction${if (count == 1) "" else "s"} today. Take a look."
        } else {
            "No transactions logged today. Take a look and add anything you missed."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TRANSACTIONS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            WORK_NAME_PERIODIC.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(WORK_NAME_PERIODIC.hashCode(), notification)
    }
}
