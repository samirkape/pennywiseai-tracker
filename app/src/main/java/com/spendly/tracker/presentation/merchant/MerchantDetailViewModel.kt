package com.spendly.tracker.presentation.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.dao.MerchantMonthlyTotal
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class MerchantDetailPeriod { MONTH, YEAR, ALL }

sealed interface MerchantDetailUiState {
    data object Loading : MerchantDetailUiState

    data class Loaded(
        val merchantName: String,
        val currency: String,
        val period: MerchantDetailPeriod,
        val allTimeTotal: BigDecimal,
        val allTimeCount: Int,
        val memberSinceLabel: String?,
        val periodTotal: BigDecimal,
        val periodCount: Int,
        val periodAvg: BigDecimal,
        val momTrendPct: Int?,
        val anomalyMultiplier: BigDecimal?,
        val chartData: List<Pair<String, BigDecimal>>,
    ) : MerchantDetailUiState
}

@HiltViewModel
class MerchantDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val merchantName = MutableStateFlow<String?>(null)
    private val selectedPeriod = MutableStateFlow(MerchantDetailPeriod.MONTH)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val totalsWithName = merchantName.filterNotNull().flatMapLatest { name ->
        transactionRepository.getMonthlyTotalsForMerchant(name).map { name to it }
    }

    val uiState: StateFlow<MerchantDetailUiState> = combine(
        totalsWithName,
        userPreferencesRepository.baseCurrency,
        selectedPeriod,
    ) { (name, totals), currency, period ->
        buildUiState(name, currency, totals, period)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MerchantDetailUiState.Loading)

    fun load(merchant: String) {
        merchantName.value = merchant
    }

    fun selectPeriod(period: MerchantDetailPeriod) {
        selectedPeriod.value = period
    }

    private fun buildUiState(
        name: String,
        currency: String,
        totals: List<MerchantMonthlyTotal>,
        period: MerchantDetailPeriod,
    ): MerchantDetailUiState {
        val sorted = totals.sortedBy { it.yearMonth }
        val allTimeTotal = sorted.fold(BigDecimal.ZERO) { acc, t -> acc + t.total }
        val allTimeCount = sorted.sumOf { it.count }
        val memberSinceLabel = sorted.firstOrNull()?.let { formatMonthLabel(it.yearMonth, withYear = true) }

        val currentYearMonth = YearMonth.now()
        val currentKey = currentYearMonth.toString()
        val previousKey = currentYearMonth.minusMonths(1).toString()

        val currentMonth = sorted.find { it.yearMonth == currentKey }
        val previousMonth = sorted.find { it.yearMonth == previousKey }
        val currentYearTotals = sorted.filter { it.yearMonth.startsWith(currentYearMonth.year.toString()) }

        val (periodTotal, periodCount) = when (period) {
            MerchantDetailPeriod.MONTH -> (currentMonth?.total ?: BigDecimal.ZERO) to (currentMonth?.count ?: 0)
            MerchantDetailPeriod.YEAR -> currentYearTotals.fold(BigDecimal.ZERO) { acc, t -> acc + t.total } to
                currentYearTotals.sumOf { it.count }
            MerchantDetailPeriod.ALL -> allTimeTotal to allTimeCount
        }
        val periodAvg = if (periodCount > 0) {
            periodTotal.divide(BigDecimal(periodCount), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val momTrendPct = if (previousMonth != null && previousMonth.total > BigDecimal.ZERO && currentMonth != null) {
            (currentMonth.total - previousMonth.total)
                .divide(previousMonth.total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toInt()
        } else {
            null
        }

        val trailingMonths = sorted.filter { it.yearMonth != currentKey }.takeLast(3)
        val trailingAvg = if (trailingMonths.isNotEmpty()) {
            trailingMonths.fold(BigDecimal.ZERO) { acc, t -> acc + t.total }
                .divide(BigDecimal(trailingMonths.size), 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        val anomalyMultiplier = if (currentMonth != null && trailingAvg > BigDecimal.ZERO) {
            val multiplier = currentMonth.total.divide(trailingAvg, 2, RoundingMode.HALF_UP)
            if (multiplier >= BigDecimal("1.5")) multiplier else null
        } else {
            null
        }

        val chartData = sorted.takeLast(6).map { formatMonthLabel(it.yearMonth, withYear = false) to it.total }

        return MerchantDetailUiState.Loaded(
            merchantName = name,
            currency = currency,
            period = period,
            allTimeTotal = allTimeTotal,
            allTimeCount = allTimeCount,
            memberSinceLabel = memberSinceLabel,
            periodTotal = periodTotal,
            periodCount = periodCount,
            periodAvg = periodAvg,
            momTrendPct = momTrendPct,
            anomalyMultiplier = anomalyMultiplier,
            chartData = chartData,
        )
    }

    private fun formatMonthLabel(yearMonth: String, withYear: Boolean): String {
        val parsed = YearMonth.parse(yearMonth)
        val pattern = if (withYear) "MMM yyyy" else "MMM"
        return parsed.format(DateTimeFormatter.ofPattern(pattern))
    }
}
