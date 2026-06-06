package com.pennywiseai.tracker.ui.screens.behavioral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.SalaryMonthOverrideRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.defaultTimePeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForYearMonthNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
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
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val spendingForecast: SpendingForecast? = null,
    val timeOfDayBuckets: List<TimeOfDayBucket> = emptyList(),
    val dayOfWeekBuckets: List<DayOfWeekBucket> = emptyList(),
    val topMerchants: List<MerchantLoyalty> = emptyList(),
    val topTags: List<TagData> = emptyList(),
    val categoryOverlaps: List<CategoryOverlapData> = emptyList(),
    val multiCategoryTransactions: List<MultiCategoryTransactionData> = emptyList(),
)

// ─── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BehavioralStatsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    val useFinancialMonth: StateFlow<Boolean> = userPreferencesRepository.useFinancialMonth
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _periodAnchorMonthKey = savedStateHandle.getStateFlow<String?>("periodAnchorMonth", null)
    private val periodNavUsesCalendarKey = "periodNavUsesCalendar"

    val periodAnchorMonth: StateFlow<YearMonth?> = _periodAnchorMonthKey
        .map { key -> key?.let { YearMonth.parse(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val default = defaultTimePeriod(userPreferencesRepository.useFinancialMonth.first())
            _selectedPeriod.value = default
            updatePeriodAnchorForChip(default)
        }
    }

    val uiState: StateFlow<BehavioralStatsUiState> = combine(
        combine(
            _selectedPeriod,
            _periodAnchorMonthKey,
        ) { period, anchorKey -> period to anchorKey },
        combine(
            userPreferencesRepository.monthStartDay,
            userPreferencesRepository.useFinancialMonth,
            userPreferencesRepository.useFixedBudgetPeriodEnd,
            userPreferencesRepository.budgetPeriodEndDay,
            salaryMonthOverrideRepository.overridesMap,
        ) { monthStartDay, useFinancialMonth, useFixedEnd, endDom, overrides ->
            listOf(monthStartDay, useFinancialMonth, useFixedEnd, endDom, overrides)
        },
    ) { periodPack, prefsPack ->
        val period = periodPack.first
        @Suppress("UNCHECKED_CAST")
        val prefs = prefsPack as List<Any?>
        listOf(
            period,
            periodPack.second,
            prefs[0],
            prefs[1],
            prefs[2],
            prefs[3],
            prefs[4],
        )
    }
        .flatMapLatest { args ->
            val period = args[0] as TimePeriod
            val monthStartDay = args[2] as Int
            val useFinancialMonth = args[3] as Boolean
            val useFixedEnd = args[4] as Boolean
            val endDom = args[5] as Int
            @Suppress("UNCHECKED_CAST")
            val overrides = args[6] as Map<String, Int>
            val dateRange = resolveDateRange(
                period = period,
                monthStartDay = monthStartDay,
                useFinancialMonth = useFinancialMonth,
                monthStartOverrides = overrides,
                useFixedEnd = useFixedEnd,
                endDom = endDom,
            )
                ?: getDateRangeForPeriod(
                    defaultTimePeriod(useFinancialMonth),
                    monthStartDay,
                    useFinancialMonth,
                    overrides,
                    useFixedEnd,
                    endDom,
                )!!

            val forecastPeriodEnd = resolveForecastPeriodEnd(
                period = period,
                startDate = dateRange.first,
                queryEndDate = dateRange.second,
                monthStartDay = monthStartDay,
                useFinancialMonth = useFinancialMonth,
                monthStartOverrides = overrides,
                useFixedEnd = useFixedEnd,
                endDom = endDom,
            )

            transactionRepository.getTransactionsBetweenDates(
                startDate = dateRange.first,
                endDate = dateRange.second
            ).mapLatest { transactions ->
                computeStats(
                    allTransactions = transactions,
                    startDate = dateRange.first,
                    queryEndDate = dateRange.second,
                    forecastPeriodEnd = forecastPeriodEnd,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BehavioralStatsUiState(isLoading = true)
        )

    fun selectPeriod(period: TimePeriod) {
        updatePeriodAnchorForChip(period)
        _selectedPeriod.value = period
    }

    fun navigateToMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val monthStartDay = userPreferencesRepository.monthStartDay.first()
            val payPeriodEnabled = userPreferencesRepository.useFinancialMonth.first()
            val useFixedEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
            val endDom = userPreferencesRepository.budgetPeriodEndDay.first()
            val usesCalendar = resolveUsesCalendarMonthNavigation()
            when {
                yearMonth == YearMonth.now() && usesCalendar ->
                    selectPeriod(TimePeriod.CALENDAR_MONTH)
                yearMonth == YearMonth.now() && !usesCalendar && payPeriodEnabled ->
                    selectPeriod(TimePeriod.THIS_MONTH)
                yearMonth == YearMonth.now() && !usesCalendar && !payPeriodEnabled ->
                    selectPeriod(TimePeriod.CALENDAR_MONTH)
                else -> {
                    savedStateHandle["periodAnchorMonth"] = yearMonth.toString()
                }
            }
        }
    }

    private fun updatePeriodAnchorForChip(period: TimePeriod) {
        val anchor = when (period) {
            TimePeriod.THIS_MONTH, TimePeriod.CALENDAR_MONTH -> YearMonth.now()
            TimePeriod.LAST_MONTH -> YearMonth.now().minusMonths(1)
            TimePeriod.ALL, TimePeriod.CURRENT_FY -> null
            TimePeriod.CUSTOM -> return
        }
        savedStateHandle["periodAnchorMonth"] = anchor?.toString()
        when (period) {
            TimePeriod.CALENDAR_MONTH -> savedStateHandle[periodNavUsesCalendarKey] = true
            TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH -> savedStateHandle[periodNavUsesCalendarKey] = false
            TimePeriod.ALL, TimePeriod.CURRENT_FY -> savedStateHandle.remove<Boolean>(periodNavUsesCalendarKey)
            TimePeriod.CUSTOM -> Unit
        }
    }

    private fun resolveUsesCalendarMonthNavigation(): Boolean {
        savedStateHandle.get<Boolean>(periodNavUsesCalendarKey)?.let { return it }
        return _selectedPeriod.value == TimePeriod.CALENDAR_MONTH
    }

    private fun naturalAnchorForPeriod(period: TimePeriod): YearMonth? = when (period) {
        TimePeriod.THIS_MONTH, TimePeriod.CALENDAR_MONTH -> YearMonth.now()
        TimePeriod.LAST_MONTH -> YearMonth.now().minusMonths(1)
        else -> null
    }

    private fun resolveDateRange(
        period: TimePeriod,
        monthStartDay: Int,
        useFinancialMonth: Boolean,
        monthStartOverrides: Map<String, Int>,
        useFixedEnd: Boolean,
        endDom: Int,
    ): Pair<LocalDate, LocalDate>? {
        val anchorMonth = _periodAnchorMonthKey.value?.let { YearMonth.parse(it) }
        val usesCalendar = resolveUsesCalendarMonthNavigation()
        val navigablePeriod = period == TimePeriod.THIS_MONTH ||
            period == TimePeriod.CALENDAR_MONTH ||
            period == TimePeriod.LAST_MONTH

        if (anchorMonth != null && navigablePeriod && anchorMonth != naturalAnchorForPeriod(period)) {
            return getDateRangeForYearMonthNavigation(
                yearMonth = anchorMonth,
                useCalendarMonth = usesCalendar,
                monthStartDay = monthStartDay,
                monthStartOverrides = monthStartOverrides,
                useFixedBudgetPeriodEnd = useFixedEnd,
                budgetPeriodEndDay = endDom,
            )
        }

        return getDateRangeForPeriod(
            period,
            monthStartDay,
            useFinancialMonth,
            monthStartOverrides,
            useFixedEnd,
            endDom,
        )
    }

    /**
     * Full period boundary for forecast math. Query ranges for calendar month cap at today,
     * but projections need the real period end (e.g. month-end).
     */
    private fun resolveForecastPeriodEnd(
        period: TimePeriod,
        startDate: LocalDate,
        queryEndDate: LocalDate,
        monthStartDay: Int,
        useFinancialMonth: Boolean,
        monthStartOverrides: Map<String, Int>,
        useFixedEnd: Boolean,
        endDom: Int,
    ): LocalDate {
        val anchorMonth = _periodAnchorMonthKey.value?.let { YearMonth.parse(it) }
        return when (period) {
            TimePeriod.CALENDAR_MONTH -> {
                val ym = anchorMonth ?: YearMonth.from(startDate)
                ym.atEndOfMonth()
            }
            TimePeriod.THIS_MONTH -> {
                if (useFinancialMonth) {
                    if (anchorMonth != null && anchorMonth != YearMonth.now()) {
                        getDateRangeForYearMonthNavigation(
                            yearMonth = anchorMonth,
                            useCalendarMonth = false,
                            monthStartDay = monthStartDay,
                            monthStartOverrides = monthStartOverrides,
                            useFixedBudgetPeriodEnd = useFixedEnd,
                            budgetPeriodEndDay = endDom,
                        ).second
                    } else {
                        getDateRangeForPeriod(
                            period,
                            monthStartDay,
                            useFinancialMonth,
                            monthStartOverrides,
                            useFixedEnd,
                            endDom,
                        )!!.second
                    }
                } else {
                    (anchorMonth ?: YearMonth.from(startDate)).atEndOfMonth()
                }
            }
            else -> queryEndDate
        }
    }

    // ── Core computation ────────────────────────────────────────────────────────

    private fun computeStats(
        allTransactions: List<TransactionEntity>,
        startDate: LocalDate,
        queryEndDate: LocalDate,
        forecastPeriodEnd: LocalDate,
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
        val tagInsights = computeTagInsights(spendingTxns)

        return BehavioralStatsUiState(
            isLoading = false,
            isEmpty = false,
            currency = currency,
            periodStart = startDate,
            periodEnd = queryEndDate,
            spendingForecast = computeSpendingForecast(spendingTxns, startDate, forecastPeriodEnd),
            timeOfDayBuckets = computeTimeOfDayBuckets(spendingTxns),
            dayOfWeekBuckets = computeDayOfWeekBuckets(spendingTxns),
            topMerchants = computeMerchantLoyalty(spendingTxns),
            topTags = tagInsights.topTags,
            categoryOverlaps = tagInsights.categoryOverlaps,
            multiCategoryTransactions = tagInsights.multiCategoryTransactions,
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

    // ── Tag insights ────────────────────────────────────────────────────────────

    private data class TagInsightsResult(
        val topTags: List<TagData>,
        val categoryOverlaps: List<CategoryOverlapData>,
        val multiCategoryTransactions: List<MultiCategoryTransactionData>,
    )

    private fun computeTagInsights(txns: List<TransactionEntity>): TagInsightsResult {
        val tagsByTx = txns
            .filter { it.tags.isNotBlank() }
            .associate { tx ->
                tx.id to tx.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }

        val pairCounts = mutableMapOf<Pair<String, String>, Int>()
        for ((_, tags) in tagsByTx) {
            if (tags.size < 2) continue
            val sorted = tags.sorted()
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    val pair = sorted[i] to sorted[j]
                    pairCounts[pair] = (pairCounts[pair] ?: 0) + 1
                }
            }
        }
        val categoryOverlaps = pairCounts
            .map { (pair, count) -> CategoryOverlapData(pair.first, pair.second, count) }
            .sortedByDescending { it.coOccurrenceCount }
            .take(10)

        val multiCategoryTransactions = tagsByTx
            .filter { (_, tags) -> tags.size >= 2 }
            .mapNotNull { (txId, tags) ->
                txns.find { it.id == txId }?.let { tx ->
                    MultiCategoryTransactionData(
                        transactionId = txId,
                        merchantName = tx.merchantName,
                        amount = tx.amount,
                        dateTime = tx.dateTime,
                        categories = tags,
                        currency = tx.currency,
                    )
                }
            }
            .sortedByDescending { it.dateTime }
            .take(20)

        val tagCountMap = mutableMapOf<String, Int>()
        val tagAmountMap = mutableMapOf<String, BigDecimal>()
        for ((txId, tags) in tagsByTx) {
            val txAmount = txns.find { it.id == txId }?.amount ?: BigDecimal.ZERO
            for (tag in tags) {
                tagCountMap[tag] = (tagCountMap[tag] ?: 0) + 1
                tagAmountMap[tag] = (tagAmountMap[tag] ?: BigDecimal.ZERO) + txAmount
            }
        }
        val topTags = tagCountMap.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (tag, count) ->
                TagData(
                    name = tag,
                    transactionCount = count,
                    totalAmount = tagAmountMap[tag] ?: BigDecimal.ZERO,
                )
            }

        return TagInsightsResult(
            topTags = topTags,
            categoryOverlaps = categoryOverlaps,
            multiCategoryTransactions = multiCategoryTransactions,
        )
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

