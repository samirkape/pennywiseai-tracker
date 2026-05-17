package com.pennywiseai.tracker.ui.screens.behavioral

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.defaultTimePeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// ─── UI Data Classes ───────────────────────────────────────────────────────────

data class TimeOfDayBucket(
    val label: String,
    val totalAmount: BigDecimal,
    val transactionCount: Int,
    val share: Float          // 0f–1f fraction of daily total
)

data class DayOfWeekBucket(
    val dayOfWeek: DayOfWeek,
    val label: String,        // e.g. "Mon"
    val totalAmount: BigDecimal,
    val transactionCount: Int,
    val share: Float
)

data class StreakData(
    val currentStreak: Int,   // consecutive "good" days (spend ≤ daily avg)
    val longestStreak: Int,
    val goodDaysCount: Int,   // total good days in period
    val totalDays: Int
)

enum class TrendDirection { GROWING, SHRINKING, STABLE }

data class CategoryTrend(
    val name: String,
    val firstHalfAmount: BigDecimal,
    val secondHalfAmount: BigDecimal,
    val trendPercent: Float,  // positive = growing, negative = shrinking
    val direction: TrendDirection
)

data class MerchantLoyalty(
    val name: String,
    val visitCount: Int,
    val avgAmount: BigDecimal,
    val totalAmount: BigDecimal
)

enum class ForecastConfidence { LOW, MEDIUM, HIGH }

/**
 * Spending forecast for the selected period.
 *
 * @param spentSoFar         Actual spend up to today
 * @param daysElapsed        Days with data so far (≥ 1)
 * @param totalDays          Total days in period
 * @param daysRemaining      Days left until period end (0 if in the past)
 * @param baseForecast       Simple projection: dailyAvg × totalDays
 * @param trendForecast      Trend-adjusted projection using the recent 7-day avg for remaining days
 * @param recentDailyAvg     Avg daily spend over the most recent 7 days (or all elapsed if < 7)
 * @param overallDailyAvg    Avg daily spend over the full elapsed period
 * @param expectedByNow      What a flat-line budget would have spent at this point
 * @param pace               Difference: spentSoFar – expectedByNow (positive = over-pace)
 * @param confidence         Reliability hint driven by how many days of data exist
 * @param periodIsCurrent    True only when today falls inside the period (enables "remaining" copy)
 */
data class SpendingForecast(
    val spentSoFar: BigDecimal,
    val daysElapsed: Int,
    val totalDays: Int,
    val daysRemaining: Int,
    val baseForecast: BigDecimal,
    val trendForecast: BigDecimal,
    val recentDailyAvg: BigDecimal,
    val overallDailyAvg: BigDecimal,
    val expectedByNow: BigDecimal,
    val pace: BigDecimal,             // positive → over-spending vs. flat budget line
    val confidence: ForecastConfidence,
    val periodIsCurrent: Boolean
)

data class BehavioralStatsUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val currency: String = "INR",
    val spendingForecast: SpendingForecast? = null,
    val timeOfDayBuckets: List<TimeOfDayBucket> = emptyList(),
    val dayOfWeekBuckets: List<DayOfWeekBucket> = emptyList(),
    val streakData: StreakData? = null,
    val categoryTrends: List<CategoryTrend> = emptyList(),
    val topMerchants: List<MerchantLoyalty> = emptyList()
)

