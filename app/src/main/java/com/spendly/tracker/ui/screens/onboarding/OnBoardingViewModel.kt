package com.spendly.tracker.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.spendly.tracker.data.manager.SmsScanManager
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnBoardingStep {
    WELCOME,
    PERMISSIONS,
    SMS_SCAN
}

data class OnBoardingUiState(
    val currentStep: OnBoardingStep = OnBoardingStep.WELCOME,
    val smsPermissionGranted: Boolean = false,
    val smsPermissionSkipped: Boolean = false,
    val isScanning: Boolean = false,
    val scanTotal: Int = 0,
    val scanProcessed: Int = 0,
    val scanParsed: Int = 0,
    val scanSaved: Int = 0,
    val scanTimeElapsed: Long = 0L,
    val scanEstimatedRemaining: Long = 0L,
    val scanCompleted: Boolean = false,
    val isCompleting: Boolean = false
)

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val smsScanManager: SmsScanManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnBoardingUiState())
    val uiState: StateFlow<OnBoardingUiState> = _uiState.asStateFlow()

    fun onSmsPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                smsPermissionGranted = granted,
                smsPermissionSkipped = !granted
            )
        }
    }

    fun skipSmsPermission() {
        _uiState.update { it.copy(smsPermissionSkipped = true) }
    }

    fun navigateToStep(step: OnBoardingStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun goToNextStep() {
        val nextStep = when (_uiState.value.currentStep) {
            OnBoardingStep.WELCOME -> OnBoardingStep.PERMISSIONS
            OnBoardingStep.PERMISSIONS -> OnBoardingStep.SMS_SCAN
            OnBoardingStep.SMS_SCAN -> OnBoardingStep.SMS_SCAN
        }
        _uiState.update { it.copy(currentStep = nextStep) }
    }

    fun goToPreviousStep() {
        val previousStep = when (_uiState.value.currentStep) {
            OnBoardingStep.WELCOME -> OnBoardingStep.WELCOME
            OnBoardingStep.PERMISSIONS -> OnBoardingStep.WELCOME
            OnBoardingStep.SMS_SCAN -> OnBoardingStep.PERMISSIONS
        }
        _uiState.update { it.copy(currentStep = previousStep) }
    }

    fun startSmsScan() {
        smsScanManager.startSmsLoggingScan()
        _uiState.update { it.copy(isScanning = true, scanCompleted = false) }
        observeScanProgress()
    }

    private fun observeScanProgress() {
        val workManager = WorkManager.getInstance(context)
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData(OptimizedSmsReaderWorker.WORK_NAME)
                .asFlow()
                .collect { workInfos ->
                    val workInfo = workInfos?.firstOrNull() ?: return@collect

                    val progress = workInfo.progress
                    val total = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, 0)
                    val processed = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, 0)
                    val parsed = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_PARSED, 0)
                    val saved = progress.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, 0)
                    val elapsed = progress.getLong(OptimizedSmsReaderWorker.PROGRESS_TIME_ELAPSED, 0L)
                    val remaining = progress.getLong(OptimizedSmsReaderWorker.PROGRESS_ESTIMATED_TIME_REMAINING, 0L)

                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            _uiState.update {
                                it.copy(
                                    isScanning = true,
                                    scanTotal = total,
                                    scanProcessed = processed,
                                    scanParsed = parsed,
                                    scanSaved = saved,
                                    scanTimeElapsed = elapsed,
                                    scanEstimatedRemaining = remaining
                                )
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val outputTotal = workInfo.outputData.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, total)
                            val outputProcessed = workInfo.outputData.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, processed)
                            val outputParsed = workInfo.outputData.getInt(OptimizedSmsReaderWorker.PROGRESS_PARSED, parsed)
                            val outputSaved = workInfo.outputData.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, saved)
                            _uiState.update {
                                it.copy(
                                    isScanning = false,
                                    scanCompleted = true,
                                    scanTotal = outputTotal,
                                    scanProcessed = outputProcessed,
                                    scanParsed = outputParsed,
                                    scanSaved = outputSaved
                                )
                            }
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _uiState.update {
                                it.copy(isScanning = false, scanCompleted = true)
                            }
                        }
                        else -> { /* ENQUEUED, BLOCKED */ }
                    }
                }
        }
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            userPreferencesRepository.updateHasCompletedOnboarding(true)
            _uiState.update { it.copy(isCompleting = false) }
            onComplete()
        }
    }

}
