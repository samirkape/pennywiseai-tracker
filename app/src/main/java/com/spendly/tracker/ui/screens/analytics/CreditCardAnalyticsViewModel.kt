package com.spendly.tracker.ui.screens.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.LoanRepository
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreditCardAnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val loanRepository: LoanRepository,
) : ViewModel() {

    private val startEpochDay: Long = savedStateHandle["startEpoch"] ?: LocalDate.now().toEpochDay()
    private val endEpochDay: Long = savedStateHandle["endEpoch"] ?: LocalDate.now().toEpochDay()
    private val argCurrency: String = savedStateHandle["currency"] ?: "INR"

    val payPeriodStart: LocalDate = LocalDate.ofEpochDay(startEpochDay)
    val payPeriodEnd: LocalDate = LocalDate.ofEpochDay(endEpochDay)

    enum class ViewMode { PAY_PERIOD, BILLING_CYCLE }

    data class CardOption(val bankName: String, val last4: String) {
        val key: String get() = "$bankName|$last4"
        val displayName: String get() = if (last4.isNotBlank()) "$bankName ••$last4" else bankName
    }

    /** Spending grouped by card for the current period. */
    data class CardSpend(
        val key: String,
        val displayName: String,
        val amount: BigDecimal,
        val percentage: Float,
        val transactionCount: Int,
    )

    /** Money lent to people, kept out of spending totals. */
    data class LentSpend(
        val loanId: Long?,
        val personName: String,
        val cardLabel: String?,
        val amount: BigDecimal,
        val transactionCount: Int,
    )

    data class UiState(
        val view: ViewMode = ViewMode.PAY_PERIOD,
        val selectedCardKey: String? = null,
        val availableCards: List<CardOption> = emptyList(),
        val transactions: List<TransactionEntity> = emptyList(),
        val totalAmount: BigDecimal = BigDecimal.ZERO,
        val currency: String = "INR",
        val payPeriodStart: LocalDate = LocalDate.now(),
        val payPeriodEnd: LocalDate = LocalDate.now(),
        val billingCycleStart: LocalDate? = null,
        val billingCycleEnd: LocalDate? = null,
        val globalBillingCycleDay: Int = 0,
        val perCardBillingCycleDays: Map<String, Int> = emptyMap(),
        val categoryBreakdown: List<CategoryData> = emptyList(),
        val cardBreakdown: List<CardSpend> = emptyList(),
        val lentBreakdown: List<LentSpend> = emptyList(),
        val lentTransactions: List<TransactionEntity> = emptyList(),
        val totalLent: BigDecimal = BigDecimal.ZERO,
        val isLoading: Boolean = true,
    ) {
        val effectiveBillingCycleDay: Int
            get() = if (selectedCardKey != null) {
                perCardBillingCycleDays[selectedCardKey] ?: globalBillingCycleDay
            } else {
                globalBillingCycleDay
            }

        val selectedCardHasOverride: Boolean
            get() = selectedCardKey != null && perCardBillingCycleDays.containsKey(selectedCardKey)
    }

    private data class QueryParams(
        val view: ViewMode,
        val cardKey: String?,
        val globalDay: Int,
        val perCardDays: Map<String, Int>,
        val loans: List<LoanEntity>,
    )

    private data class QueryResult(
        val params: QueryParams,
        val transactions: List<TransactionEntity>,
        val rangeStart: LocalDate,
        val rangeEnd: LocalDate,
    )

    private val _view = MutableStateFlow(ViewMode.PAY_PERIOD)
    private val _selectedCardKey = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(
        UiState(
            currency = argCurrency,
            payPeriodStart = payPeriodStart,
            payPeriodEnd = payPeriodEnd,
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadTransactions() {
        viewModelScope.launch {
            combine(
                _view,
                _selectedCardKey,
                userPreferencesRepository.creditCardBillingCycleDay,
                userPreferencesRepository.creditCardBillingCyclePerCard,
                loanRepository.getAllLoans(),
            ) { view, cardKey, globalDay, perCardDays, loans ->
                QueryParams(view, cardKey, globalDay, perCardDays, loans)
            }.flatMapLatest { params ->
                val effectiveDay = if (params.cardKey != null) {
                    params.perCardDays[params.cardKey] ?: params.globalDay
                } else {
                    params.globalDay
                }
                val (rangeStart, rangeEnd) = if (params.view == ViewMode.BILLING_CYCLE && effectiveDay > 0) {
                    computeBillingCycleRange(payPeriodStart, effectiveDay)
                } else {
                    payPeriodStart to payPeriodEnd
                }
                transactionRepository.getTransactionsFiltered(
                    startDate = rangeStart,
                    endDate = rangeEnd,
                    currency = argCurrency,
                    transactionType = TransactionType.CREDIT,
                ).map { txns -> QueryResult(params, txns, rangeStart, rangeEnd) }
            }.collect { result ->
                val availableCards = result.transactions
                    .mapNotNull { txn ->
                        val bank = txn.bankName ?: return@mapNotNull null
                        val last4 = txn.accountNumber?.takeLast(4) ?: ""
                        CardOption(bank, last4)
                    }
                    .distinctBy { it.key }
                    .sortedBy { it.bankName }

                val filtered = if (result.params.cardKey == null) {
                    result.transactions
                } else {
                    result.transactions.filter { txn -> txn.cardKey() == result.params.cardKey }
                }

                // Money lent to people is tracked separately and is not part of card spending.
                val spendTransactions = filtered.filter { it.loanId == null }
                val lentTransactions = filtered.filter { it.loanId != null }

                val total = spendTransactions.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

                val categoryAmounts = mutableMapOf<String, BigDecimal>()
                val categoryCounts = mutableMapOf<String, Int>()
                for (txn in spendTransactions) {
                    if (txn.amount.signum() <= 0) continue
                    val name = txn.category.ifBlank { "Others" }
                    categoryAmounts[name] = (categoryAmounts[name] ?: BigDecimal.ZERO) + txn.amount
                    categoryCounts[name] = (categoryCounts[name] ?: 0) + 1
                }
                val breakdownTotal = categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                val categoryBreakdown = categoryAmounts.entries
                    .map { (name, amount) ->
                        CategoryData(
                            name = name,
                            amount = amount,
                            percentage = percentOf(amount, breakdownTotal),
                            transactionCount = categoryCounts[name] ?: 0,
                        )
                    }
                    .sortedByDescending { it.amount }

                // Per-card view uses every card in the period so it stays useful while a card
                // filter is active; lent transactions are excluded here too.
                val cardAmounts = mutableMapOf<String, BigDecimal>()
                val cardCounts = mutableMapOf<String, Int>()
                val cardNames = mutableMapOf<String, String>()
                for (txn in result.transactions) {
                    if (txn.loanId != null || txn.amount.signum() <= 0) continue
                    val key = txn.cardKey()
                    cardNames[key] = txn.cardDisplayName()
                    cardAmounts[key] = (cardAmounts[key] ?: BigDecimal.ZERO) + txn.amount
                    cardCounts[key] = (cardCounts[key] ?: 0) + 1
                }
                val cardTotal = cardAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                val cardBreakdown = cardAmounts.entries
                    .map { (key, amount) ->
                        CardSpend(
                            key = key,
                            displayName = cardNames[key] ?: "Unknown card",
                            amount = amount,
                            percentage = percentOf(amount, cardTotal),
                            transactionCount = cardCounts[key] ?: 0,
                        )
                    }
                    .sortedByDescending { it.amount }

                val loansById = result.params.loans.associateBy { it.id }
                val lentSpendTransactions = lentTransactions.filter { it.amount.signum() > 0 }
                val lentBreakdown = lentSpendTransactions
                    .groupBy { it.loanId }
                    .map { (loanId, txns) ->
                        LentSpend(
                            loanId = loanId,
                            personName = loanId?.let { loansById[it]?.personName }
                                ?: txns.first().merchantName.ifBlank { "Lent" },
                            cardLabel = txns.first().cardDisplayName().takeIf { it.isNotBlank() },
                            amount = txns.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount },
                            transactionCount = txns.size,
                        )
                    }
                    .sortedByDescending { it.amount }
                val totalLent = lentBreakdown.fold(BigDecimal.ZERO) { acc, lent -> acc + lent.amount }

                val billingCycleRange = run {
                    val effectiveDay = if (result.params.cardKey != null) {
                        result.params.perCardDays[result.params.cardKey] ?: result.params.globalDay
                    } else {
                        result.params.globalDay
                    }
                    if (effectiveDay > 0) computeBillingCycleRange(payPeriodStart, effectiveDay) else null
                }

                _uiState.update { state ->
                    state.copy(
                        view = result.params.view,
                        selectedCardKey = result.params.cardKey,
                        availableCards = availableCards,
                        transactions = spendTransactions,
                        totalAmount = total,
                        categoryBreakdown = categoryBreakdown,
                        cardBreakdown = cardBreakdown,
                        lentBreakdown = lentBreakdown,
                        lentTransactions = lentSpendTransactions,
                        totalLent = totalLent,
                        globalBillingCycleDay = result.params.globalDay,
                        perCardBillingCycleDays = result.params.perCardDays,
                        billingCycleStart = billingCycleRange?.first,
                        billingCycleEnd = billingCycleRange?.second,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun setView(view: ViewMode) { _view.value = view }
    fun setSelectedCardKey(key: String?) { _selectedCardKey.value = key }

    fun updateGlobalBillingCycleDay(day: Int) {
        viewModelScope.launch { userPreferencesRepository.updateCreditCardBillingCycleDay(day) }
    }

    fun updateCardBillingCycleDay(cardKey: String, day: Int) {
        viewModelScope.launch { userPreferencesRepository.updateCreditCardBillingCycleForCard(cardKey, day) }
    }

    fun clearCardBillingCycleOverride(cardKey: String) {
        viewModelScope.launch { userPreferencesRepository.clearCreditCardBillingCycleForCard(cardKey) }
    }

    private fun percentOf(amount: BigDecimal, total: BigDecimal): Float =
        if (total.signum() > 0) {
            amount.multiply(BigDecimal(100))
                .divide(total, 2, RoundingMode.HALF_UP)
                .toFloat()
        } else 0f

    private fun TransactionEntity.cardKey(): String =
        "${bankName ?: "Unknown"}|${accountNumber?.takeLast(4) ?: ""}"

    private fun TransactionEntity.cardDisplayName(): String {
        val bank = bankName?.trim().orEmpty()
        val last4 = accountNumber?.takeLast(4).orEmpty()
        return when {
            bank.isBlank() && last4.isBlank() -> "Unknown card"
            bank.isBlank() -> "••$last4"
            last4.isBlank() -> bank
            else -> "$bank ••$last4"
        }
    }

    private fun computeBillingCycleRange(start: LocalDate, billingDay: Int): Pair<LocalDate, LocalDate> {
        val cycleStart = if (start.dayOfMonth >= billingDay) {
            start.withDayOfMonth(billingDay)
        } else {
            start.minusMonths(1).withDayOfMonth(billingDay)
        }
        return cycleStart to cycleStart.plusMonths(1).minusDays(1)
    }
}
