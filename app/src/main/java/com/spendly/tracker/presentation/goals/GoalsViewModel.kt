package com.spendly.tracker.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.GoalEntity
import com.spendly.tracker.data.database.entity.GoalStatus
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.GoalRepository
import com.spendly.tracker.domain.model.GoalProgress
import com.spendly.tracker.domain.usecase.ComputeGoalProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class GoalsUiState(
    val activeGoals: List<GoalProgress> = emptyList(),
    val archivedGoals: List<GoalProgress> = emptyList(),
    val totalTargetAmount: BigDecimal = BigDecimal.ZERO,
    val totalCurrentAmount: BigDecimal = BigDecimal.ZERO,
    val totalDailySavingsNeeded: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val showArchived: Boolean = false,
    val currency: String = "INR"
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val computeGoalProgressUseCase: ComputeGoalProgressUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    init {
        observeGoals()
    }

    private fun observeGoals() {
        viewModelScope.launch {
            val currency = userPreferencesRepository.baseCurrency.first()
            combine(
                goalRepository.getActiveGoals(),
                goalRepository.getArchivedGoals(),
            ) { active, archived -> active to archived }
                .collect { (active, archived) ->
                    val activeProgress = active.map { computeGoalProgressUseCase.compute(it) }
                    val archivedProgress = archived.map { computeGoalProgressUseCase.compute(it) }
                    _uiState.value = GoalsUiState(
                        activeGoals = activeProgress,
                        archivedGoals = archivedProgress,
                        totalTargetAmount = active.fold(BigDecimal.ZERO) { acc, g -> acc + g.targetAmount },
                        totalCurrentAmount = active.fold(BigDecimal.ZERO) { acc, g -> acc + g.currentAmount },
                        totalDailySavingsNeeded = activeProgress.fold(BigDecimal.ZERO) { acc, gp -> acc + gp.dailySavingsNeeded },
                        isLoading = false,
                        showArchived = _uiState.value.showArchived,
                        currency = currency
                    )
                }
        }
    }

    fun toggleShowArchived() {
        _uiState.value = _uiState.value.copy(showArchived = !_uiState.value.showArchived)
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            goalRepository.deleteGoal(goalId)
        }
    }

    fun updateGoalStatus(goalId: Long, status: GoalStatus) {
        viewModelScope.launch {
            goalRepository.updateGoalStatus(goalId, status)
        }
    }
}
