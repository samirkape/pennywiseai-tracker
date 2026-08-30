package com.spendly.tracker.domain.model

import java.math.BigDecimal

/**
 * Represents a single smart insight generated from transaction data.
 */
data class SmartInsight(
    val id: String,
    val type: InsightType,
    val title: String,
    val primaryValue: String,
    val secondaryText: String,
    val confidence: InsightConfidence = InsightConfidence.HIGH,
    val metadata: Map<String, String> = emptyMap(),
    val scope: InsightScope = InsightScope.PERIOD,
)

/**
 * Distinguishes insights tied to the currently selected month/period from insights that
 * summarize the user's entire transaction history and don't change when the month selector
 * is flipped (e.g. lifetime totals, all-time top merchant).
 */
enum class InsightScope {
    PERIOD,
    LIFETIME
}

enum class InsightType {
    ANOMALY,
    TOP_GROWER,
    MERCHANT_JUMP,
    PACE,
    RECURRING_RATIO,
    SAVINGS_WIN,
    TOP_CATEGORIES,
    LARGEST_EXPENSE,
    WEEKEND_SPEND,
    INCOME_VS_EXPENSE,
    INVESTMENT_RATIO,
    NEW_MERCHANTS,
    MERCHANT_LOYALTY,
    TRANSACTION_FREQUENCY,
    MONTHLY_COMPARISON,
    ZERO_SPEND_DAYS,
    PEAK_SPEND_DAY,
    SPEND_SPLIT,

    // Lifetime (period-agnostic) insight types
    LIFETIME_TOP_MERCHANT,
    LIFETIME_TOTAL_SPEND,
    LIFETIME_NO_SPEND_STREAK,
    LIFETIME_HIGHEST_MONTH,
    LIFETIME_AVG_MONTHLY_SPEND,
    LIFETIME_MERCHANT_LOYALTY,
    LIFETIME_MEMBER_SINCE,
}

enum class InsightConfidence {
    LOW,
    MEDIUM,
    HIGH
}

