package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalStatus
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.domain.model.GoalProgress
import com.pennywiseai.tracker.domain.usecase.ComputeGoalProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class GoalDetailUiState(
    val progress: GoalProgress? = null,
    val contributions: List<GoalContributionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDepositSheet: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val computeGoalProgressUseCase: ComputeGoalProgressUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val goalId: Long = savedStateHandle.get<Long>("goalId") ?: -1L

    private val _uiState = MutableStateFlow(GoalDetailUiState())
    val uiState: StateFlow<GoalDetailUiState> = _uiState.asStateFlow()

    init {
        loadGoal()
    }

    private fun loadGoal() {
        viewModelScope.launch {
            combine(
                goalRepository.getGoalByIdFlow(goalId),
                goalRepository.getContributionsForGoal(goalId)
            ) { goal, contributions -> goal to contributions }
                .collect { (goal, contributions) ->
                    val progress = goal?.let {
                        computeGoalProgressUseCase.compute(it, contributions)
                    }
                    _uiState.update { it.copy(
                        progress = progress,
                        contributions = contributions,
                        isLoading = false
                    ) }
                }
        }
    }

    fun addDeposit(amount: BigDecimal, note: String?) {
        viewModelScope.launch {
            try {
                goalRepository.addManualDeposit(goalId, amount, note)
                _uiState.update { it.copy(showAddDepositSheet = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun removeContribution(contributionId: Long) {
        viewModelScope.launch {
            goalRepository.unlinkTransaction(contributionId)
        }
    }

    fun updateStatus(status: GoalStatus) {
        viewModelScope.launch {
            goalRepository.updateGoalStatus(goalId, status)
            if (status == GoalStatus.ABANDONED) {
                _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }

    fun deleteGoal() {
        viewModelScope.launch {
            goalRepository.deleteGoal(goalId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun showAddDepositSheet() { _uiState.update { it.copy(showAddDepositSheet = true) } }
    fun hideAddDepositSheet() { _uiState.update { it.copy(showAddDepositSheet = false) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}
