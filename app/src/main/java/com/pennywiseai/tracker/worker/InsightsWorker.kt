package com.pennywiseai.tracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pennywiseai.tracker.domain.usecase.ComputeInsightsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class InsightsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val computeInsightsUseCase: ComputeInsightsUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting insights computation in background")
            computeInsightsUseCase()
            Log.d(TAG, "Insights computation completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error computing insights", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "InsightsWorker"
        const val WORK_NAME = "insights_computation_work"

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<InsightsWorker>()
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<InsightsWorker>(12, TimeUnit.HOURS)
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

