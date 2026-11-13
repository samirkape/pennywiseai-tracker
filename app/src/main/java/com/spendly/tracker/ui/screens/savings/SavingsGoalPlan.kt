package com.spendly.tracker.ui.screens.savings

import com.spendly.tracker.ui.screens.analytics.SavingsProjection
import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

const val DEFAULT_SAVINGS_GOAL_PERCENT = 70

/** December of the current year, the default "by the end of the year" deadline. */
fun defaultSavingsGoalEndMonth(today: LocalDate = LocalDate.now()): YearMonth =
    YearMonth.from(today).withMonth(12)

/**
 * Rolls a passed goal deadline forward by whole years until it is after [today].
 * Keeping the original month preserves the user's chosen ending season.
 */
fun rolloverSavingsGoalMonth(
    current: YearMonth,
    today: YearMonth = YearMonth.now(),
): YearMonth {
    var next = current.plusYears(1)
    while (!next.isAfter(today)) next = next.plusYears(1)
    return next
}

/** Where the projected monthly income came from, shown to the user for transparency. */
enum class SavingsIncomeBasis {
    SALARY,
    AVERAGE,
    PROJECTION,
    NONE,
}

/**
 * A percentage-of-income savings target with a deadline.
 *
 * The plan measures income and spending from the start of the savings-rate tile's window
 * (the "parent" period) up to today, then works out what is still needed over the months
 * left. That catch-up math means a user who is behind can see the higher monthly amount
 * required to still hit the target on time.
 */
data class SavingsGoalPlan(
    val kind: SavingsGoalKind = SavingsGoalKind.PERCENT,
    val targetPercent: Int,
    val targetAmount: BigDecimal = BigDecimal.ZERO,
    val periodStart: LocalDate,
    val endMonth: YearMonth,
    val remainingMonths: Int,
    val actualIncome: BigDecimal,
    val actualSpent: BigDecimal,
    val actualSaved: BigDecimal,
    val currentPercent: Float?,
    val projectedMonthlyIncome: BigDecimal,
    val projectedMonthlySpend: BigDecimal,
    val incomeBasis: SavingsIncomeBasis,
    val projectedTotalIncome: BigDecimal,
    val requiredTotalSavings: BigDecimal,
    val remainingToSave: BigDecimal,
    val monthlySavingsNeeded: BigDecimal,
    /** This goal's monthly amount plus every other goal's, used for the spendable pool. */
    val totalMonthlySavings: BigDecimal = monthlySavingsNeeded,
    /** The part of [totalMonthlySavings] that belongs to the other goals. */
    val otherMonthlySavings: BigDecimal = BigDecimal.ZERO,
    val spendablePerMonth: BigDecimal,
    val shortfallPerMonth: BigDecimal,
    val cutPerMonth: BigDecimal,
    val onTrack: Boolean,
    val currency: String,
    /** Progress toward the amount that has to be saved; null when no amount is known. */
    val goalProgressPercent: Float? = null,
) {
    val periodEnd: LocalDate get() = endMonth.atEndOfMonth()
    val hasIncome: Boolean get() = projectedTotalIncome.signum() > 0
    val isGoalDatePassed: Boolean get() = remainingMonths <= 0
    val isFixedAmount: Boolean get() = kind == SavingsGoalKind.FIXED
}

/**
 * Builds the monthly save/spend plan for [targetPercent] of income by [endMonth].
 *
 * [actualIncome] and [actualSpent] cover [periodStart] through today. [salaryMonthlyIncome]
 * is the average salary credit in that period, used ahead of the average-income fallback so
 * one-off credits do not distort the plan. [summary] and [projection] supply the final
 * fallback when the period has too little data. [today] is injectable for deterministic tests.
 */
