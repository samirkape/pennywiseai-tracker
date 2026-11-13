package com.spendly.tracker.ui.screens.savings

import com.spendly.tracker.ui.screens.analytics.MonthlySavingsRate
import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsGoalForecastTest {

    private val today = LocalDate.of(2026, 3, 15)

    private fun month(year: Int, month: Int, income: Int, spent: Int) = MonthlySavingsRate(
        month = YearMonth.of(year, month),
        start = LocalDate.of(year, month, 1),
        end = YearMonth.of(year, month).atEndOfMonth(),
        income = BigDecimal(income),
        spent = BigDecimal(spent),
        isCurrent = false,
    )

    private fun summary(vararg months: MonthlySavingsRate) = SavingsRateSummary(
        months = months.toList(),
        windowMonths = months.size,
        currency = "INR",
    )

    private fun plan(
        targetPercent: Int = 50,
        endMonth: YearMonth = YearMonth.of(2026, 6),
        actualIncome: BigDecimal = BigDecimal(3000),
        actualSpent: BigDecimal = BigDecimal(2700),
        projectedMonthlyIncome: BigDecimal = BigDecimal(1000),
        projectedMonthlySpend: BigDecimal = BigDecimal(500),
        requiredTotalSavings: BigDecimal = BigDecimal(3500),
        remainingMonths: Int = 4,
    ) = SavingsGoalPlan(
        targetPercent = targetPercent,
        periodStart = LocalDate.of(2026, 1, 1),
        endMonth = endMonth,
        remainingMonths = remainingMonths,
        actualIncome = actualIncome,
        actualSpent = actualSpent,
        actualSaved = actualIncome - actualSpent,
        currentPercent = null,
        projectedMonthlyIncome = projectedMonthlyIncome,
        projectedMonthlySpend = projectedMonthlySpend,
        incomeBasis = SavingsIncomeBasis.SALARY,
        projectedTotalIncome = BigDecimal(7000),
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
    fun forecast_marksActualThenProjectedThroughDeadline() {
        val summary = summary(
            month(2026, 1, 1000, 900),
            month(2026, 2, 1000, 900),
            month(2026, 3, 1000, 900),
        )

        val forecast = computeSavingsGoalForecast(plan(), summary, today = today)

        assertEquals(6, forecast.points.size)
        assertEquals(YearMonth.of(2026, 1), forecast.points.first().month)
        assertEquals(YearMonth.of(2026, 6), forecast.points.last().month)
        assertFalse(forecast.points[0].isProjected)
        assertFalse(forecast.points[2].isProjected)
        assertTrue(forecast.points[3].isProjected)
        assertTrue(forecast.points.last().isProjected)
        assertEquals(YearMonth.of(2026, 3), forecast.todayMonth)
        assertTrue(forecast.hasData)
        assertEquals(YearMonth.of(2026, 6), forecast.projectedEndMonth)
    }

    @Test
    fun forecast_usesCumulativeRateOfIncome() {
        val summary = summary(
            month(2026, 1, 1000, 900),
            month(2026, 2, 1000, 900),
            month(2026, 3, 1000, 900),
        )

        val forecast = computeSavingsGoalForecast(plan(), summary, today = today)

        // Actual months: 100/1000, 200/2000, 300/3000.
        assertEquals(10f, forecast.points[0].savedPercent!!, 0.01f)
        assertEquals(10f, forecast.points[2].savedPercent!!, 0.01f)
        // Projected months add 500 saved on 1000 income each.
        assertEquals(20f, forecast.points[3].savedPercent!!, 0.01f)
        assertEquals(30f, forecast.points[5].savedPercent!!, 0.01f)
    }

    @Test
    fun milestones_usePlanRequiredAmountAndFirstCrossingMonth() {
        val summary = summary(
            month(2026, 1, 1000, 900),
            month(2026, 2, 1000, 900),
            month(2026, 3, 1000, 900),
        )

        val forecast = computeSavingsGoalForecast(
            plan = plan(
                projectedMonthlyIncome = BigDecimal(2000),
                requiredTotalSavings = BigDecimal(7000),
            ),
            summary = summary,
            today = today,
        )

        assertEquals(listOf(25, 50, 75, 100), forecast.milestones.map { it.percent })
        assertEquals(0, forecast.milestones[0].amount.compareTo(BigDecimal("1750.00")))
        assertEquals(0, forecast.milestones[3].amount.compareTo(BigDecimal("7000.00")))

        // Cumulative saved reaches 1,800 in April and 4,600 in June; the rest is never reached.
        assertEquals(YearMonth.of(2026, 4), forecast.milestones[0].month)
        assertEquals(YearMonth.of(2026, 6), forecast.milestones[1].month)
        assertNull(forecast.milestones[2].month)
        assertNull(forecast.milestones[3].month)
    }

    @Test
    fun noSummaryMonths_fallsBackToCurrentMonthActuals() {
        val forecast = computeSavingsGoalForecast(
            plan = plan(
                endMonth = YearMonth.of(2026, 3),
                actualIncome = BigDecimal(1000),
                actualSpent = BigDecimal(400),
                requiredTotalSavings = BigDecimal(500),
                remainingMonths = 0,
            ),
            summary = SavingsRateSummary(),
            today = today,
        )

        assertEquals(1, forecast.points.size)
        assertEquals(YearMonth.of(2026, 3), forecast.points.first().month)
        assertFalse(forecast.points.first().isProjected)
        assertEquals(60f, forecast.points.first().savedPercent!!, 0.01f)
    }

    @Test
    fun noIncome_producesNullRateButStillShowsMonth() {
        val forecast = computeSavingsGoalForecast(
            plan = plan(
                endMonth = YearMonth.of(2026, 3),
                actualIncome = BigDecimal.ZERO,
                actualSpent = BigDecimal.ZERO,
                requiredTotalSavings = BigDecimal.ZERO,
                remainingMonths = 0,
            ),
            summary = SavingsRateSummary(),
            today = today,
        )

        assertEquals(1, forecast.points.size)
        assertNull(forecast.points.first().savedPercent)
        assertTrue(forecast.milestones.all { it.amount.signum() == 0 })
    }

    @Test
    fun milestoneMonthIsNullWhenRateNeverCrossesThreshold() {
        val summary = summary(
            month(2026, 1, 1000, 900),
            month(2026, 2, 1000, 900),
            month(2026, 3, 1000, 900),
        )

        val forecast = computeSavingsGoalForecast(
            plan = plan(
                targetPercent = 100,
                projectedMonthlyIncome = BigDecimal(1000),
                projectedMonthlySpend = BigDecimal(900),
            ),
            summary = summary,
            today = today,
        )

        assertTrue(forecast.milestones.all { it.month == null })
    }

    @Test
    fun fixedGoalForecast_reachesTargetInDeadlineMonth() {
        val fixedPlan = SavingsGoalPlan(
            kind = SavingsGoalKind.FIXED,
            targetPercent = 70,
            targetAmount = BigDecimal(150_000),
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 6),
            remainingMonths = 4,
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            actualSaved = BigDecimal(25_000),
            currentPercent = null,
            projectedMonthlyIncome = BigDecimal.ZERO,
            projectedMonthlySpend = BigDecimal.ZERO,
            incomeBasis = SavingsIncomeBasis.NONE,
            projectedTotalIncome = BigDecimal.ZERO,
            requiredTotalSavings = BigDecimal(150_000),
            remainingToSave = BigDecimal(125_000),
            monthlySavingsNeeded = BigDecimal("31250.00"),
            spendablePerMonth = BigDecimal.ZERO,
            shortfallPerMonth = BigDecimal.ZERO,
            cutPerMonth = BigDecimal.ZERO,
            onTrack = true,
            currency = "INR",
            goalProgressPercent = null,
        )

        val forecast = computeSavingsGoalForecast(
            plan = fixedPlan,
            summary = SavingsRateSummary(),
            today = today,
        )

        assertEquals(4, forecast.points.size)
        assertEquals(YearMonth.of(2026, 3), forecast.points.first().month)
        assertEquals(YearMonth.of(2026, 6), forecast.points.last().month)
        assertEquals(100f, forecast.points.last().savedPercent!!, 0.01f)
        assertEquals(YearMonth.of(2026, 6), forecast.milestones.last().month)
    }
}
