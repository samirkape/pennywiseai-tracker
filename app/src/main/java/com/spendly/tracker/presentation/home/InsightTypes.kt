package com.spendly.tracker.presentation.home

/**
 * A single rule-based spending insight shown in the Smart Insights card.
 */
data class SpendInsight(
    val type: InsightType,
    val title: String,
    val body: String,
    val severity: InsightSeverity,
    val actionLabel: String? = null
)

enum class InsightType {
    PACE_PREDICTION,
    CATEGORY_SPIKE,
    SUBSCRIPTION_UPCOMING,
    GOAL_MILESTONE,
    WEEK_TREND,
    LOW_REMAINING,
    LOAN_REMINDER,
    CREDIT_CARD_ALERT,
    DAILY_TREND,
    BALANCE_HEALTH,
    PERIOD_END,
}

enum class InsightSeverity {
    INFO,
    CAUTION,
    ALERT
}

