package com.spendly.tracker.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spendly.tracker.MainActivity
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Handles the quick actions on savings notifications: opening a pre-filled
 * transaction flow, jumping straight to the goal editor, and snoozing alerts.
 */
class SavingsNotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SavingsNotificationEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    companion object {
        const val ACTION_LOG_SAVINGS = "com.spendly.tracker.ACTION_LOG_SAVINGS"
        const val ACTION_ADJUST_GOAL = "com.spendly.tracker.ACTION_ADJUST_SAVINGS_GOAL"
        const val ACTION_SNOOZE = "com.spendly.tracker.ACTION_SNOOZE_SAVINGS_ALERT"
        const val EXTRA_NOTIFICATION_ID = "savings_notification_id"
        private const val SNOOZE_DAYS = 7L
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LOG_SAVINGS -> openApp(
                context = context,
                addTransaction = true,
                editGoal = false,
            )
            ACTION_ADJUST_GOAL -> openApp(
                context = context,
                addTransaction = false,
                editGoal = true,
            )
            ACTION_SNOOZE -> snooze(context, intent)
        }
    }

    private fun openApp(context: Context, addTransaction: Boolean, editGoal: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (addTransaction) putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
            if (editGoal) {
                putExtra(MainActivity.EXTRA_OPEN_SAVINGS, true)
                putExtra(MainActivity.EXTRA_EDIT_SAVINGS_GOAL, true)
            }
        }
        context.startActivity(intent)
    }

    private fun snooze(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
        }
        receiverScope.launch {
            val preferences = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SavingsNotificationEntryPoint::class.java,
            ).userPreferencesRepository()
            preferences.saveSavingsNotificationSnoozeUntil(
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(SNOOZE_DAYS),
            )
        }
    }
}
