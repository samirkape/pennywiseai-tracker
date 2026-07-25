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
    /** Time-bucketed totals (day/week/month), for the Trend tab. */
    val trendPoints: List<FilterTrendPoint>,
    val currency: String,
    /** Human-readable label describing what kind of data is shown, e.g. "Spending", "Income". */
    val dominantTypeLabel: String
)

