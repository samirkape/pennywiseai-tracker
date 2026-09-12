package com.spendly.tracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.backup.AutoBackupManager
import com.spendly.tracker.data.backup.BackupImporter
import com.spendly.tracker.data.backup.ImportResult
import com.spendly.tracker.data.backup.ImportStrategy
import com.spendly.tracker.data.database.SpendlyDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AutoRestoreViewModel @Inject constructor(
    private val autoBackupManager: AutoBackupManager,
    private val backupImporter: BackupImporter,
    private val database: SpendlyDatabase
) : ViewModel() {

    data class RestorePromptState(
        val shouldShow: Boolean = false,
        val backupTimestamp: String? = null,
        val isRestoring: Boolean = false,
        val restoreComplete: Boolean = false,
        val errorMessage: String? = null
    )

    private val _restorePromptState = MutableStateFlow(RestorePromptState())
    val restorePromptState: StateFlow<RestorePromptState> = _restorePromptState.asStateFlow()

    fun checkForAutoBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hasData = !database.transactionDao().getAllTransactions().first().isEmpty()
                if (hasData) return@launch

                if (!autoBackupManager.hasAutoBackup()) return@launch

                val timestampMs = autoBackupManager.getAutoBackupTimestamp()
                val formattedDate = timestampMs?.let {
                    SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(it))
                }

                _restorePromptState.value = RestorePromptState(
                    shouldShow = true,
                    backupTimestamp = formattedDate
                )
            } catch (e: Exception) {
                Log.e("AutoRestoreViewModel", "Failed to check for auto-backup", e)
            }
        }
    }

    fun restoreFromAutoBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            _restorePromptState.value = _restorePromptState.value.copy(isRestoring = true, errorMessage = null)
            try {
                val uri = autoBackupManager.getAutoBackupUri()
                if (uri == null) {
                    _restorePromptState.value = _restorePromptState.value.copy(
                        isRestoring = false,
                        errorMessage = "Backup file not found"
                    )
                    return@launch
                }

                val result = backupImporter.importBackup(uri, ImportStrategy.REPLACE_ALL)
                when (result) {
                    is ImportResult.Success -> {
                        _restorePromptState.value = RestorePromptState(
                            shouldShow = false,
                            restoreComplete = true
                        )
                    }
                    is ImportResult.Error -> {
                        _restorePromptState.value = _restorePromptState.value.copy(
                            isRestoring = false,
                            errorMessage = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoRestoreViewModel", "Auto-restore failed", e)
                _restorePromptState.value = _restorePromptState.value.copy(
                    isRestoring = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun dismissRestorePrompt() {
        _restorePromptState.value = RestorePromptState(shouldShow = false)
    }
}
