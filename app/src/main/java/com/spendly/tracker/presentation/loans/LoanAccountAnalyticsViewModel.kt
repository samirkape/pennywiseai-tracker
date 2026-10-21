package com.spendly.tracker.presentation.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanStatus
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AccountLoanAmount(
    val label: String,
    val amount: BigDecimal,
    val transactions: List<TransactionEntity>
) {
    val transactionCount: Int get() = transactions.size
}

data class LoanAccountAnalyticsUiState(
    val direction: LoanDirection = LoanDirection.LENT,
    val lentByAccount: List<AccountLoanAmount> = emptyList(),
    val borrowedByAccount: List<AccountLoanAmount> = emptyList(),
    val currency: String = "INR",
    val selectedLabel: String? = null,
    val includeSettled: Boolean = false,
    val isLoading: Boolean = true
) {
    val accounts: List<AccountLoanAmount>
        get() = if (direction == LoanDirection.LENT) lentByAccount else borrowedByAccount
}

@HiltViewModel
class LoanAccountAnalyticsViewModel @Inject constructor(
    private val loanRepository: LoanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoanAccountAnalyticsUiState())
    val uiState: StateFlow<LoanAccountAnalyticsUiState> = _uiState.asStateFlow()

    private val includeSettled = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                loanRepository.getAllLoans(),
                loanRepository.getAllLoanTransactions(),
                includeSettled
            ) { allLoans, txs, withSettled ->
                val loans = if (withSettled) allLoans else allLoans.filter { it.status == LoanStatus.ACTIVE }
                val directionByLoan = loans.associate { it.id to it.direction }
                // Money leaving an account when lending, arriving in an account when borrowing.
                val relevant = txs.filter { tx ->
                    val dir = directionByLoan[tx.loanId] ?: return@filter false
                    when (dir) {
                        LoanDirection.LENT ->
                            tx.transactionType == TransactionType.EXPENSE || tx.transactionType == TransactionType.CREDIT
                        LoanDirection.BORROWED -> tx.transactionType == TransactionType.INCOME
                    }
                }
                // Amounts are not converted; use the dominant currency to keep totals meaningful.
                val currency = relevant.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: "INR"
                val sameCurrency = relevant.filter { it.currency == currency }

                fun aggregate(direction: LoanDirection) = sameCurrency
                    .filter { directionByLoan[it.loanId] == direction }
                    .groupBy { accountLabel(it.bankName, it.accountNumber) }
                    .map { (label, list) ->
                        AccountLoanAmount(label, list.fold(BigDecimal.ZERO) { a, t -> a + t.amount }, list.sortedByDescending { it.dateTime })
                    }
                    .sortedByDescending { it.amount }

                Triple(aggregate(LoanDirection.LENT), aggregate(LoanDirection.BORROWED), currency)
            }.collect { (lent, borrowed, currency) ->
                _uiState.value = _uiState.value.copy(
                    lentByAccount = lent,
                    borrowedByAccount = borrowed,
                    currency = currency,
                    isLoading = false
                )
            }
        }
    }

    fun toggleIncludeSettled() {
        val next = !includeSettled.value
        includeSettled.value = next
        _uiState.value = _uiState.value.copy(includeSettled = next, selectedLabel = null)
    }

    fun setDirection(direction: LoanDirection) {
        _uiState.value = _uiState.value.copy(direction = direction, selectedLabel = null)
    }

    fun toggleSelected(label: String) {
        val current = _uiState.value.selectedLabel
        _uiState.value = _uiState.value.copy(selectedLabel = if (current == label) null else label)
    }

    private fun accountLabel(bankName: String?, last4: String?): String = when {
        bankName.isNullOrBlank() && last4.isNullOrBlank() -> "Cash / Other"
        last4.isNullOrBlank() -> bankName!!
        bankName.isNullOrBlank() -> "Account ••$last4"
        else -> "$bankName ••$last4"
    }
}
