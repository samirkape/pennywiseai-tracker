package com.spendly.tracker.ui.screens.savings

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Which side of the budget moved the savings rate the most between two months. */
enum class SavingsChangeDriver {
    SPENDING,
    INCOME,
    BOTH,
    NONE,
}

/**
 * Month-over-month change of the savings rate, split into the spending and income parts.
 * Values are percentage points. The two parts add up to [deltaPoints] by construction.
 */
data class SavingsRateChange(
    val currentMonth: YearMonth,
    val previousMonth: YearMonth,
    val deltaPoints: Float,
    val spendingPoints: Float,
    val incomePoints: Float,
    val driver: SavingsChangeDriver,
)

/**
 * Attributes the change in savings rate between [previous] and [current].
 *
 * Spending is measured at the previous month's income, so both effects use the same
 * denominator and can be shown as additive percentage points.
 */
fun attributeSavingsRateChange(
    current: SavingsMonthSnapshot,
    previous: SavingsMonthSnapshot,
): SavingsRateChange? {
    val previousRate = previous.ratePercent ?: return null
    val currentRate = current.ratePercent ?: return null
    if (previous.income.signum() <= 0) return null

    val delta = currentRate - previousRate
    val spending = previous.spent
        .subtract(current.spent)
        .divide(previous.income, 4, RoundingMode.HALF_UP)
        .toFloat() * 100f
    val income = delta - spending
    val driver = when {
        delta > -1f && delta < 1f -> SavingsChangeDriver.NONE
        spending > 1f && income > 1f -> SavingsChangeDriver.BOTH
        kotlin.math.abs(spending) > kotlin.math.abs(income) -> SavingsChangeDriver.SPENDING
        kotlin.math.abs(income) > 1f -> SavingsChangeDriver.INCOME
        else -> SavingsChangeDriver.NONE
    }
    return SavingsRateChange(
        currentMonth = current.month,
        previousMonth = previous.month,
        deltaPoints = delta,
        spendingPoints = spending,
        incomePoints = income,
        driver = driver,
    )
}

/**
 * Compares the two most recent months that actually have income, skipping months without
 * income (e.g. the still-in-progress month or a month with no salary). Comparing against
 * an empty month would otherwise hide the attribution line entirely.
 */
fun latestSavingsRateChange(
    history: List<SavingsMonthSnapshot>,
): SavingsRateChange? {
    val rated = history
        .distinctBy { it.month }
        .filter { it.income.signum() > 0 && it.ratePercent != null }
        .sortedByDescending { it.month }
    if (rated.size < 2) return null
    return attributeSavingsRateChange(rated[0], rated[1])
}

/** When the goal will be reached if the recent saving pace continues. */
data class SavingsTimeToGoal(
    val months: Int?,
    val targetMonth: YearMonth?,
    val isComplete: Boolean,
    val isUnreachable: Boolean,
)

/** Projects the completion month from the plan's income and spending averages. */
fun computeTimeToGoal(
    plan: SavingsGoalPlan,
    today: LocalDate = LocalDate.now(),
): SavingsTimeToGoal {
    if (plan.remainingToSave.signum() <= 0) {
        return SavingsTimeToGoal(
            months = 0,
            targetMonth = YearMonth.from(today),
            isComplete = true,
            isUnreachable = false,
        )
    }
    val monthlyPace = if (plan.isFixedAmount) {
        // Fixed goals are funded by earmarked contributions, so the planned monthly
        // amount is the honest pace until contribution history exists.
        plan.monthlySavingsNeeded
    } else {
        plan.projectedMonthlyIncome - plan.projectedMonthlySpend
    }
    if (monthlyPace.signum() <= 0) {
        return SavingsTimeToGoal(
            months = null,
            targetMonth = null,
            isComplete = false,
            isUnreachable = true,
        )
    }
    val months = plan.remainingToSave
        .divide(monthlyPace, 0, RoundingMode.CEILING)
        .toInt()
        .coerceAtLeast(1)
    // The pace counts whole months starting now, so the first month of saving already
    // lands in the current month. Adding the raw month count would push every on-pace
    // goal one month past its deadline.
    return SavingsTimeToGoal(
        months = months,
        targetMonth = YearMonth.from(today).plusMonths((months - 1).toLong()),
        isComplete = false,
        isUnreachable = false,
    )
}

/** How much can still be spent today without breaking the monthly plan. */
data class SafeToSpend(
    val perDay: BigDecimal,
    val daysRemaining: Int,
    val monthlyAmount: BigDecimal,
)

/**
 * Splits the plan's remaining spendable amount across the days left in the current month.
 * Returns null when there is no income basis, the deadline passed, or nothing is left.
 */
fun computeSafeToSpend(
    plan: SavingsGoalPlan,
    today: LocalDate = LocalDate.now(),
): SafeToSpend? {
    if (!plan.hasIncome || plan.isGoalDatePassed) return null
    if (plan.spendablePerMonth.signum() <= 0) return null
    val daysRemaining = today.lengthOfMonth() - today.dayOfMonth + 1
    if (daysRemaining <= 0) return null
    val perDay = plan.spendablePerMonth.divide(
        BigDecimal(daysRemaining),
        2,
        RoundingMode.HALF_UP,
    )
    return SafeToSpend(
        perDay = perDay,
        daysRemaining = daysRemaining,
        monthlyAmount = plan.spendablePerMonth,
    )
}

/** Consecutive months that met the target, plus the best run in the stored history. */
data class SavingsStreak(
    val current: Int = 0,
    val best: Int = 0,
)

/**
 * Counts consecutive months at or above [targetPercent]. The in-progress month is skipped
 * so a streak is only credited once a month has actually finished.
 */
fun computeSavingsStreak(
    history: List<SavingsMonthSnapshot>,
    targetPercent: Int,
    today: YearMonth = YearMonth.now(),
): SavingsStreak {
    val completed = history
        .filter { it.month.isBefore(today) }
        .distinctBy { it.month }
        .sortedByDescending { it.month }
    if (completed.isEmpty()) return SavingsStreak()

    var current = 0
    for (snapshot in completed) {
        val rate = snapshot.ratePercent ?: break
        if (rate >= targetPercent) current++ else break
    }

    var best = 0
    var run = 0
    completed.sortedBy { it.month }.forEach { snapshot ->
        val rate = snapshot.ratePercent
        if (rate != null && rate >= targetPercent) {
            run++
            if (run > best) best = run
        } else {
            run = 0
        }
    }
    return SavingsStreak(current = current, best = best)
}

/** First month the history covers, used for the "tracking since" caption. */
fun savingsTrackingSince(history: List<SavingsMonthSnapshot>): YearMonth? =
    history.minOfOrNull { it.month }
