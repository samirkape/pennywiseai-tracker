package com.spendly.tracker.widget

import java.math.BigDecimal

/** Cached values rendered by the home-screen savings widget. */
data class SavingsWidgetData(
    val targetPercent: Int = 70,
    val saved: BigDecimal = BigDecimal.ZERO,
    val required: BigDecimal = BigDecimal.ZERO,
    val monthlyNeeded: BigDecimal = BigDecimal.ZERO,
    /** How much is still needed to reach the target; zero once the goal is complete. */
    val remaining: BigDecimal = BigDecimal.ZERO,
    val progressPercent: Float = 0f,
    val currentPercent: Float? = null,
    val endMonth: String = "",
    val currency: String = "INR",
    val hasIncome: Boolean = false,
    val goalName: String = "",
    val isFixedGoal: Boolean = false,
    val safeToSpendPerDay: BigDecimal? = null,
    val daysToDeadline: Long = 0L,
    val isComplete: Boolean = false,
)
