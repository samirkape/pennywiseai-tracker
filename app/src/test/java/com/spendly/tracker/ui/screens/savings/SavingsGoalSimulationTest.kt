package com.spendly.tracker.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsGoalSimulationTest {

    private val today = LocalDate.of(2026, 1, 15)

    private fun plan(
        remainingToSave: BigDecimal = BigDecimal(12_000),
        monthlySavingsNeeded: BigDecimal = BigDecimal(1_000),
        remainingMonths: Int = 12,
        projectedTotalIncome: BigDecimal = BigDecimal(24_000),
        actualSaved: BigDecimal = BigDecimal.ZERO,
    ) = SavingsGoalPlan(
        targetPercent = 50,
        periodStart = LocalDate.of(2026, 1, 1),
        endMonth = YearMonth.of(2026, 12),
        remainingMonths = remainingMonths,
        actualIncome = actualSaved,
        actualSpent = BigDecimal.ZERO,
        actualSaved = actualSaved,
        currentPercent = null,
        projectedMonthlyIncome = BigDecimal(2_000),
        projectedMonthlySpend = BigDecimal.ZERO,
        incomeBasis = SavingsIncomeBasis.SALARY,
        projectedTotalIncome = projectedTotalIncome,
        requiredTotalSavings = actualSaved + remainingToSave,
        remainingToSave = remainingToSave,
        monthlySavingsNeeded = monthlySavingsNeeded,
        spendablePerMonth = BigDecimal.ZERO,
        shortfallPerMonth = BigDecimal.ZERO,
        cutPerMonth = BigDecimal.ZERO,
        onTrack = false,
        currency = "INR",
    )

    @Test
    fun extraSavings_shortensTimeToGoal() {
        val simulation = simulateExtraSavings(
            plan = plan(),
            extraPerMonth = BigDecimal(1_000),
            today = today,
        )

        assertEquals(2_000.0, simulation.newMonthlySavings.toDouble(), 0.01)
        assertEquals(6, simulation.monthsToGoal)
        assertEquals(YearMonth.of(2026, 6), simulation.completionMonth)
        assertEquals(100f, simulation.newPercentAtDeadline!!, 0.01f)
        assertFalse(simulation.isAlreadyComplete)
    }

    @Test
    fun noExtraSavings_keepsOriginalCompletionMonth() {
        val simulation = simulateExtraSavings(
            plan = plan(),
            extraPerMonth = BigDecimal.ZERO,
            today = today,
        )

        assertEquals(12, simulation.monthsToGoal)
        assertEquals(YearMonth.of(2026, 12), simulation.completionMonth)
    }

    @Test
    fun largeExtraSavings_finishesSooner() {
        val simulation = simulateExtraSavings(
            plan = plan(),
            extraPerMonth = BigDecimal(5_000),
            today = today,
        )

        assertEquals(2, simulation.monthsToGoal)
        assertEquals(YearMonth.of(2026, 2), simulation.completionMonth)
    }

    @Test
    fun negativeExtra_isTreatedAsZero() {
        val simulation = simulateExtraSavings(
            plan = plan(),
            extraPerMonth = BigDecimal(-500),
            today = today,
        )

        assertEquals(0.0, simulation.extraPerMonth.toDouble(), 0.01)
        assertEquals(1_000.0, simulation.newMonthlySavings.toDouble(), 0.01)
        assertEquals(12, simulation.monthsToGoal)
    }

    @Test
    fun completedGoal_reportsAlreadyComplete() {
        val simulation = simulateExtraSavings(
            plan = plan(remainingToSave = BigDecimal.ZERO),
            extraPerMonth = BigDecimal.ZERO,
            today = today,
        )

        assertTrue(simulation.isAlreadyComplete)
        assertEquals(0, simulation.monthsToGoal)
        assertEquals(YearMonth.of(2026, 1), simulation.completionMonth)
    }

    @Test
    fun noMonthlyProgress_returnsNullCompletion() {
        val simulation = simulateExtraSavings(
            plan = plan(
                remainingToSave = BigDecimal(500),
                monthlySavingsNeeded = BigDecimal.ZERO,
                remainingMonths = 0,
            ),
            extraPerMonth = BigDecimal.ZERO,
            today = today,
        )

        assertNull(simulation.monthsToGoal)
        assertNull(simulation.completionMonth)
        assertFalse(simulation.isAlreadyComplete)
    }
}
