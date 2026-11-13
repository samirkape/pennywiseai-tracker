package com.spendly.tracker.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsPaceEvaluatorTest {

    private val today = LocalDate.of(2026, 12, 1)

    private fun plan(
        targetPercent: Int = 70,
        actualIncome: BigDecimal = BigDecimal(10_000),
        actualSaved: BigDecimal = BigDecimal(7_000),
        requiredTotalSavings: BigDecimal = BigDecimal(10_000),
    ) = SavingsGoalPlan(
        targetPercent = targetPercent,
        periodStart = LocalDate.of(2026, 1, 1),
        endMonth = YearMonth.of(2026, 12),
        remainingMonths = 1,
        actualIncome = actualIncome,
        actualSpent = actualIncome - actualSaved,
        actualSaved = actualSaved,
        currentPercent = null,
        projectedMonthlyIncome = BigDecimal.ZERO,
        projectedMonthlySpend = BigDecimal.ZERO,
        incomeBasis = SavingsIncomeBasis.NONE,
        projectedTotalIncome = BigDecimal.ZERO,
        requiredTotalSavings = requiredTotalSavings,
        remainingToSave = BigDecimal.ZERO,
        monthlySavingsNeeded = BigDecimal.ZERO,
        spendablePerMonth = BigDecimal.ZERO,
        shortfallPerMonth = BigDecimal.ZERO,
        cutPerMonth = BigDecimal.ZERO,
        onTrack = false,
        currency = "INR",
    )

    @Test
    fun onTrack_whenSavedMatchesTargetShare() {
        val pace = evaluateSavingsPace(plan(), today = today)

        assertEquals(SavingsPaceStatus.ON_TRACK, pace.status)
        assertEquals(0.0, pace.gap.toDouble(), 0.01)
        assertFalse(pace.isBehind)
    }

    @Test
    fun ahead_whenSavedExceedsTargetShare() {
        val pace = evaluateSavingsPace(
            plan = plan(actualSaved = BigDecimal(7_500)),
            today = today,
        )

        assertEquals(SavingsPaceStatus.AHEAD, pace.status)
        assertTrue(pace.gap.signum() < 0)
        assertFalse(pace.isBehind)
    }

    @Test
    fun behind_whenGapExceedsToleranceButNotCritical() {
        val pace = evaluateSavingsPace(
            plan = plan(actualSaved = BigDecimal(6_400)),
            today = today,
        )

        assertEquals(SavingsPaceStatus.BEHIND, pace.status)
        assertEquals(600.0, pace.gap.toDouble(), 0.01)
        assertTrue(pace.isBehind)
    }

    @Test
    fun critical_whenGapIsMoreThanThreeTimesTolerance() {
        val pace = evaluateSavingsPace(
            plan = plan(actualSaved = BigDecimal(5_000)),
            today = today,
        )

        assertEquals(SavingsPaceStatus.CRITICAL, pace.status)
        assertEquals(2_000.0, pace.gap.toDouble(), 0.01)
        assertTrue(pace.isBehind)
    }

    @Test
    fun noIncome_returnsNoIncomeStatus() {
        val pace = evaluateSavingsPace(
            plan = plan(actualIncome = BigDecimal.ZERO, actualSaved = BigDecimal.ZERO),
            today = today,
        )

        assertEquals(SavingsPaceStatus.NO_INCOME, pace.status)
        assertEquals(0.0, pace.expectedSaved.toDouble(), 0.01)
        assertNull(pace.milestoneCrossed)
    }

    @Test
    fun milestone_returnsHighestNewlyReachedPercent() {
        val pace = evaluateSavingsPace(
            plan = plan(actualSaved = BigDecimal(6_000)),
            lastMilestoneNotified = 25,
            today = today,
        )

        // 6,000 of the 10,000 goal is 60%, so the 50% milestone is the newest one.
        assertEquals(50, pace.milestoneCrossed)
    }

    @Test
    fun milestone_isNullWhenAlreadyNotified() {
        val pace = evaluateSavingsPace(
            plan = plan(actualSaved = BigDecimal(6_000)),
            lastMilestoneNotified = 50,
            today = today,
        )

        assertNull(pace.milestoneCrossed)
    }

    @Test
    fun daysToDeadline_countsToEndOfGoalMonth() {
        val pace = evaluateSavingsPace(plan(), today = today)

        assertEquals(30L, pace.daysToDeadline)
    }
}
