package com.spendly.tracker.presentation.prepaid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.PrepaidExpenseEntity
import com.spendly.tracker.data.repository.PrepaidExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class PrepaidExpenseCard(
    val plan: PrepaidExpenseEntity,
    val monthsElapsed: Int
)

data class PrepaidExpensesUiState(
    val plans: List<PrepaidExpenseCard> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PrepaidExpensesViewModel @Inject constructor(
    private val prepaidExpenseRepository: PrepaidExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrepaidExpensesUiState())
    val uiState: StateFlow<PrepaidExpensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prepaidExpenseRepository.getAllPlans().collect { plans ->
                val currentMonth = YearMonth.now()
                val cards = plans.map { plan ->
                    val start = YearMonth.from(plan.startDate)
                    val elapsed = (java.time.temporal.ChronoUnit.MONTHS.between(start, currentMonth) + 1)
                        .toInt()
                        .coerceIn(0, plan.totalMonths)
                    PrepaidExpenseCard(plan = plan, monthsElapsed = elapsed)
                }
                _uiState.value = PrepaidExpensesUiState(plans = cards, isLoading = false)
            }
        }
    }

    fun cancelPlan(planId: Long) {
        viewModelScope.launch { prepaidExpenseRepository.cancelPlan(planId) }
    }

    fun deletePlan(planId: Long) {
        viewModelScope.launch { prepaidExpenseRepository.deletePlan(planId) }
    }
}