// ─── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BehavioralStatsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    val useFinancialMonth: StateFlow<Boolean> = userPreferencesRepository.useFinancialMonth
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            _selectedPeriod.value = defaultTimePeriod(
                userPreferencesRepository.useFinancialMonth.first()
            )
        }
    }

    val uiState: StateFlow<BehavioralStatsUiState> = combine(
        _selectedPeriod,
        userPreferencesRepository.monthStartDay,
        userPreferencesRepository.useFinancialMonth,
        userPreferencesRepository.useFixedBudgetPeriodEnd,
        userPreferencesRepository.budgetPeriodEndDay
    ) { period, monthStartDay, useFinancialMonth, useFixedEnd, endDom ->
        listOf(period, monthStartDay, useFinancialMonth, useFixedEnd, endDom)
    }
        .flatMapLatest { args ->
            val period = args[0] as TimePeriod
            val monthStartDay = args[1] as Int
            val useFinancialMonth = args[2] as Boolean
            val useFixedEnd = args[3] as Boolean
            val endDom = args[4] as Int
            val dateRange = getDateRangeForPeriod(
                period,
                monthStartDay,
                useFinancialMonth,
                emptyMap(),
                useFixedEnd,
                endDom
            )
                ?: getDateRangeForPeriod(
                    defaultTimePeriod(useFinancialMonth),
                    monthStartDay,
                    useFinancialMonth,
                    emptyMap(),
                    useFixedEnd,
                    endDom
                )!!

            transactionRepository.getTransactionsBetweenDates(
                startDate = dateRange.first,
                endDate = dateRange.second
            ).mapLatest { transactions ->
                computeStats(transactions, dateRange.first, dateRange.second)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BehavioralStatsUiState(isLoading = true)
        )

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    // ── Core computation ────────────────────────────────────────────────────────

    private fun computeStats(
        allTransactions: List<TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): BehavioralStatsUiState {
        // Only count spending transactions (EXPENSE + CREDIT)
        val spendingTxns = allTransactions.filter {
            it.transactionType == TransactionType.EXPENSE ||
                it.transactionType == TransactionType.CREDIT
        }

        if (spendingTxns.isEmpty()) {
            return BehavioralStatsUiState(isLoading = false, isEmpty = true)
        }

        val currency = spendingTxns.firstOrNull()?.currency ?: "INR"

        return BehavioralStatsUiState(
            isLoading = false,
            isEmpty = false,
            currency = currency,
            spendingForecast = computeSpendingForecast(spendingTxns, startDate, endDate),
            timeOfDayBuckets = computeTimeOfDayBuckets(spendingTxns),
            dayOfWeekBuckets = computeDayOfWeekBuckets(spendingTxns),
            streakData = computeStreakData(spendingTxns, startDate, endDate),
            categoryTrends = computeCategoryTrends(spendingTxns, startDate, endDate),
            topMerchants = computeMerchantLoyalty(spendingTxns)
        )
    }

    // ── Spending Forecast ───────────────────────────────────────────────────────
    // Projects end-of-period spend using two signals:
    //  1. Base forecast   — overall daily average × total days
    //  2. Trend forecast  — already spent + recent 7-day daily avg × remaining days
    // The trend forecast reacts faster to changes in spending behaviour.

    private fun computeSpendingForecast(
        txns: List<TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): SpendingForecast {
        val today = LocalDate.now()

        // Clamp "elapsed" to at most today; treat the last day of the period as inclusive
        val effectiveToday = when {
            today.isBefore(startDate) -> startDate
            today.isAfter(endDate)    -> endDate
            else                      -> today
        }
        val periodIsCurrent = !today.isBefore(startDate) && !today.isAfter(endDate)

        val totalDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)
        val daysElapsed = (ChronoUnit.DAYS.between(startDate, effectiveToday) + 1).toInt().coerceAtLeast(1)
        val daysRemaining = (totalDays - daysElapsed).coerceAtLeast(0)

        val spentSoFar = txns
            .filter { !it.dateTime.toLocalDate().isAfter(effectiveToday) }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val overallDailyAvg = spentSoFar.divide(
            BigDecimal(daysElapsed), 2, RoundingMode.HALF_UP
        )

        // Recent 7-day window (use however many days we actually have, min 1)
        val recentWindowStart = effectiveToday.minusDays(6)
        val recentWindowDays = (ChronoUnit.DAYS.between(recentWindowStart, effectiveToday) + 1)
            .toInt().coerceIn(1, 7)
        val recentSpend = txns
            .filter {
                val d = it.dateTime.toLocalDate()
                !d.isBefore(recentWindowStart) && !d.isAfter(effectiveToday)
            }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
        val recentDailyAvg = recentSpend.divide(
            BigDecimal(recentWindowDays), 2, RoundingMode.HALF_UP
        )

        // 1. Base forecast: flat daily average applied to whole period
        val baseForecast = overallDailyAvg
            .multiply(BigDecimal(totalDays))
            .setScale(2, RoundingMode.HALF_UP)

        // 2. Trend forecast: actual + recent rate × remaining days
        val trendForecast = (spentSoFar + recentDailyAvg.multiply(BigDecimal(daysRemaining)))
            .setScale(2, RoundingMode.HALF_UP)

        // Expected spend if budget were perfectly flat up to today
        val expectedByNow = overallDailyAvg
            .multiply(BigDecimal(daysElapsed))
            .setScale(2, RoundingMode.HALF_UP)
        val pace = spentSoFar.subtract(expectedByNow).setScale(2, RoundingMode.HALF_UP)

        // Confidence rises with more data points and more days elapsed
        val confidence = when {
            daysElapsed >= 14 && txns.size >= 20 -> ForecastConfidence.HIGH
            daysElapsed >= 5  && txns.size >= 5  -> ForecastConfidence.MEDIUM
            else                                  -> ForecastConfidence.LOW
        }

        return SpendingForecast(
            spentSoFar = spentSoFar,
            daysElapsed = daysElapsed,
            totalDays = totalDays,
            daysRemaining = daysRemaining,
            baseForecast = baseForecast,
            trendForecast = trendForecast,
            recentDailyAvg = recentDailyAvg,
            overallDailyAvg = overallDailyAvg,
            expectedByNow = expectedByNow,
            pace = pace,
            confidence = confidence,
            periodIsCurrent = periodIsCurrent
        )
    }

    // ── Time-of-day buckets ─────────────────────────────────────────────────────
    // Morning: 6–12, Afternoon: 12–17, Evening: 17–22, Night: 22–6

    private fun computeTimeOfDayBuckets(txns: List<TransactionEntity>): List<TimeOfDayBucket> {
        data class BucketDef(val label: String, val hourRange: IntRange)
        val defs = listOf(
            BucketDef("Morning", 6..11),
            BucketDef("Afternoon", 12..16),
            BucketDef("Evening", 17..21),
            BucketDef("Night", 22..23)   // 22–23 + 0–5 handled below
        )

        fun hourBucket(hour: Int): Int = when (hour) {
            in 6..11  -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else      -> 3  // 22–5 → Night
        }

        val amounts = Array(4) { BigDecimal.ZERO }
        val counts = IntArray(4)
        for (tx in txns) {
            val idx = hourBucket(tx.dateTime.hour)
            amounts[idx] = amounts[idx] + tx.amount
            counts[idx]++
        }

        val totalAmount = amounts.fold(BigDecimal.ZERO) { acc, v -> acc + v }

        return defs.mapIndexed { idx, def ->
            TimeOfDayBucket(
                label = def.label,
                totalAmount = amounts[idx],
                transactionCount = counts[idx],
                share = if (totalAmount > BigDecimal.ZERO)
                    (amounts[idx].divide(totalAmount, 4, RoundingMode.HALF_UP)).toFloat()
                else 0f
            )
        }
    }

    // ── Day-of-week buckets ─────────────────────────────────────────────────────

    private fun computeDayOfWeekBuckets(txns: List<TransactionEntity>): List<DayOfWeekBucket> {
        val amounts = mutableMapOf<DayOfWeek, BigDecimal>()
        val counts = mutableMapOf<DayOfWeek, Int>()

        for (tx in txns) {
            val dow = tx.dateTime.dayOfWeek
            amounts[dow] = (amounts[dow] ?: BigDecimal.ZERO) + tx.amount
            counts[dow] = (counts[dow] ?: 0) + 1
        }

        val totalAmount = amounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }

        return DayOfWeek.values().map { dow ->
            val amt = amounts[dow] ?: BigDecimal.ZERO
            DayOfWeekBucket(
                dayOfWeek = dow,
                label = dow.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                totalAmount = amt,
                transactionCount = counts[dow] ?: 0,
                share = if (totalAmount > BigDecimal.ZERO)
                    (amt.divide(totalAmount, 4, RoundingMode.HALF_UP)).toFloat()
                else 0f
            )
        }
    }

    // ── Spending streaks ────────────────────────────────────────────────────────
    // "Good day" = day where total spend ≤ rolling daily average for the period

    private fun computeStreakData(
        txns: List<TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): StreakData {
        val today = LocalDate.now()
        val effectiveEnd = if (endDate.isAfter(today)) today else endDate

        val totalDays = (ChronoUnit.DAYS.between(startDate, effectiveEnd) + 1).toInt()
        if (totalDays <= 0) return StreakData(0, 0, 0, 0)

        val totalSpend = txns.sumOf { it.amount.toDouble() }
        val dailyAvg = totalSpend / totalDays

        // Build daily spend map
        val dailySpend = txns.groupBy { it.dateTime.toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.amount.toDouble() } }

        var currentStreak = 0
        var longestStreak = 0
        var runningStreak = 0
        var goodDays = 0

        var currentDate = startDate
        while (!currentDate.isAfter(effectiveEnd)) {
            val daySpend = dailySpend[currentDate] ?: 0.0
            val isGood = daySpend <= dailyAvg
            if (isGood) {
                runningStreak++
                goodDays++
                if (runningStreak > longestStreak) longestStreak = runningStreak
            } else {
                runningStreak = 0
            }
            currentDate = currentDate.plusDays(1)
        }

        // Current streak = from most recent day going back
        currentStreak = 0
        currentDate = effectiveEnd
        while (!currentDate.isBefore(startDate)) {
            val daySpend = dailySpend[currentDate] ?: 0.0
            if (daySpend <= dailyAvg) {
                currentStreak++
                currentDate = currentDate.minusDays(1)
            } else {
                break
            }
        }

        return StreakData(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            goodDaysCount = goodDays,
            totalDays = totalDays
        )
    }

    // ── Category velocity (first-half vs. second-half comparison) ──────────────

    private fun computeCategoryTrends(
        txns: List<TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<CategoryTrend> {
        val midDate = startDate.plusDays(ChronoUnit.DAYS.between(startDate, endDate) / 2)

        val firstHalf = txns.filter { !it.dateTime.toLocalDate().isAfter(midDate) }
        val secondHalf = txns.filter { it.dateTime.toLocalDate().isAfter(midDate) }

        val firstAmounts = firstHalf.groupBy { it.category.ifEmpty { "Others" } }
            .mapValues { (_, list) -> list.sumOf { it.amount.toDouble() }.toBigDecimal() }
        val secondAmounts = secondHalf.groupBy { it.category.ifEmpty { "Others" } }
            .mapValues { (_, list) -> list.sumOf { it.amount.toDouble() }.toBigDecimal() }

        val allCategories = (firstAmounts.keys + secondAmounts.keys).distinct()

        return allCategories.map { cat ->
            val first = firstAmounts[cat] ?: BigDecimal.ZERO
            val second = secondAmounts[cat] ?: BigDecimal.ZERO
            val trendPct = if (first > BigDecimal.ZERO) {
                ((second - first).divide(first, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toFloat()
            } else if (second > BigDecimal.ZERO) {
                100f
            } else {
                0f
            }
            val direction = when {
                trendPct > 10f  -> TrendDirection.GROWING
                trendPct < -10f -> TrendDirection.SHRINKING
                else            -> TrendDirection.STABLE
            }
            CategoryTrend(
                name = cat,
                firstHalfAmount = first,
                secondHalfAmount = second,
                trendPercent = trendPct,
                direction = direction
            )
        }.sortedByDescending { it.secondHalfAmount }.take(8)
    }

    // ── Merchant loyalty ────────────────────────────────────────────────────────

    private fun computeMerchantLoyalty(txns: List<TransactionEntity>): List<MerchantLoyalty> {
        return txns.groupBy { it.merchantName.ifEmpty { "Unknown" } }
            .map { (merchant, list) ->
                val total = list.sumOf { it.amount.toDouble() }.toBigDecimal()
                val avg = total.divide(BigDecimal(list.size), 2, RoundingMode.HALF_UP)
                MerchantLoyalty(
                    name = merchant,
                    visitCount = list.size,
                    avgAmount = avg,
                    totalAmount = total
                )
            }
            .filter { it.visitCount > 1 }  // Only show repeat merchants
            .sortedByDescending { it.visitCount }
            .take(10)
    }
}

