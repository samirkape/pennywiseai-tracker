package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.GoalType
import com.pennywiseai.tracker.data.database.entity.GoalTrackingMode
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class GoalEditUiState(
    val name: String = "",
    val description: String = "",
    val goalType: GoalType = GoalType.SAVINGS,
    val customTypeName: String = "",
    val targetAmountText: String = "",
    val targetDate: LocalDate = LocalDate.now().plusMonths(6),
    val currency: String = "INR",
    val color: String = "#4CAF50",
    val trackingMode: GoalTrackingMode = GoalTrackingMode.MANUAL_DEPOSIT,
    val autoTrackCategories: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isEditMode: Boolean = false
)

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val goalId: Long = savedStateHandle.get<Long>("goalId") ?: -1L

    private val _uiState = MutableStateFlow(GoalEditUiState())
    val uiState: StateFlow<GoalEditUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val currency = userPreferencesRepository.baseCurrency.first()
            val categories = categoryRepository.getAllCategories().first().map { it.name }
            _uiState.update { it.copy(currency = currency, availableCategories = categories) }

            if (goalId != -1L) {
                val goal = goalRepository.getGoalById(goalId)
                if (goal != null) {
                    _uiState.update { it.copy(
                        name = goal.name,
                        description = goal.description ?: "",
                        goalType = goal.goalType,
                        customTypeName = goal.customTypeName ?: "",
                        targetAmountText = goal.targetAmount.toPlainString(),
                        targetDate = goal.targetDate,
                        currency = goal.currency,
                        color = goal.color,
                        trackingMode = goal.trackingMode,
                        autoTrackCategories = if (goal.autoTrackCategories.isBlank()) emptyList()
                            else goal.autoTrackCategories.split(",").filter { it.isNotBlank() },
                        isEditMode = true
                    ) }
                }
            }
        }
    }

    fun updateName(name: String) { _uiState.update { it.copy(name = name, errorMessage = null) } }
    fun updateDescription(description: String) { _uiState.update { it.copy(description = description) } }
    fun updateGoalType(goalType: GoalType) { _uiState.update { it.copy(goalType = goalType) } }
    fun updateCustomTypeName(name: String) { _uiState.update { it.copy(customTypeName = name) } }
    fun updateTargetAmount(text: String) { _uiState.update { it.copy(targetAmountText = text, errorMessage = null) } }
    fun updateTargetDate(date: LocalDate) { _uiState.update { it.copy(targetDate = date) } }
    fun updateCurrency(currency: String) { _uiState.update { it.copy(currency = currency) } }
    fun updateColor(color: String) { _uiState.update { it.copy(color = color) } }
    fun updateTrackingMode(mode: GoalTrackingMode) { _uiState.update { it.copy(trackingMode = mode) } }

    fun toggleAutoTrackCategory(category: String) {
        _uiState.update { state ->
            val current = state.autoTrackCategories.toMutableList()
            if (current.contains(category)) current.remove(category) else current.add(category)
            state.copy(autoTrackCategories = current)
        }
    }

    fun saveGoal() {
        val state = _uiState.value
        val amount = state.targetAmountText.toBigDecimalOrNull()

        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Goal name is required") }
            return
        }
        if (state.goalType == GoalType.CUSTOM && state.customTypeName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a name for your custom goal type") }
            return
        }
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(errorMessage = "Enter a valid target amount") }
            return
        }
        if (state.targetDate.isBefore(LocalDate.now())) {
            _uiState.update { it.copy(errorMessage = "Target date must be in the future") }
            return
        }

        val resolvedCustomTypeName = if (state.goalType == GoalType.CUSTOM) state.customTypeName.trim() else null

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (goalId != -1L) {
                    goalRepository.updateGoal(
                        goalId = goalId,
                        name = state.name,
                        description = state.description.ifBlank { null },
                        goalType = state.goalType,
                        targetAmount = amount,
                        targetDate = state.targetDate,
                        currency = state.currency,
                        color = state.color,
                        trackingMode = state.trackingMode,
                        autoTrackCategories = state.autoTrackCategories,
                        customTypeName = resolvedCustomTypeName
                    )
                } else {
                    goalRepository.createGoal(
                        name = state.name,
                        description = state.description.ifBlank { null },
                        goalType = state.goalType,
                        targetAmount = amount,
                        targetDate = state.targetDate,
                        currency = state.currency,
                        color = state.color,
                        trackingMode = state.trackingMode,
                        autoTrackCategories = state.autoTrackCategories,
                        customTypeName = resolvedCustomTypeName
                    )
                }
                _uiState.update { it.copy(saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save goal") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
