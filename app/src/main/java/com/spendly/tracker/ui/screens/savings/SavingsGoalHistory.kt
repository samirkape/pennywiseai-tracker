package com.spendly.tracker.ui.screens.savings

import com.spendly.tracker.ui.screens.analytics.MonthlySavingsRate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * One month of the savings goal's history. Snapshots are captured from the same monthly
 * buckets shown on the savings-rate card, then persisted so the history survives even after
 * those months fall outside the visible window.
 */
data class SavingsMonthSnapshot(
    val month: YearMonth,
    val income: BigDecimal,
    val spent: BigDecimal,
    val targetPercent: Int,
    val capturedAtMillis: Long,
) {
    val saved: BigDecimal get() = income - spent

    /** Percent of income saved; null when the month has no income. */
    val ratePercent: Float?
        get() = if (income.signum() > 0) {
            saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
        } else {
            null
        }

    fun withTarget(targetPercent: Int, capturedAtMillis: Long): SavingsMonthSnapshot =
        copy(targetPercent = targetPercent.coerceIn(0, 100), capturedAtMillis = capturedAtMillis)
}

/** Builds history entries from monthly savings buckets, newest first. */
fun buildSavingsHistory(
    months: List<MonthlySavingsRate>,
    targetPercent: Int,
    capturedAtMillis: Long,
    limit: Int = 12,
): List<SavingsMonthSnapshot> =
    months
        .sortedByDescending { it.month }
        .take(limit.coerceAtLeast(0))
        .map { month ->
            SavingsMonthSnapshot(
                month = month.month,
                income = month.income,
                spent = month.spent,
                targetPercent = targetPercent.coerceIn(0, 100),
                capturedAtMillis = capturedAtMillis,
            )
        }
