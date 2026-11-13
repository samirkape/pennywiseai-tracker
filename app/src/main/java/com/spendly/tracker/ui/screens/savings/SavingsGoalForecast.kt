package com.spendly.tracker.ui.screens.savings

import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** One month on the savings-goal forecast line. */
data class GoalForecastPoint(
    val month: YearMonth,
    /** Share of income kept that month; null when the month has no income. */
    val savedPercent: Float?,
    /** Cumulative amount saved by the end of this month, used for milestone progress. */
    val savedAmount: BigDecimal,
    /** True for months after today, where the value is an estimate. */
    val isProjected: Boolean,
)

/** A target milestone and the month the plan is expected to reach it. */
data class GoalMilestone(
    val percent: Int,
    val amount: BigDecimal,
    val month: YearMonth?,
)

data class SavingsGoalForecast(
    val points: List<GoalForecastPoint> = emptyList(),
    val milestones: List<GoalMilestone> = emptyList(),
    val targetPercent: Int = 0,
    val todayMonth: YearMonth = YearMonth.now(),
) {
    val hasData: Boolean get() = points.isNotEmpty()
    val projectedEndMonth: YearMonth? get() = points.lastOrNull()?.month
}

/**
 * Builds the actual-then-projected savings-rate line for the goal card.
 *
 * Past months come from [summary] (the same window as the parent tile). Future months use the
 * plan's projected monthly income and spend, so the line ends at the goal deadline. Milestones
 * are measured against the final target amount, which keeps the chart internally consistent.
 */
fun computeSavingsGoalForecast(
    plan: SavingsGoalPlan,
    summary: SavingsRateSummary,
    today: LocalDate = LocalDate.now(),
): SavingsGoalForecast {
    if (plan.isFixedAmount) {
        return computeFixedGoalForecast(plan = plan, today = today)
    }

    val target = plan.targetPercent.coerceIn(0, 100)
    val todayMonth = YearMonth.from(today)
    val points = mutableListOf<GoalForecastPoint>()

    var cumulativeIncome = BigDecimal.ZERO
    var cumulativeSaved = BigDecimal.ZERO

    // Actual months from the parent tile's window.
    summary.months.forEach { month ->
        cumulativeIncome += month.income
        cumulativeSaved += month.income - month.spent
        points += GoalForecastPoint(
            month = month.month,
            savedPercent = percentOf(cumulativeSaved, cumulativeIncome),
            savedAmount = cumulativeSaved,
            isProjected = false,
        )
    }

    // Future months after the current one (or after a custom range that ended earlier),
    // through the deadline.
    val lastActualMonth = summary.months.lastOrNull()?.month ?: todayMonth
    var month = maxOf(todayMonth, lastActualMonth).plusMonths(1)
    while (!month.isAfter(plan.endMonth)) {
        cumulativeIncome += plan.projectedMonthlyIncome
        cumulativeSaved += plan.projectedMonthlyIncome - plan.projectedMonthlySpend
        points += GoalForecastPoint(
            month = month,
            savedPercent = percentOf(cumulativeSaved, cumulativeIncome),
            savedAmount = cumulativeSaved,
            isProjected = true,
        )
        month = month.plusMonths(1)
    }

    // When the window has no buckets yet, still show the current month.
    if (points.isEmpty()) {
        points += GoalForecastPoint(
            month = todayMonth,
            savedPercent = percentOf(plan.actualSaved, plan.actualIncome),
            savedAmount = plan.actualSaved,
            isProjected = false,
        )
    }

    // Use the plan's required amount so milestone figures match the planner and details sheet.
    val finalTargetAmount = plan.requiredTotalSavings.takeIf { it.signum() > 0 }
        ?: cumulativeIncome.multiply(BigDecimal(target))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    val milestones = listOf(25, 50, 75, 100).map { percent ->
        val amount = finalTargetAmount
            .multiply(BigDecimal(percent))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        // Milestones track money saved toward the target amount, not the rate line.
        GoalMilestone(
            percent = percent,
            amount = amount,
            month = if (amount.signum() <= 0) {
                null
            } else {
                points.firstOrNull { point -> point.savedAmount >= amount }?.month
            },
        )
    }

    return SavingsGoalForecast(
        points = points,
        milestones = milestones,
        targetPercent = target,
        todayMonth = todayMonth,
    )
}

private fun percentOf(saved: BigDecimal, income: BigDecimal): Float? =
    if (income.signum() > 0) {
        saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
    } else {
        null
    }

/**
 * Fixed-amount goals are funded by earmarked contributions, so the forecast starts at the
 * amount set aside so far and spreads the rest of the target across the months left.
 * Spreading the remainder (rather than adding a fixed monthly step) guarantees the line
 * reaches the target in the deadline month, matching the plan's catch-up math.
 * The plotted percentage is progress toward the target rather than a share of income.
 */
private fun computeFixedGoalForecast(
    plan: SavingsGoalPlan,
    today: LocalDate,
): SavingsGoalForecast {
    val todayMonth = YearMonth.from(today)
    val target = plan.requiredTotalSavings
    fun progress(saved: BigDecimal): Float? = if (target.signum() > 0) {
        saved.coerceAtMost(target)
            .divide(target, 4, RoundingMode.HALF_UP)
            .toFloat() * 100f
    } else {
        null
    }

    val points = mutableListOf<GoalForecastPoint>()
    var cumulative = plan.actualSaved
    points += GoalForecastPoint(
        month = todayMonth,
        savedPercent = progress(cumulative),
        savedAmount = cumulative,
        isProjected = false,
    )

    val futureMonths = ChronoUnit.MONTHS.between(todayMonth, plan.endMonth).toInt().coerceAtLeast(0)
    val monthlyStep = if (futureMonths > 0) {
        plan.remainingToSave.divide(BigDecimal(futureMonths), 2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }

    var month = todayMonth.plusMonths(1)
    while (!month.isAfter(plan.endMonth)) {
        cumulative = if (month == plan.endMonth && plan.remainingToSave.signum() > 0) {
            target
        } else {
            (cumulative + monthlyStep).coerceAtMost(target)
        }
        points += GoalForecastPoint(
            month = month,
            savedPercent = progress(cumulative),
            savedAmount = cumulative,
            isProjected = true,
        )
        month = month.plusMonths(1)
    }

    val milestones = listOf(25, 50, 75, 100).map { percent ->
        val amount = target
            .multiply(BigDecimal(percent))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        GoalMilestone(
            percent = percent,
            amount = amount,
            month = if (amount.signum() <= 0) {
                null
            } else {
                points.firstOrNull { point -> point.savedAmount >= amount }?.month
            },
        )
    }

    return SavingsGoalForecast(
        points = points,
        milestones = milestones,
        targetPercent = plan.targetPercent,
        todayMonth = todayMonth,
    )
}
