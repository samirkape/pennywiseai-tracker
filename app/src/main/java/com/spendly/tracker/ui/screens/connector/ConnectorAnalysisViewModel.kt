package com.spendly.tracker.ui.screens.connector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.connector.data.preferences.ConnectorPreferencesRepository
import com.spendly.tracker.connector.data.repository.ConnectorRepository
import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.domain.AiStreamEvent
import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.ConnectorError
import com.spendly.tracker.connector.domain.ConnectorException
import com.spendly.tracker.connector.domain.SpendAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State rendered by the analysis screen. */
data class ConnectorAnalysisUiState(
    val isAnalysing: Boolean = false,
    val analysis: String = "",
    val errorMessage: String? = null,
    val preview: SpendAnalysis? = null,
    val provider: AiProvider = AiProvider.GEMINI,
    val model: String = ""
)

/**
 * Backs the AI spending analysis screen: picks the period, runs the analysis and
 * streams the provider answer back.
 */
@HiltViewModel
class ConnectorAnalysisViewModel @Inject constructor(
    private val connectorRepository: ConnectorRepository,
    private val preferencesRepository: ConnectorPreferencesRepository
) : ViewModel() {

    private val _window = MutableStateFlow(AnalysisWindow.LAST_3_MONTHS)
    val window: StateFlow<AnalysisWindow> = _window.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _uiState = MutableStateFlow(ConnectorAnalysisUiState())
    val uiState: StateFlow<ConnectorAnalysisUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var loadedWindow: AnalysisWindow? = null

    init {
        viewModelScope.launch {
            connectorRepository.config.collect { config ->
                _uiState.value = _uiState.value.copy(
                    provider = config.provider,
                    model = config.model
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.window.collect { storedWindow ->
                _window.value = storedWindow
                loadPreview(storedWindow)
            }
        }
    }

    fun setWindow(newWindow: AnalysisWindow) {
        if (newWindow == _window.value) return
        _window.value = newWindow
        viewModelScope.launch {
            preferencesRepository.setWindow(newWindow)
            loadPreview(newWindow)
        }
    }

    fun setQuestion(value: String) {
        _question.value = value
    }

    /** Recomputes the on-device summary shown above the provider answer. */
    private suspend fun loadPreview(targetWindow: AnalysisWindow) {
        if (loadedWindow == targetWindow && _uiState.value.preview != null) return
        val preview = connectorRepository.buildAnalysis(targetWindow)
        loadedWindow = targetWindow
        _uiState.value = _uiState.value.copy(preview = preview)
    }

    fun analyse() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAnalysing = true,
            analysis = "",
            errorMessage = null
        )

        analysisJob = viewModelScope.launch {
            val builder = StringBuilder()
            try {
                connectorRepository.analyze(_window.value, _question.value).collect { event ->
                    when (event) {
                        is AiStreamEvent.TextDelta -> {
                            builder.append(event.text)
                            _uiState.value = _uiState.value.copy(analysis = builder.toString())
                        }
                        else -> Unit
                    }
                }
                _uiState.value = _uiState.value.copy(isAnalysing = false)
            } catch (e: ConnectorException) {
                _uiState.value = _uiState.value.copy(
                    isAnalysing = false,
                    errorMessage = e.error.userMessage
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalysing = false,
                    errorMessage = ConnectorError.Unknown(e.message).userMessage
                )
            }
        }
    }

    fun cancel() {
        analysisJob?.cancel()
        analysisJob = null
        _uiState.value = _uiState.value.copy(isAnalysing = false)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
