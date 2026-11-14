package com.spendly.tracker.presentation.transactions

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A single category item used in the filter results breakdown view.
 */
data class FilterCategoryItem(
    val name: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

/**
 * A single merchant item used in the drill-down (category active) breakdown view.
 * Top merchants by amount; the remainder is aggregated into a single "Others" entry.
 */
data class FilterMerchantItem(
    val name: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

/**
 * A single data point for the spending trend chart.
 */
data class FilterTrendPoint(
    val dateTime: LocalDateTime,
    val amount: BigDecimal,
    val label: String
)

/**
 * Computed visualization data for the currently filtered transaction set.
 * Null when there are fewer than 2 transactions.
 */
data class FilterVisualizationData(
    /** Top 6 categories by amount, for the Breakdown tab. */
    val categoryItems: List<FilterCategoryItem>,
    /** Top 5 merchants + Others within the current filter (category drill-down), for the Breakdown tab. */
    val merchantItems: List<FilterMerchantItem> = emptyList(),
    /** Time-bucketed totals (day/week/month), for the Trend tab. */
    val trendPoints: List<FilterTrendPoint>,
    val currency: String,
    /** Human-readable label describing what kind of data is shown, e.g. "Spending", "Income". */
    val dominantTypeLabel: String,
    /**
     * Average spend per day across the filtered window. Null when it cannot be computed
     * (no spend, or a window of unknown length).
     */
    val perDaySpend: BigDecimal? = null,
    /** Number of days the [perDaySpend] average is spread over. */
    val perDaySpendDays: Int = 0
)

