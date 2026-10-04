package com.spendly.tracker.presentation.networth

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.NetWorthSourceEntity
import com.spendly.tracker.data.database.entity.NetWorthSourceOrigin
import com.spendly.tracker.data.database.entity.NetWorthSourceType
import com.spendly.tracker.data.networth.CasParser
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.NetWorthSourceRepository
import com.spendly.tracker.data.statement.PdfCellExtractor
import com.spendly.tracker.domain.usecase.ComputeNetWorthUseCase
import com.spendly.tracker.domain.usecase.NetWorthSummary
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NetWorthUiState(
    val summary: NetWorthSummary = NetWorthSummary(),
    val currency: String = "INR",
    val isLoading: Boolean = true,
    /** Picked CAS file waiting for its password. */
    val pendingCasUri: Uri? = null,
    val isImporting: Boolean = false,
    val casError: String? = null,
    val message: String? = null
)

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val computeNetWorth: ComputeNetWorthUseCase,
    private val repository: NetWorthSourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetWorthUiState())
    val uiState: StateFlow<NetWorthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferencesRepository.baseCurrency.first()
            computeNetWorth(currency).collect { summary ->
                _uiState.update { it.copy(summary = summary, currency = currency, isLoading = false) }
            }
        }
    }

    fun onCasFilePicked(uri: Uri) {
        _uiState.update { it.copy(pendingCasUri = uri, casError = null) }
    }

    fun dismissCasPrompt() {
        _uiState.update { it.copy(pendingCasUri = null, casError = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Parses the picked CAS on-device; the file and password never leave the phone. */
    fun importCas(password: String) {
        val uri = _uiState.value.pendingCasUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, casError = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val rows = PdfCellExtractor.extractRows(context, uri, password)
                    logCasShape(rows)
                    CasParser.parse(rows).also { parsed ->
                        Log.println(
                            Log.WARN, TAG,
                            "parsed=${parsed.size} stocks=${parsed.count { it.type == NetWorthSourceType.STOCKS }} " +
                                "funds=${parsed.count { it.type == NetWorthSourceType.MUTUAL_FUND }}"
                        )
                    }
                }
            }
            result.onSuccess { holdings ->
                if (holdings.isEmpty()) {
                    _uiState.update {
                        it.copy(isImporting = false, casError = "No holdings found in this statement")
                    }
                } else {
                    // CAS statements are always denominated in INR.
                    val currency = "INR"
                    repository.replaceCasHoldings(
                        holdings.map { h ->
                            NetWorthSourceEntity(
                                name = h.name,
                                type = h.type,
                                origin = NetWorthSourceOrigin.CAS,
                                value = h.value,
                                currency = currency,
                                externalKey = h.externalKey
                            )
                        }
                    )
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            pendingCasUri = null,
                            message = "Imported ${holdings.size} holdings"
                        )
                    }
                }
            }.onFailure { error ->
                val text = when (error) {
                    is InvalidPasswordException -> "Wrong password. CAS files are usually protected with your PAN."
                    else -> "Couldn't read this file as a CAS statement"
                }
                _uiState.update { it.copy(isImporting = false, casError = text) }
            }
        }
    }

    // TEMPORARY: logs the masked structure of the extracted rows (no names or numbers) to debug parsing.
    private fun logCasShape(rows: List<com.spendly.tracker.data.networth.CasRow>) {
        val keywords = listOf(
            "central depository", "holding statement", "statement of transactions", "mutual fund units",
            "dp name", "client id", "cdsl", "nsdl", "isin", "sub total", "total", "security", "market price"
        )
        fun mask(cell: String): String {
            val lower = cell.lowercase()
            keywords.firstOrNull { it in lower }?.let { return "[KW:$it]" }
            return cell.map { c -> if (c.isDigit()) '9' else if (c.isLetter()) 'A' else c }.joinToString("")
        }
        // Log.println is used because release builds strip Log.w/d/i/v/e.
        Log.println(Log.WARN, TAG, "rows=${rows.size} pages=${rows.maxOfOrNull { it.page } ?: 0}")
        rows.withIndex().drop(LOG_FROM_ROW).take(MAX_LOGGED_ROWS).forEach { (i, row) ->
            Log.println(Log.WARN, TAG, "$i p${row.page} n=${row.cells.size} " + row.cells.joinToString(" | ") { mask(it) })
        }
    }

    private companion object {
        const val TAG = "CasDebug"
        const val LOG_FROM_ROW = 400
        const val MAX_LOGGED_ROWS = 400
    }
}
