package com.spendly.tracker.ui.screens.savings

import com.spendly.tracker.ui.screens.analytics.MonthlySavingsRate
import com.spendly.tracker.ui.screens.analytics.SavingsProjection
import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsGoalPlanTest {

    private fun summary(vararg months: MonthlySavingsRate) = SavingsRateSummary(
        months = months.toList(),
        windowMonths = months.size,
        currency = "INR",
    )

    private fun month(year: Int, month: Int, income: Int, spent: Int) = MonthlySavingsRate(
        month = YearMonth.of(year, month),
        start = LocalDate.of(year, month, 1),
        end = YearMonth.of(year, month).atEndOfMonth(),
        income = BigDecimal(income),
        spent = BigDecimal(spent),
        isCurrent = false,
    )

    private fun projection(income: Int, spent: Int, horizon: Int = 3) = SavingsProjection(
        horizonMonths = horizon,
        projectedIncome = BigDecimal(income),
        projectedSpent = BigDecimal(spent),
        projectedSaved = BigDecimal(income - spent),
        projectedPercent = if (income == 0) null else (income - spent).toFloat() / income * 100f,
    )

    @Test
    fun projectionFallback_setsMonthlySaveAndSpend() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 1, 15),
        )

        assertEquals(12, plan.remainingMonths)
        assertEquals(SavingsIncomeBasis.PROJECTION, plan.incomeBasis)
        assertEquals(700.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
        assertEquals(300.0, plan.spendablePerMonth.toDouble(), 0.01)
        assertEquals(100.0, plan.cutPerMonth.toDouble(), 0.01)
        assertNull(plan.currentPercent)
        assertFalse(plan.onTrack)
    }

    @Test
    fun salaryIncome_takesPriorityOverActuals() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal(10_000),
            actualSpent = BigDecimal(6_000),
            salaryMonthlyIncome = BigDecimal(5_000),
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 11, 15),
        )

        assertEquals(2, plan.remainingMonths)
        assertEquals(SavingsIncomeBasis.SALARY, plan.incomeBasis)
        assertEquals(5_000.0, plan.projectedMonthlyIncome.toDouble(), 0.01)
        assertEquals(40f, plan.currentPercent!!, 0.01f)
        // 70% of (10,000 actual + 2 x 5,000 salary) = 14,000; 4,000 already saved.
        assertEquals(5_000.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
        assertEquals(0.0, plan.spendablePerMonth.toDouble(), 0.01)
        assertFalse(plan.onTrack)
    }

    @Test
    fun averageIncome_usedWhenNoSalaryIsDetected() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal(10_000),
            actualSpent = BigDecimal(6_000),
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 11, 15),
        )

        assertEquals(SavingsIncomeBasis.AVERAGE, plan.incomeBasis)
        assertEquals(954.16, plan.projectedMonthlyIncome.toDouble(), 1.0)
        assertEquals(2, plan.remainingMonths)
        // 70% of (10,000 + 2 x ~954) is ~8,336; 4,000 is saved, so ~2,168 over 2 months.
        assertEquals(2_168.0, plan.monthlySavingsNeeded.toDouble(), 2.0)
        assertEquals(1_213.0, plan.shortfallPerMonth.toDouble(), 2.0)
        assertFalse(plan.onTrack)
    }

    @Test
    fun noIncome_reportsNoSpendableAmount() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 0, 400)),
            projection = null,
            currency = "INR",
            today = LocalDate.of(2026, 6, 15),
        )

        assertEquals(SavingsIncomeBasis.NONE, plan.incomeBasis)
        assertFalse(plan.hasIncome)
        assertEquals(0.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
        assertEquals(0.0, plan.spendablePerMonth.toDouble(), 0.01)
    }

    @Test
    fun goalDatePassed_remainingMonthsIsZero() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 10),
            actualIncome = BigDecimal(10_000),
            actualSpent = BigDecimal(6_000),
            salaryMonthlyIncome = BigDecimal(3_000),
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 11, 15),
        )

        assertEquals(0, plan.remainingMonths)
        assertTrue(plan.isGoalDatePassed)
        // No future months: 70% of 10,000 income = 7,000 required, 4,000 saved.
        assertEquals(3_000.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
    }

    @Test
    fun zeroTarget_needsNoSavings() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 0,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal(10_000),
            actualSpent = BigDecimal(2_000),
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 6, 15),
        )

        assertEquals(0.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
        assertTrue(plan.spendablePerMonth.signum() > 0)
        assertTrue(plan.onTrack)
    }

    @Test
    fun overspentPeriod_catchUpIncludesDeficit() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 50,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal(10_000),
            actualSpent = BigDecimal(11_000),
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 11, 15),
        )

        assertEquals(-10f, plan.currentPercent!!, 0.01f)
        // 50% of ~11,908 is ~5,954; the 1,000 deficit is added, spread over 2 months.
        assertEquals(3_477.0, plan.monthlySavingsNeeded.toDouble(), 2.0)
        assertEquals(2_523.0, plan.shortfallPerMonth.toDouble(), 2.0)
    }

    @Test
    fun projectionMissing_fallsBackToSummaryAverages() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 50,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = summary(
                month(2026, 1, income = 1000, spent = 400),
                month(2026, 2, income = 1000, spent = 600),
            ),
            projection = null,
            currency = "INR",
            today = LocalDate.of(2026, 6, 15),
        )

        assertEquals(1000.0, plan.projectedMonthlyIncome.toDouble(), 0.01)
        assertEquals(500.0, plan.projectedMonthlySpend.toDouble(), 0.01)
        assertEquals(500.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
    }

    @Test
    fun onTrack_whenCurrentPaceMeetsTarget() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 40,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 1000, 600)),
            projection = projection(3000, 1800),
            currency = "INR",
            today = LocalDate.of(2026, 1, 15),
        )

        assertTrue(plan.onTrack)
        assertEquals(400.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
        assertEquals(600.0, plan.spendablePerMonth.toDouble(), 0.01)
    }

    @Test
    fun futureYear_spreadsOverFullPeriod() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 50,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2027, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 1, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 11, 15),
        )

        assertEquals(14, plan.remainingMonths)
        assertEquals(0.0, plan.actualIncome.toDouble(), 0.01)
        assertEquals(500.0, plan.monthlySavingsNeeded.toDouble(), 0.01)
        assertEquals(500.0, plan.spendablePerMonth.toDouble(), 0.01)
    }

    @Test
    fun plan_keepsParentWindowStart() {
        val start = LocalDate.of(2026, 3, 1)
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = start,
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = summary(month(2026, 3, 1000, 400)),
            projection = projection(3000, 1200),
            currency = "INR",
            today = LocalDate.of(2026, 6, 15),
        )

        assertEquals(start, plan.periodStart)
        assertEquals(YearMonth.of(2026, 12).atEndOfMonth(), plan.periodEnd)
    }

    @Test
    fun rollover_movesPassedGoalForwardOneYear() {
        val next = rolloverSavingsGoalMonth(
            current = YearMonth.of(2026, 12),
            today = YearMonth.of(2027, 1),
        )

        assertEquals(YearMonth.of(2027, 12), next)
    }

    @Test
    fun rollover_skipsYearsUntilDeadlineIsInFuture() {
        val next = rolloverSavingsGoalMonth(
            current = YearMonth.of(2026, 12),
            today = YearMonth.of(2028, 1),
        )

        assertEquals(YearMonth.of(2028, 12), next)
    }

    @Test
    fun rollover_keepsOriginalMonth() {
        val next = rolloverSavingsGoalMonth(
            current = YearMonth.of(2026, 3),
            today = YearMonth.of(2026, 10),
        )

        assertEquals(YearMonth.of(2027, 3), next)
    }
}
