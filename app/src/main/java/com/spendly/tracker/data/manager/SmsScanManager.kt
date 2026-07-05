package com.spendly.tracker.data.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.spendly.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SMS scanning operations using WorkManager.
 * Uses OptimizedSmsReaderWorker for parallel processing and progress tracking.
 */
@Singleton
class SmsScanManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        /** Minimum interval between background auto-scans (launch / resume). */
        private const val AUTO_SCAN_MIN_INTERVAL_MS = 15 * 60 * 1000L

        @Volatile
        private var lastAutoScanEnqueueMs = 0L
    }

    /**
     * Starts a one-time SMS scan using the optimized worker for faster processing.
     */
    fun startSmsLoggingScan() {
        scheduleIncrementalScan(replaceExisting = true)
    }

    /**
     * Enqueues an incremental SMS scan.
     *
     * @param forceResync Reprocess all messages (clears DB first in worker).
     * @param replaceExisting If true, cancels any in-flight scan (manual sync). If false,
     *   skips enqueue when a scan is already running (auto-scan).
     * @return true if work was enqueued, false if throttled or skipped.
     */
    fun scheduleIncrementalScan(
        forceResync: Boolean = false,
        replaceExisting: Boolean = false,
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (!replaceExisting && !forceResync) {
            val now = System.currentTimeMillis()
            if (now - lastAutoScanEnqueueMs < AUTO_SCAN_MIN_INTERVAL_MS) {
                return false
            }
            lastAutoScanEnqueueMs = now
        }

        val inputData = workDataOf(
            OptimizedSmsReaderWorker.INPUT_FORCE_RESYNC to forceResync
        )
        val smsReaderWork = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .setInputData(inputData)
            .addTag("sms_logging")
            .addTag("optimized_sms_processing")
            .addTag(OptimizedSmsReaderWorker.WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            OptimizedSmsReaderWorker.WORK_NAME,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            smsReaderWork
        )
        return true
    }

    /**
     * Enqueues a scan that starts exactly at [fromTimestamp] (epoch millis).
     * Used after a backup restore to pick up only transactions newer than the backup.
     */
    fun scheduleScanFromTimestamp(fromTimestamp: Long): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val inputData = workDataOf(
            OptimizedSmsReaderWorker.INPUT_SCAN_FROM_TIMESTAMP to fromTimestamp
        )
        val smsReaderWork = OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>()
            .setInputData(inputData)
            .addTag("sms_logging")
            .addTag("optimized_sms_processing")
            .addTag(OptimizedSmsReaderWorker.WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            OptimizedSmsReaderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            smsReaderWork
        )
        return true
    }

    /**
     * Cancels any ongoing SMS scanning work.
     */
    fun cancelSmsScanning() {
        workManager.cancelUniqueWork(OptimizedSmsReaderWorker.WORK_NAME)
    }

    /**
     * Gets the work info for monitoring progress of the SMS scan.
     */
    fun getSmsScanWorkInfo() = workManager.getWorkInfosByTagLiveData("optimized_sms_processing")
}