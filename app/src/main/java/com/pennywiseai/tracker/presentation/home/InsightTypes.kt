package com.pennywiseai.tracker.presentation.home

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
    LOW_REMAINING
}

enum class InsightSeverity {
    INFO,
    CAUTION,
    ALERT
}

