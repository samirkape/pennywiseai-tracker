package com.pennywiseai.tracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasSaveResult
import com.pennywiseai.tracker.utils.MerchantAliasAuditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MerchantAliasesUiState(
    val isLoading: Boolean = true,
    val items: List<MerchantAliasAuditor.AuditResult> = emptyList(),
    val suspiciousCount: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class MerchantAliasesViewModel @Inject constructor(
    private val merchantAliasRepository: MerchantAliasRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MerchantAliasesUiState())
    val uiState: StateFlow<MerchantAliasesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = merchantAliasRepository.auditAllAliases()
            _uiState.value = MerchantAliasesUiState(
                isLoading = false,
                items = items,
                suspiciousCount = items.count { it.isSuspicious },
            )
        }
    }

    fun deleteAlias(sourceMerchant: String) {
        viewModelScope.launch {
            merchantAliasRepository.deleteAlias(sourceMerchant)
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun addAlias(sourceMerchant: String, displayName: String) {
        viewModelScope.launch {
            val result = merchantAliasRepository.addAlias(sourceMerchant, displayName)
            handleSaveResult(result)
        }
    }

    fun updateAlias(originalSource: String, newSource: String, newDisplay: String) {
        viewModelScope.launch {
            val result = merchantAliasRepository.updateAlias(originalSource, newSource, newDisplay)
            handleSaveResult(result)
        }
    }

    private suspend fun handleSaveResult(result: MerchantAliasSaveResult) {
        when (result) {
            MerchantAliasSaveResult.SUCCESS -> {
                refresh()
                _uiState.value = _uiState.value.copy(message = SAVE_SUCCESS)
            }
            MerchantAliasSaveResult.EMPTY_FIELDS ->
                _uiState.value = _uiState.value.copy(message = ERROR_EMPTY)
            MerchantAliasSaveResult.SAME_SOURCE_AND_DISPLAY ->
                _uiState.value = _uiState.value.copy(message = ERROR_SAME)
            MerchantAliasSaveResult.DUPLICATE_SOURCE ->
                _uiState.value = _uiState.value.copy(message = ERROR_DUPLICATE)
            MerchantAliasSaveResult.NOT_FOUND -> refresh()
        }
    }

    companion object {
        const val SAVE_SUCCESS = "Mapping saved"
        const val ERROR_EMPTY = "Enter both SMS label and display name"
        const val ERROR_SAME = "SMS label and display name must be different"
        const val ERROR_DUPLICATE = "Another mapping already uses this SMS label"
    }
}