fun computeSavingsGoalPlan(
    targetPercent: Int,
    periodStart: LocalDate,
    endMonth: YearMonth,
    actualIncome: BigDecimal,
    actualSpent: BigDecimal,
    salaryMonthlyIncome: BigDecimal?,
    summary: SavingsRateSummary,
    projection: SavingsProjection?,
    currency: String,
    today: LocalDate = LocalDate.now(),
    goalKind: SavingsGoalKind = SavingsGoalKind.PERCENT,
    targetAmount: BigDecimal = BigDecimal.ZERO,
    contributedAmount: BigDecimal = BigDecimal.ZERO,
    otherMonthlySavings: BigDecimal = BigDecimal.ZERO,
): SavingsGoalPlan {
    val target = targetPercent.coerceIn(0, 100)
    val currentMonth = YearMonth.from(today)
    val remainingMonths = if (endMonth.isBefore(currentMonth)) {
        0
    } else {
        ChronoUnit.MONTHS.between(currentMonth, endMonth).toInt() + 1
    }

    // Use the same window the savings-rate tile above is showing, so the plan's "so far"
    // numbers match what the user sees in the chart.
    val elapsedMonths = if (periodStart.isAfter(today)) {
        0.0
    } else {
        (ChronoUnit.DAYS.between(periodStart, today) + 1) / DAYS_PER_MONTH
    }
    val averageIncome = if (elapsedMonths > 0) {
        actualIncome.divide(BigDecimal.valueOf(elapsedMonths), 2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    val averageSpend = if (elapsedMonths > 0) {
        actualSpent.divide(BigDecimal.valueOf(elapsedMonths), 2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }

    val projectionIncome = monthlyAverage(
        projection = projection,
        fallback = summary.ratedMonths.fold(BigDecimal.ZERO) { acc, m -> acc + m.income },
        fallbackCount = summary.ratedMonths.size,
        selector = { it.projectedIncome },
    )
    val projectionSpend = monthlyAverage(
        projection = projection,
        fallback = summary.months.fold(BigDecimal.ZERO) { acc, m -> acc + m.spent },
        fallbackCount = summary.months.size,
        selector = { it.projectedSpent },
    )

    val salary = salaryMonthlyIncome?.takeIf { it.signum() > 0 }
    val (monthlyIncome, incomeBasis) = when {
        salary != null -> salary to SavingsIncomeBasis.SALARY
        averageIncome.signum() > 0 -> averageIncome to SavingsIncomeBasis.AVERAGE
        projectionIncome.signum() > 0 -> projectionIncome to SavingsIncomeBasis.PROJECTION
        else -> BigDecimal.ZERO to SavingsIncomeBasis.NONE
    }
    val monthlySpend = if (averageSpend.signum() > 0) averageSpend else projectionSpend

    val monthCount = BigDecimal(remainingMonths)
    val isFixedGoal = goalKind == SavingsGoalKind.FIXED
    // A fixed-amount goal is funded only by money explicitly earmarked for it; the main
    // percent goal keeps its own income-minus-spending progress.
    val actualSaved = if (isFixedGoal) {
        contributedAmount.coerceAtLeast(BigDecimal.ZERO)
    } else {
        actualIncome - actualSpent
    }
    val projectedTotalIncome = actualIncome + monthlyIncome.multiply(monthCount)
    val requiredTotalSavings = if (isFixedGoal) {
        targetAmount.coerceAtLeast(BigDecimal.ZERO)
    } else {
        projectedTotalIncome
            .multiply(BigDecimal(target))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }
    val remainingToSave = (requiredTotalSavings - actualSaved).coerceAtLeast(BigDecimal.ZERO)
    val monthlySavingsNeeded = if (remainingMonths > 0) {
        remainingToSave.divide(monthCount, 2, RoundingMode.HALF_UP)
    } else {
        remainingToSave.setScale(2, RoundingMode.HALF_UP)
    }

    // Every goal's monthly amount comes out of the same spendable pool, so fixed goals
    // are funded from what is left after the other goals rather than from the main goal.
    val totalMonthlySavings = monthlySavingsNeeded +
        otherMonthlySavings.coerceAtLeast(BigDecimal.ZERO)
    val spendablePerMonth = (monthlyIncome - totalMonthlySavings).coerceAtLeast(BigDecimal.ZERO)
    val shortfallPerMonth = (totalMonthlySavings - monthlyIncome).coerceAtLeast(BigDecimal.ZERO)
    val cutPerMonth = (monthlySpend - spendablePerMonth).coerceAtLeast(BigDecimal.ZERO)

    // Would the current spending pace land on the target by the deadline?
    val projectedMonthlySaved = monthlyIncome - monthlySpend
    val projectedTotalSaved = actualSaved + projectedMonthlySaved.multiply(monthCount)
    val onTrack = if (isFixedGoal) {
        remainingToSave.signum() <= 0 || shortfallPerMonth.signum() <= 0
    } else {
        projectedTotalSaved >= requiredTotalSavings
    }

    val currentPercent = if (actualIncome.signum() > 0) {
        actualSaved.divide(actualIncome, 4, RoundingMode.HALF_UP).toFloat() * 100f
    } else {
        null
    }
    val goalProgressPercent = if (requiredTotalSavings.signum() > 0) {
        actualSaved.divide(requiredTotalSavings, 4, RoundingMode.HALF_UP).toFloat() * 100f
    } else {
        null
    }

    return SavingsGoalPlan(
        kind = goalKind,
        targetPercent = target,
        targetAmount = targetAmount.coerceAtLeast(BigDecimal.ZERO),
        periodStart = periodStart,
        endMonth = endMonth,
        remainingMonths = remainingMonths,
        actualIncome = actualIncome,
        actualSpent = actualSpent,
        actualSaved = actualSaved,
        currentPercent = currentPercent,
        projectedMonthlyIncome = monthlyIncome,
        projectedMonthlySpend = monthlySpend,
        incomeBasis = incomeBasis,
        projectedTotalIncome = projectedTotalIncome,
        requiredTotalSavings = requiredTotalSavings,
        remainingToSave = remainingToSave,
        monthlySavingsNeeded = monthlySavingsNeeded,
        totalMonthlySavings = totalMonthlySavings,
        otherMonthlySavings = otherMonthlySavings.coerceAtLeast(BigDecimal.ZERO),
        spendablePerMonth = spendablePerMonth,
        shortfallPerMonth = shortfallPerMonth,
        cutPerMonth = cutPerMonth,
        onTrack = onTrack,
        currency = currency,
        goalProgressPercent = goalProgressPercent,
    )
}

private const val DAYS_PER_MONTH = 30.4375

private fun monthlyAverage(
    projection: SavingsProjection?,
    fallback: BigDecimal,
    fallbackCount: Int,
    selector: (SavingsProjection) -> BigDecimal,
): BigDecimal {
    if (projection != null && projection.horizonMonths > 0) {
        return selector(projection)
            .divide(BigDecimal(projection.horizonMonths), 2, RoundingMode.HALF_UP)
    }
    return if (fallbackCount > 0) {
        fallback.divide(BigDecimal(fallbackCount), 2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
}
