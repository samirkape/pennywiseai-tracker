package com.spendly.tracker.ui.screens.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsProjectionTest {

    private val today = LocalDate.of(2026, 3, 11)

    private fun month(
        year: Int,
        month: Int,
        income: Int,
        spent: Int,
        startDay: Int = 1,
        endDay: Int = YearMonth.of(year, month).lengthOfMonth(),
    ) = MonthlySavingsRate(
        month = YearMonth.of(year, month),
        start = LocalDate.of(year, month, startDay),
        end = LocalDate.of(year, month, endDay),
        income = BigDecimal(income),
        spent = BigDecimal(spent),
        isCurrent = false,
    )

    private fun summary(vararg months: MonthlySavingsRate) = SavingsRateSummary(
        months = months.toList(),
        windowMonths = months.size,
    )

    @Test
    fun currentMonth_pacesActualSpendWithHistoricalDailyRate() {
        val projection = computeSavingsProjection(
            summary = summary(
                month(2026, 1, income = 1000, spent = 500),
                month(2026, 2, income = 1000, spent = 300),
                month(2026, 3, income = 1000, spent = 200),
            ),
            horizonMonths = 3,
            today = today,
        )!!

        val current = projection.months.first()
        assertEquals(YearMonth.of(2026, 3), current.month)
        assertTrue(current.isCurrent)
        // 400 baseline spend / 31 days * 20 remaining = 258.06, plus 200 already spent.
        assertEquals(458.06, current.spent.toDouble(), 0.01)
        assertEquals(1000, current.income.toInt())
        assertTrue(projection.usesCurrentMonthPace)
    }

    @Test
    fun futureMonths_useCompletedMonthAverages() {
        val projection = computeSavingsProjection(
            summary = summary(
                month(2026, 1, income = 1000, spent = 500),
                month(2026, 2, income = 1000, spent = 300),
                month(2026, 3, income = 1000, spent = 200),
            ),
            horizonMonths = 3,
            today = today,
        )!!

        assertEquals(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5)), projection.months.map { it.month })
        projection.months.drop(1).forEach {
            assertEquals(1000, it.income.toInt())
            assertEquals(400, it.spent.toInt())
        }
    }

    @Test
    fun currentMonth_isExcludedFromBaseline() {
        val projection = computeSavingsProjection(
            summary = summary(
                month(2026, 1, income = 1000, spent = 500),
                month(2026, 2, income = 1000, spent = 300),
                // A heavy current month must not drag the future-month baseline up.
                month(2026, 3, income = 1000, spent = 5000),
            ),
            horizonMonths = 3,
            today = today,
        )!!

        assertEquals(400, projection.months[1].spent.toInt())
        assertEquals(400, projection.months[2].spent.toInt())
        assertEquals(2, projection.basisMonths)
    }

    @Test
    fun aggregate_isTotalSavedOverTotalIncome() {
        val projection = computeSavingsProjection(
            summary = summary(
                month(2026, 1, income = 1000, spent = 500),
                month(2026, 2, income = 1000, spent = 300),
                month(2026, 3, income = 1000, spent = 200),
            ),
            horizonMonths = 3,
            today = today,
        )!!

        assertEquals(3000, projection.projectedIncome.toInt())
        assertEquals(1258.06, projection.projectedSpent.toDouble(), 0.01)
        assertEquals(1741.94, projection.projectedSaved.toDouble(), 0.01)
        assertEquals(58.06f, projection.projectedPercent!!, 0.02f)
    }

    @Test
    fun overspending_projectsNegativeRate() {
        val projection = computeSavingsProjection(
            summary = summary(
                month(2026, 1, income = 1000, spent = 1500),
                month(2026, 2, income = 1000, spent = 1500),
                month(2026, 3, income = 1000, spent = 3000),
            ),
            horizonMonths = 3,
            today = today,
        )!!

        assertTrue(projection.projectedPercent!! < 0f)
        assertTrue(projection.projectedSaved < BigDecimal.ZERO)
    }

    @Test
    fun noHistoryAndTooFewDays_returnsNull() {
        val projection = computeSavingsProjection(
            summary = summary(month(2026, 3, income = 1000, spent = 100)),
            horizonMonths = 3,
            today = LocalDate.of(2026, 3, 2),
        )

        assertNull(projection)
    }

    @Test
    fun currentMonthAlone_projectsOnceItHasSignal() {
        val projection = computeSavingsProjection(
            summary = summary(month(2026, 3, income = 1000, spent = 100)),
            horizonMonths = 3,
            today = LocalDate.of(2026, 3, 4),
        )!!

        assertEquals(3, projection.months.size)
        assertEquals(0, projection.basisMonths)
        assertTrue(projection.usesCurrentMonthPace)
        // 100 / 4 elapsed days * 31 days = 775 projected spend.
        assertEquals(775, projection.months.first().spent.toInt())
    }

    @Test
    fun noIncome_returnsNull() {
        val projection = computeSavingsProjection(
            summary = summary(month(2026, 3, income = 0, spent = 100)),
            horizonMonths = 3,
            today = today,
        )

        assertNull(projection)
    }

    @Test
    fun historicalWindow_projectsFromBaselineOnly() {
        val projection = computeSavingsProjection(
            summary = summary(
                month(2026, 1, income = 1000, spent = 500),
                month(2026, 2, income = 1000, spent = 300),
            ),
            horizonMonths = 3,
            today = today,
        )!!

        assertFalse(projection.usesCurrentMonthPace)
        assertEquals(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5)), projection.months.map { it.month })
        assertEquals(60f, projection.projectedPercent!!, 0.01f)
        assertEquals(YearMonth.of(2026, 3) to YearMonth.of(2026, 5), projection.range)
    }

    @Test
    fun horizon_isClampedAndCounted() {
        val projection = computeSavingsProjection(
            summary = summary(month(2026, 1, income = 1000, spent = 500)),
            horizonMonths = 99,
            today = today,
        )!!

        assertEquals(SAVINGS_PROJECTION_MAX_HORIZON, projection.horizonMonths)
        assertEquals(SAVINGS_PROJECTION_MAX_HORIZON, projection.months.size)
    }
}
