package com.spendly.tracker.ui.screens.savings

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SavingsPaceStatus {
    AHEAD,
    ON_TRACK,
    BEHIND,
    CRITICAL,
    NO_INCOME,
}

/**
 * How the user is doing against the pace needed for the percentage target.
 *
 * [gap] is positive when the user is behind (expected saved minus actual saved). [milestoneCrossed]
 * is the highest 25/50/75/100 milestone reached that has not been notified yet.
 */
data class SavingsPace(
    val status: SavingsPaceStatus,
    val expectedSaved: BigDecimal,
    val actualSaved: BigDecimal,
    val gap: BigDecimal,
    val milestoneCrossed: Int?,
    val daysToDeadline: Long,
) {
    val isBehind: Boolean
        get() = status == SavingsPaceStatus.BEHIND || status == SavingsPaceStatus.CRITICAL
}

/**
 * Evaluates whether the user is on pace to hit [plan]'s target by today's date.
 *
 * The expected amount is the target share of the income actually earned so far. A small
 * tolerance band keeps normal month-to-month noise from triggering alerts. [lastMilestoneNotified]
 * lets the caller avoid re-notifying a milestone that was already celebrated.
 */
fun evaluateSavingsPace(
    plan: SavingsGoalPlan,
    lastMilestoneNotified: Int = 0,
    today: LocalDate = LocalDate.now(),
): SavingsPace {
    val daysToDeadline = ChronoUnit.DAYS.between(today, plan.periodEnd)

    if (plan.actualIncome.signum() <= 0 && plan.kind != SavingsGoalKind.FIXED) {
        return SavingsPace(
            status = SavingsPaceStatus.NO_INCOME,
            expectedSaved = BigDecimal.ZERO,
            actualSaved = plan.actualSaved,
            gap = BigDecimal.ZERO,
            milestoneCrossed = null,
            daysToDeadline = daysToDeadline,
        )
    }

    val targetShare = BigDecimal(plan.targetPercent)
        .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
    val expected: BigDecimal
    val tolerance: BigDecimal
    if (plan.kind == SavingsGoalKind.FIXED) {
        // Fixed goals are paced against time: by today the user should have saved the
        // share of the goal amount that matches the share of the planning period elapsed.
        val totalDays = ChronoUnit.DAYS.between(plan.periodStart, plan.periodEnd)
            .coerceAtLeast(1)
        val elapsedDays = ChronoUnit.DAYS.between(plan.periodStart, today).coerceIn(0, totalDays)
        val goalAmount = plan.targetAmount.coerceAtLeast(BigDecimal.ZERO)
        expected = goalAmount
            .multiply(BigDecimal(elapsedDays))
            .divide(BigDecimal(totalDays), 2, RoundingMode.HALF_UP)
        tolerance = goalAmount.multiply(BigDecimal("0.02"))
    } else {
        expected = plan.actualIncome.multiply(targetShare)
        tolerance = plan.actualIncome.multiply(BigDecimal("0.02"))
    }
    val actual = plan.actualSaved
    val gap = expected - actual

    val status = when {
        gap <= tolerance.negate() -> SavingsPaceStatus.AHEAD
        gap.abs() <= tolerance -> SavingsPaceStatus.ON_TRACK
        gap > tolerance.multiply(BigDecimal(3)) -> SavingsPaceStatus.CRITICAL
        else -> SavingsPaceStatus.BEHIND
    }

    val reachedPercent = if (plan.requiredTotalSavings.signum() > 0) {
        actual
            .divide(plan.requiredTotalSavings, 4, RoundingMode.HALF_UP)
            .toFloat() * 100f
    } else {
        0f
    }
    val milestone = listOf(100, 75, 50, 25)
        .firstOrNull { reachedPercent >= it && it > lastMilestoneNotified }

    return SavingsPace(
        status = status,
        expectedSaved = expected,
        actualSaved = actual,
        gap = gap,
        milestoneCrossed = milestone,
        daysToDeadline = daysToDeadline,
    )
}
