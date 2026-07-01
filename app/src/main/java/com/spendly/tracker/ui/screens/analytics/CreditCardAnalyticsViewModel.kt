package com.spendly.tracker.ui.screens.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
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
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreditCardAnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
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
            ) { view, cardKey, globalDay, perCardDays ->
                QueryParams(view, cardKey, globalDay, perCardDays)
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
                    result.transactions.filter { txn ->
                        "${txn.bankName}|${txn.accountNumber?.takeLast(4) ?: ""}" == result.params.cardKey
                    }
                }

                val total = filtered.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

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
                        transactions = filtered,
                        totalAmount = total,
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

    private fun computeBillingCycleRange(start: LocalDate, billingDay: Int): Pair<LocalDate, LocalDate> {
        val cycleStart = if (start.dayOfMonth >= billingDay) {
            start.withDayOfMonth(billingDay)
        } else {
            start.minusMonths(1).withDayOfMonth(billingDay)
        }
        return cycleStart to cycleStart.plusMonths(1).minusDays(1)
    }
}
