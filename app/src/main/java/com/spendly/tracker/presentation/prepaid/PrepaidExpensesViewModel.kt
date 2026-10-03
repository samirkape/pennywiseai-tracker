package com.spendly.tracker.presentation.prepaid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.PrepaidExpenseEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.repository.PrepaidExpenseRepository
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    private val prepaidExpenseRepository: PrepaidExpenseRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Expenses that can still be converted into a prepaid plan, filtered by the search query. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val candidates: StateFlow<List<TransactionEntity>> = _searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            val source = if (query.isBlank()) transactionRepository.getAllTransactions()
            else transactionRepository.searchTransactions(query.trim())
            combine(source, prepaidExpenseRepository.getAllPlans()) { list, plans ->
                val planSourceIds = plans.map { it.sourceTransactionId }.toSet()
                list.filter {
                    (it.transactionType == TransactionType.EXPENSE ||
                        it.transactionType == TransactionType.CREDIT) &&
                        !it.isExcludedFromTracking &&
                        !it.isRecurring &&
                        it.id !in planSourceIds
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun clearError() { _errorMessage.value = null }

    fun convertTransaction(transactionId: Long, totalMonths: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                prepaidExpenseRepository.createPlanFromTransaction(transactionId, totalMonths)
                onDone()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create prepaid plan: ${e.message}"
            }
        }
    }

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
