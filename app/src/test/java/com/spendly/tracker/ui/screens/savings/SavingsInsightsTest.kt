package com.spendly.tracker.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsInsightsTest {

    private fun snapshot(
        month: YearMonth,
        income: Int,
        spent: Int,
        target: Int = 70,
    ) = SavingsMonthSnapshot(
        month = month,
        income = BigDecimal(income),
        spent = BigDecimal(spent),
        targetPercent = target,
        capturedAtMillis = 0L,
    )

    private fun plan(
        remaining: Int,
        income: Int,
        spend: Int,
        spendable: Int = 0,
        remainingMonths: Int = 3,
    ) = SavingsGoalPlan(
        targetPercent = 70,
        periodStart = LocalDate.of(2026, 1, 1),
        endMonth = YearMonth.of(2026, 9),
        remainingMonths = remainingMonths,
        actualIncome = BigDecimal(income),
        actualSpent = BigDecimal(spend),
        actualSaved = BigDecimal(income - spend),
        currentPercent = null,
        projectedMonthlyIncome = BigDecimal(income),
        projectedMonthlySpend = BigDecimal(spend),
        incomeBasis = SavingsIncomeBasis.AVERAGE,
        projectedTotalIncome = BigDecimal(income * 12),
        requiredTotalSavings = BigDecimal(remaining + income - spend),
        remainingToSave = BigDecimal(remaining),
        monthlySavingsNeeded = BigDecimal.ZERO,
        spendablePerMonth = BigDecimal(spendable),
        shortfallPerMonth = BigDecimal.ZERO,
        cutPerMonth = BigDecimal.ZERO,
        onTrack = false,
        currency = "INR",
    )

    @Test
    fun attribution_splitsChangeIntoSpendingAndIncome() {
        val previous = snapshot(YearMonth.of(2026, 5), income = 1_000, spent = 400)
        val current = snapshot(YearMonth.of(2026, 6), income = 1_000, spent = 300)

        val change = attributeSavingsRateChange(current, previous)!!

        assertEquals(10f, change.deltaPoints, 0.01f)
        assertEquals(10f, change.spendingPoints, 0.01f)
        assertEquals(0f, change.incomePoints, 0.01f)
        assertEquals(SavingsChangeDriver.SPENDING, change.driver)
    }

    @Test
    fun attribution_returnsNullWithoutPreviousIncome() {
        val previous = snapshot(YearMonth.of(2026, 5), income = 0, spent = 0)
        val current = snapshot(YearMonth.of(2026, 6), income = 1_000, spent = 300)

        assertNull(attributeSavingsRateChange(current, previous))
    }

    @Test
    fun latestChange_skipsMonthsWithoutIncome() {
        val history = listOf(
            snapshot(YearMonth.of(2026, 8), income = 0, spent = 0),
            snapshot(YearMonth.of(2026, 7), income = 1_000, spent = 200),
            snapshot(YearMonth.of(2026, 6), income = 0, spent = 0),
            snapshot(YearMonth.of(2026, 5), income = 1_000, spent = 400),
        )

        val change = latestSavingsRateChange(history)!!

        assertEquals(YearMonth.of(2026, 7), change.currentMonth)
        assertEquals(YearMonth.of(2026, 5), change.previousMonth)
        assertEquals(20f, change.deltaPoints, 0.01f)
    }

    @Test
    fun latestChange_needsTwoMonthsWithIncome() {
        val history = listOf(
            snapshot(YearMonth.of(2026, 7), income = 1_000, spent = 200),
            snapshot(YearMonth.of(2026, 6), income = 0, spent = 0),
        )

        assertNull(latestSavingsRateChange(history))
    }

    @Test
    fun timeToGoal_usesRecentSavingPace() {
        val result = computeTimeToGoal(
            plan = plan(remaining = 1_000, income = 3_000, spend = 1_000),
            today = LocalDate.of(2026, 7, 15),
        )

        assertEquals(1, result.months)
        // Saving the remaining amount in one month finishes in the current month.
        assertEquals(YearMonth.of(2026, 7), result.targetMonth)
        assertTrue(!result.isComplete)
    }

    @Test
    fun timeToGoal_fixedGoalMatchesItsDeadlineWhenOnPlan() {
        val fixedPlan = SavingsGoalPlan(
            kind = SavingsGoalKind.FIXED,
            targetPercent = 70,
            targetAmount = BigDecimal(12_000),
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 9),
            remainingMonths = 3,
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            actualSaved = BigDecimal(6_000),
            currentPercent = null,
            projectedMonthlyIncome = BigDecimal.ZERO,
            projectedMonthlySpend = BigDecimal.ZERO,
            incomeBasis = SavingsIncomeBasis.NONE,
            projectedTotalIncome = BigDecimal.ZERO,
            requiredTotalSavings = BigDecimal(12_000),
            remainingToSave = BigDecimal(6_000),
            monthlySavingsNeeded = BigDecimal(2_000),
            spendablePerMonth = BigDecimal.ZERO,
            shortfallPerMonth = BigDecimal.ZERO,
            cutPerMonth = BigDecimal.ZERO,
            onTrack = true,
            currency = "INR",
            goalProgressPercent = 50f,
        )

        val result = computeTimeToGoal(fixedPlan, today = LocalDate.of(2026, 7, 15))

        assertEquals(3, result.months)
        assertEquals(YearMonth.of(2026, 9), result.targetMonth)
    }

    @Test
    fun timeToGoal_completeWhenNothingIsLeft() {
        val result = computeTimeToGoal(
            plan = plan(remaining = 0, income = 3_000, spend = 1_000),
            today = LocalDate.of(2026, 7, 15),
        )

        assertTrue(result.isComplete)
        assertEquals(0, result.months)
    }

    @Test
    fun timeToGoal_unreachableWhenSpendingExceedsIncome() {
        val result = computeTimeToGoal(
            plan = plan(remaining = 1_000, income = 1_000, spend = 1_500),
            today = LocalDate.of(2026, 7, 15),
        )

        assertTrue(result.isUnreachable)
        assertNull(result.months)
    }

    @Test
    fun safeToSpend_dividesRemainingBudgetAcrossDays() {
        val result = computeSafeToSpend(
            plan = plan(remaining = 1_000, income = 3_000, spend = 1_000, spendable = 1_000),
            today = LocalDate.of(2026, 7, 15),
        )!!

        assertEquals(17, result.daysRemaining)
        assertEquals(58.82, result.perDay.toDouble(), 0.01)
    }

    @Test
    fun safeToSpend_nullWithoutBudget() {
        val result = computeSafeToSpend(
            plan = plan(remaining = 1_000, income = 3_000, spend = 1_000, spendable = 0),
            today = LocalDate.of(2026, 7, 15),
        )

        assertNull(result)
    }

    @Test
    fun streak_countsConsecutiveCompletedMonths() {
        val history = listOf(
            snapshot(YearMonth.of(2026, 6), 1_000, 250),
            snapshot(YearMonth.of(2026, 5), 1_000, 200),
            snapshot(YearMonth.of(2026, 4), 1_000, 500),
            snapshot(YearMonth.of(2026, 3), 1_000, 100),
        )

        val streak = computeSavingsStreak(history, targetPercent = 70, today = YearMonth.of(2026, 7))

        assertEquals(2, streak.current)
        assertEquals(2, streak.best)
    }

    @Test
    fun streak_skipsTheInProgressMonth() {
        val history = listOf(
            snapshot(YearMonth.of(2026, 7), 1_000, 0),
            snapshot(YearMonth.of(2026, 6), 1_000, 500),
        )

        val streak = computeSavingsStreak(history, targetPercent = 70, today = YearMonth.of(2026, 7))

        assertEquals(0, streak.current)
        assertEquals(0, streak.best)
    }

    @Test
    fun trackingSince_usesEarliestMonth() {
        val history = listOf(
            snapshot(YearMonth.of(2026, 6), 1_000, 250),
            snapshot(YearMonth.of(2025, 11), 1_000, 250),
        )

        assertEquals(YearMonth.of(2025, 11), savingsTrackingSince(history))
    }
}
