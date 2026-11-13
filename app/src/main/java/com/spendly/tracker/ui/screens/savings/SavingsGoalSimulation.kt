package com.spendly.tracker.ui.screens.savings

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Result of asking "what if I saved a little more each month?".
 *
 * [monthsToGoal] is null when the extra amount is still not enough to make progress (e.g. a
 * zero target with no income). [completionMonth] is the month the plan would finish.
 */
data class SavingsGoalSimulation(
    val extraPerMonth: BigDecimal = BigDecimal.ZERO,
    val newMonthlySavings: BigDecimal = BigDecimal.ZERO,
    val monthsToGoal: Int? = null,
    val completionMonth: YearMonth? = null,
    val newPercentAtDeadline: Float? = null,
    val isAlreadyComplete: Boolean = false,
)

/**
 * Simulates the goal with [extraPerMonth] added on top of the current monthly saving.
 *
 * The completion month is found by dividing the remaining amount by the new monthly saving,
 * then rounding up. [today] is injectable so the month math stays deterministic in tests.
 */
fun simulateExtraSavings(
    plan: SavingsGoalPlan,
    extraPerMonth: BigDecimal,
    today: LocalDate = LocalDate.now(),
): SavingsGoalSimulation {
    val extra = extraPerMonth.coerceAtLeast(BigDecimal.ZERO)
    val newMonthly = plan.monthlySavingsNeeded + extra
    val remaining = plan.remainingToSave
    val isComplete = remaining.signum() <= 0

    val monthsToGoal: Int? = when {
        isComplete -> 0
        newMonthly.signum() <= 0 -> null
        else -> remaining.divide(newMonthly, 0, RoundingMode.CEILING).toInt().coerceAtLeast(1)
    }

    val completionMonth = when {
        monthsToGoal == null -> null
        monthsToGoal == 0 -> YearMonth.from(today)
        else -> YearMonth.from(today).plusMonths((monthsToGoal - 1).toLong())
    }

    val newPercentAtDeadline = if (plan.projectedTotalIncome.signum() > 0) {
        val saved = plan.actualSaved + newMonthly.multiply(BigDecimal(plan.remainingMonths))
        saved.divide(plan.projectedTotalIncome, 4, RoundingMode.HALF_UP).toFloat() * 100f
    } else {
        null
    }

    return SavingsGoalSimulation(
        extraPerMonth = extra,
        newMonthlySavings = newMonthly,
        monthsToGoal = monthsToGoal,
        completionMonth = completionMonth,
        newPercentAtDeadline = newPercentAtDeadline,
        isAlreadyComplete = isComplete,
    )
}
