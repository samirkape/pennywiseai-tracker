package com.pennywiseai.tracker.ui.screens.payperiod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.presentation.common.matchesAnalyticsSpendingFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.utils.DateRangeUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class PayPeriodExplorerUiState(
    val periodStart: LocalDate = LocalDate.now(),
    val periodEnd: LocalDate = LocalDate.now(),
    val lastDayInRange: LocalDate = LocalDate.now(),
    val dayLabels: List<LocalDate> = emptyList(),
    val cumulativeSeries: List<BigDecimal> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val spentThroughSelected: BigDecimal = BigDecimal.ZERO,
    val currency: String = "INR",
    val isUnifiedMode: Boolean = false,
    val isLoading: Boolean = true,
    val periodRangeLabel: String = "",
)

@HiltViewModel
class PayPeriodExplorerViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val currencyConversionService: CurrencyConversionService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayPeriodExplorerUiState())
    val uiState: StateFlow<PayPeriodExplorerUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    fun start(periodStartEpochDay: Long, periodEndEpochDay: Long) {
        collectJob?.cancel()
        val periodStart = LocalDate.ofEpochDay(periodStartEpochDay)
        val periodEnd = LocalDate.ofEpochDay(periodEndEpochDay)
        val today = LocalDate.now()
        val lastDay = minOf(today, periodEnd)
        if (lastDay.isBefore(periodStart)) {
            _uiState.value = PayPeriodExplorerUiState(
                periodStart = periodStart,
                periodEnd = periodEnd,
                lastDayInRange = lastDay,
                selectedDate = lastDay,
                isLoading = false,
                periodRangeLabel = DateRangeUtils.formatDateRange(periodStart, periodEnd),
            )
            return
        }
        val dayCount = ChronoUnit.DAYS.between(periodStart, lastDay).toInt() + 1
        val dayLabels = (0 until dayCount).map { periodStart.plusDays(it.toLong()) }
        _uiState.value = PayPeriodExplorerUiState(
            periodStart = periodStart,
            periodEnd = periodEnd,
            lastDayInRange = lastDay,
            dayLabels = dayLabels,
            cumulativeSeries = List(dayCount) { BigDecimal.ZERO },
            selectedDate = lastDay,
            spentThroughSelected = BigDecimal.ZERO,
            isLoading = true,
            periodRangeLabel = DateRangeUtils.formatDateRange(periodStart, periodEnd),
        )
        collectJob = viewModelScope.launch {
            combine(
                combine(
                    transactionRepository.getTransactionsBetweenDates(periodStart, lastDay),
                    userPreferencesRepository.selectedProfileId,
                    accountBalanceRepository.getAllLatestBalances(),
                ) { txs, profileId, balances ->
                    Triple(txs, profileId, balances)
                },
                combine(
                    userPreferencesRepository.unifiedCurrencyMode,
                    userPreferencesRepository.displayCurrency,
                    userPreferencesRepository.baseCurrency,
                ) { isUnified, displayCurrency, baseCurrency ->
                    Triple(isUnified, displayCurrency, baseCurrency)
                },
            ) { accPack, prefPack ->
                accPack to prefPack
            }.collect { (accPack, prefPack) ->
                val (txs, profileId, balances) = accPack
                val (isUnified, displayCurrency, baseCurrency) = prefPack
                val filtered = filterTransactionsByProfile(
                    txs,
                    profileId,
                    buildProfileAccountKeys(balances),
                )
                val displayCur = if (isUnified) {
                    displayCurrency.ifEmpty { baseCurrency }
                } else {
                    baseCurrency
                }
                recompute(
                    filtered = filtered,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    lastDay = lastDay,
                    dayLabels = dayLabels,
                    displayCurrency = displayCur,
                    isUnified = isUnified,
                )
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val s = _uiState.value
        if (date.isBefore(s.periodStart) || date.isAfter(s.lastDayInRange)) return
        val idx = ChronoUnit.DAYS.between(s.periodStart, date).toInt()
        val cumulative = s.cumulativeSeries.getOrNull(idx) ?: BigDecimal.ZERO
        _uiState.update {
            it.copy(selectedDate = date, spentThroughSelected = cumulative)
        }
    }

    private suspend fun recompute(
        filtered: List<TransactionEntity>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        lastDay: LocalDate,
        dayLabels: List<LocalDate>,
        displayCurrency: String,
        isUnified: Boolean,
    ) {
        val isSpending: (TransactionEntity) -> Boolean = { tx ->
            !tx.isExcludedFromTracking && tx.matchesAnalyticsSpendingFilter()
        }
        val daily = mutableMapOf<LocalDate, BigDecimal>()
        for (tx in filtered.filter(isSpending)) {
            val day = tx.dateTime.toLocalDate()
            if (day.isBefore(periodStart) || day.isAfter(lastDay)) continue
            val amt = if (isUnified && !tx.currency.equals(displayCurrency, ignoreCase = true)) {
                currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
            } else if (!isUnified && !tx.currency.equals(displayCurrency, ignoreCase = true)) {
                continue
            } else {
                tx.amount
            }
            daily[day] = (daily[day] ?: BigDecimal.ZERO) + amt
        }
        var cum = BigDecimal.ZERO
        val series = dayLabels.map { d ->
            cum += daily[d] ?: BigDecimal.ZERO
            cum
        }
        val selected = _uiState.value.selectedDate.coerceIn(periodStart, lastDay)
        val idx = ChronoUnit.DAYS.between(periodStart, selected).toInt().coerceIn(0, series.lastIndex.coerceAtLeast(0))
        val spent = series.getOrElse(idx) { BigDecimal.ZERO }
        _uiState.update {
            it.copy(
                cumulativeSeries = series,
                currency = displayCurrency,
                isUnifiedMode = isUnified,
                spentThroughSelected = spent,
                selectedDate = selected,
                isLoading = false,
                periodRangeLabel = DateRangeUtils.formatDateRange(periodStart, periodEnd),
            )
        }
    }

    override fun onCleared() {
        collectJob?.cancel()
        super.onCleared()
    }
}
