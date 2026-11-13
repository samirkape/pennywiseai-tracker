package com.spendly.tracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendly.tracker.core.Constants
import com.spendly.tracker.ui.screens.savings.computeSafeToSpend
import com.spendly.tracker.worker.SavingsGoalLoader
import com.spendly.tracker.worker.SavingsGoalSnapshot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Refreshes each savings widget from the same goal snapshot the Savings page and reminder
 * workers use, honouring the goal selected for that widget id.
 */
@HiltWorker
class SavingsWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val goalLoader: SavingsGoalLoader,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "savings_widget_update"
        private const val WORK_NAME_PERIODIC = "savings_widget_update_periodic"

        fun enqueueOneShot(context: Context) {
            if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) return
            val request = OneTimeWorkRequestBuilder<SavingsWidgetUpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePeriodicUpdate(context: Context) {
            if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) return
            val request = PeriodicWorkRequestBuilder<SavingsWidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) {
            return Result.success()
        }
        return try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(SavingsWidget::class.java)
            if (glanceIds.isEmpty()) return Result.success()

            val widget = SavingsWidget()
            // Widgets that follow the active goal share one snapshot per run.
            val snapshots = mutableMapOf<String?, SavingsGoalSnapshot>()
            for (glanceId in glanceIds) {
                val appWidgetId = manager.getAppWidgetId(glanceId)
                val goalId = SavingsWidgetConfigStore.goalId(applicationContext, appWidgetId)
                val snapshot = snapshots.getOrPut(goalId) {
                    goalLoader.load(goalId = goalId)
                }
                SavingsWidgetDataStore.update(
                    applicationContext,
                    appWidgetId,
                    snapshot.toWidgetData(),
                )
                widget.update(applicationContext, glanceId)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun SavingsGoalSnapshot.toWidgetData(): SavingsWidgetData {
        val progressPercent = if (plan.requiredTotalSavings.signum() > 0) {
            plan.actualSaved
                .divide(plan.requiredTotalSavings, 4, RoundingMode.HALF_UP)
                .toFloat()
                .times(100f)
                .coerceIn(0f, 100f)
        } else {
            0f
        }
        return SavingsWidgetData(
            targetPercent = percent,
            saved = plan.actualSaved,
            required = plan.requiredTotalSavings,
            monthlyNeeded = plan.monthlySavingsNeeded,
            remaining = plan.remainingToSave,
            progressPercent = progressPercent,
            currentPercent = plan.currentPercent,
            endMonth = endMonth.toString(),
            currency = currency,
            hasIncome = plan.hasIncome,
            goalName = goal.name.trim(),
            isFixedGoal = plan.isFixedAmount,
            safeToSpendPerDay = computeSafeToSpend(plan)?.perDay,
            daysToDeadline = ChronoUnit.DAYS.between(
                LocalDate.now(),
                plan.periodEnd,
            ).coerceAtLeast(0L),
            isComplete = plan.remainingToSave.signum() <= 0,
        )
    }
}
