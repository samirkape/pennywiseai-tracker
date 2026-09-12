package com.spendly.tracker.data.backup

import android.content.Context
import android.os.Environment
import android.util.Log
import com.spendly.tracker.data.database.SpendlyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupExporter: BackupExporter,
    private val database: SpendlyDatabase
) {
    companion object {
        private const val TAG = "AutoBackupManager"
        private const val AUTO_BACKUP_DIR = "auto_backups"
        private const val AUTO_BACKUP_FILENAME = "spendly_auto_backup.spendlybackup"
        private const val DOWNLOADS_BACKUP_FILENAME = "Spendly_Backup.spendlybackup"
        private val MIN_BACKUP_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)
    }

    fun getAutoBackupDir(): File {
        return File(context.getExternalFilesDir(null), AUTO_BACKUP_DIR).also { it.mkdirs() }
    }

    fun getAutoBackupFile(): File {
        return File(getAutoBackupDir(), AUTO_BACKUP_FILENAME)
    }

    suspend fun performAutoBackup(): Result<File> {
        return try {
            val hasData = database.transactionDao().getAllTransactions().first().isNotEmpty()
            if (!hasData) {
                Log.i(TAG, "Skipping auto-backup: no transactions in database")
                return Result.success(getAutoBackupFile())
            }

            val existingFile = getAutoBackupFile()
            if (existingFile.exists()) {
                val age = System.currentTimeMillis() - existingFile.lastModified()
                if (age < MIN_BACKUP_INTERVAL_MS) {
                    Log.i(TAG, "Skipping auto-backup: last backup is ${age}ms old")
                    return Result.success(existingFile)
                }
            }

            val result = backupExporter.exportBackup(ExportPrivacy.FULL)
            when (result) {
                is ExportResult.Success -> {
                    val autoBackupFile = getAutoBackupFile()
                    result.file.copyTo(autoBackupFile, overwrite = true)
                    result.file.delete()
                    Log.i(TAG, "Auto-backup saved to ${autoBackupFile.absolutePath}")
                    Result.success(autoBackupFile)
                }
                is ExportResult.Error -> {
                    Log.e(TAG, "Auto-backup export failed: ${result.message}")
                    Result.failure(Exception(result.message))
                }
                else -> Result.failure(Exception("Unknown export result"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            Result.failure(e)
        }
    }

    fun hasAutoBackup(): Boolean {
        val file = getAutoBackupFile()
        return file.exists() && file.length() > 0
    }

    fun getAutoBackupUri(): android.net.Uri? {
        val file = getAutoBackupFile()
        if (!hasAutoBackup()) return null
        return android.net.Uri.fromFile(file)
    }

    fun getAutoBackupTimestamp(): Long? {
        val file = getAutoBackupFile()
        if (!hasAutoBackup()) return null
        return file.lastModified()
    }

    fun saveToDownloads(sourceFile: File): Result<File> {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val spendlyDir = File(downloadsDir, "Spendly")
            spendlyDir.mkdirs()

            val timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss")
            )
            val destFile = File(spendlyDir, "Spendly_Backup_$timestamp.spendlybackup")
            sourceFile.copyTo(destFile, overwrite = false)
            Log.i(TAG, "Backup saved to Downloads: ${destFile.absolutePath}")
            Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to Downloads", e)
            Result.failure(e)
        }
    }
}
