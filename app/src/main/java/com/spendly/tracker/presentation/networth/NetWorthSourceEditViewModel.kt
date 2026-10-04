package com.spendly.tracker.presentation.networth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.NetWorthSourceType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.NetWorthSourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class NetWorthSourceEditUiState(
    val name: String = "",
    val type: NetWorthSourceType = NetWorthSourceType.CASH,
    val valueText: String = "",
    val notes: String = "",
    val currency: String = "INR",
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class NetWorthSourceEditViewModel @Inject constructor(
    private val repository: NetWorthSourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sourceId: Long = savedStateHandle.get<Long>("sourceId") ?: -1L

    private val _uiState = MutableStateFlow(NetWorthSourceEditUiState())
    val uiState: StateFlow<NetWorthSourceEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferencesRepository.baseCurrency.first()
            val existing = if (sourceId > 0) repository.getById(sourceId) else null
            _uiState.update {
                if (existing == null) {
                    it.copy(currency = currency)
                } else {
                    it.copy(
                        name = existing.name,
                        type = existing.type,
                        valueText = existing.value.stripTrailingZeros().toPlainString(),
                        notes = existing.notes.orEmpty(),
                        currency = existing.currency,
                        isEditMode = true
                    )
                }
            }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }

    fun updateType(value: NetWorthSourceType) = _uiState.update { it.copy(type = value) }

    fun updateValue(value: String) {
        // Allow only digits and a single decimal separator.
        if (value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(valueText = value, errorMessage = null) }
        }
    }

    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun save() {
        val state = _uiState.value
        val value = state.valueText.toBigDecimalOrNull()
        when {
            state.name.isBlank() -> _uiState.update { it.copy(errorMessage = "Enter a name") }
            value == null || value.signum() < 0 -> _uiState.update { it.copy(errorMessage = "Enter a valid amount") }
            else -> viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true) }
                if (state.isEditMode) {
                    repository.updateManual(sourceId, state.name, state.type, value, state.notes)
                } else {
                    repository.addManual(state.name, state.type, value, state.currency, state.notes)
                }
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            }
        }
    }

    fun delete() {
        if (sourceId <= 0) return
        viewModelScope.launch {
            repository.delete(sourceId)
            _uiState.update { it.copy(saveSuccess = true) }
        }
    }
}
