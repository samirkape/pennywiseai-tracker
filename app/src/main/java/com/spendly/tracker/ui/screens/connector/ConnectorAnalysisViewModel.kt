package com.spendly.tracker.ui.screens.connector

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.connector.data.export.SpendBriefExporter
import com.spendly.tracker.connector.data.preferences.ConnectorPreferencesRepository
import com.spendly.tracker.connector.data.repository.ConnectorRepository
import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.domain.AiStreamEvent
import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.ConnectorError
import com.spendly.tracker.connector.domain.ConnectorException
import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.SpendBriefFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** State rendered by the analysis screen. */
data class ConnectorAnalysisUiState(
    val isAnalysing: Boolean = false,
    val isPreparingShare: Boolean = false,
    val analysis: String = "",
    val errorMessage: String? = null,
    val preview: SpendAnalysis? = null,
    val provider: AiProvider = AiProvider.GEMINI,
    val model: String = "",
    /** Adds the optional per-transaction list to the shared file. */
    val includeTransactions: Boolean = true,
    /** Shape the brief is shared in. */
    val exportFormat: SpendBriefExporter.BriefFormat = SpendBriefExporter.BriefFormat.TEXT
)

/**
 * Backs the AI spending analysis screen: picks the period, runs the analysis and
 * streams the provider answer back. It can also export the same privacy-safe brief as
 * a file the user hands to any AI app without an API key.
 */
@HiltViewModel
class ConnectorAnalysisViewModel @Inject constructor(
    private val connectorRepository: ConnectorRepository,
    private val preferencesRepository: ConnectorPreferencesRepository,
    private val briefExporter: SpendBriefExporter
) : ViewModel() {

    private val _window = MutableStateFlow(AnalysisWindow.LAST_3_MONTHS)
    val window: StateFlow<AnalysisWindow> = _window.asStateFlow()

    /** Month windows follow the pay cycle unless the user picks calendar months. */
    private val _useCalendarMonths = MutableStateFlow(false)
    val useCalendarMonths: StateFlow<Boolean> = _useCalendarMonths.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _uiState = MutableStateFlow(ConnectorAnalysisUiState())
    val uiState: StateFlow<ConnectorAnalysisUiState> = _uiState.asStateFlow()

    /** Emits a ready-to-share brief file; the screen launches the share sheet. */
    private val _shareFile = MutableStateFlow<Uri?>(null)
    val shareFile: StateFlow<Uri?> = _shareFile.asStateFlow()

    /** Brief payload awaiting a download destination picked through the save dialog. */
    data class BriefDownload(val fileName: String, val text: String)

    private val _download = MutableStateFlow<BriefDownload?>(null)
    val download: StateFlow<BriefDownload?> = _download.asStateFlow()

    private var analysisJob: Job? = null
    private var loadedWindow: AnalysisWindow? = null
    private var loadedUseCalendarMonths: Boolean? = null

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
        viewModelScope.launch {
            preferencesRepository.monthsUseCalendar.collect { useCalendar ->
                _useCalendarMonths.value = useCalendar
                loadPreview(_window.value)
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

    fun setUseCalendarMonths(useCalendar: Boolean) {
        if (useCalendar == _useCalendarMonths.value) return
        _useCalendarMonths.value = useCalendar
        viewModelScope.launch {
            preferencesRepository.setMonthsUseCalendar(useCalendar)
            loadPreview(_window.value)
        }
    }

    fun setQuestion(value: String) {
        _question.value = value
    }

    fun setIncludeTransactions(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeTransactions = value)
    }

    fun setExportFormat(format: SpendBriefExporter.BriefFormat) {
        _uiState.value = _uiState.value.copy(exportFormat = format)
    }

    /** Recomputes the on-device summary shown above the provider answer. */
    private suspend fun loadPreview(targetWindow: AnalysisWindow) {
        val useCalendar = _useCalendarMonths.value
        if (loadedWindow == targetWindow &&
            loadedUseCalendarMonths == useCalendar &&
            _uiState.value.preview != null
        ) return
        val preview = connectorRepository.buildAnalysis(targetWindow, useCalendar)
        loadedWindow = targetWindow
        loadedUseCalendarMonths = useCalendar
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
                connectorRepository.analyze(
                    _window.value,
                    _question.value,
                    _useCalendarMonths.value
                ).collect { event ->
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

    /**
     * Writes the privacy-safe brief for the selected window to a shareable file.
     *
     * Works without an API key: the user decides which AI app receives the file.
     */
    fun shareBrief() {
        if (_uiState.value.isPreparingShare) return
        _uiState.value = _uiState.value.copy(isPreparingShare = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val includeTransactions = _uiState.value.includeTransactions
                val format = _uiState.value.exportFormat
                val brief = withContext(Dispatchers.IO) {
                    connectorRepository.buildBrief(
                        window = _window.value,
                        includeTransactions = includeTransactions,
                        useCalendarMonths = _useCalendarMonths.value
                    )
                }
                _shareFile.value = withContext(Dispatchers.IO) {
                    briefExporter.write(brief.analysis, _question.value, brief.transactions, format)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not prepare the brief file. Please try again."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isPreparingShare = false)
            }
        }
    }

    /**
     * Builds the brief text for the selected window and emits it so the screen can
     * open the system save dialog. Works without an API key, like [shareBrief].
     */
    fun prepareDownload() {
        if (_uiState.value.isPreparingShare) return
        _uiState.value = _uiState.value.copy(isPreparingShare = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val includeTransactions = _uiState.value.includeTransactions
                val format = _uiState.value.exportFormat
                val brief = withContext(Dispatchers.IO) {
                    connectorRepository.buildBrief(
                        window = _window.value,
                        includeTransactions = includeTransactions,
                        useCalendarMonths = _useCalendarMonths.value
                    )
                }
                _download.value = BriefDownload(
                    fileName = briefExporter.suggestedFileName(format),
                    text = briefExporter.render(
                        brief.analysis,
                        _question.value,
                        brief.transactions,
                        format
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not prepare the brief file. Please try again."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isPreparingShare = false)
            }
        }
    }

    /** Writes a prepared download to the Uri the user picked in the save dialog. */
    fun saveDownload(uri: Uri, text: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    briefExporter.writeTo(uri, text)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not save the file. Please try again."
                )
            }
        }
    }

    fun consumeDownload() {
        _download.value = null
    }

    fun consumeShareFile() {
        _shareFile.value = null
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
