package com.pennywiseai.tracker.domain.model

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
    val metadata: Map<String, String> = emptyMap()
)

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
}

enum class InsightConfidence {
    LOW,
    MEDIUM,
    HIGH
}

