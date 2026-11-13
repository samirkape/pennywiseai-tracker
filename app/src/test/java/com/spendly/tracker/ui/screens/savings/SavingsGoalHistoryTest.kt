package com.spendly.tracker.ui.screens.savings

import com.spendly.tracker.ui.screens.analytics.MonthlySavingsRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class SavingsGoalHistoryTest {

    private fun bucket(month: YearMonth, income: String, spent: String) =
        MonthlySavingsRate(
            month = month,
            start = month.atDay(1),
            end = month.atEndOfMonth(),
            income = BigDecimal(income),
            spent = BigDecimal(spent),
            isCurrent = false,
        )

    @Test
    fun `history is newest first and limited`() {
        val history = buildSavingsHistory(
            months = listOf(
                bucket(YearMonth.of(2026, 9), "1000", "400"),
                bucket(YearMonth.of(2026, 11), "1000", "600"),
                bucket(YearMonth.of(2026, 10), "1000", "500"),
            ),
            targetPercent = 70,
            capturedAtMillis = 0L,
            limit = 2,
        )

        assertEquals(2, history.size)
        assertEquals(YearMonth.of(2026, 11), history[0].month)
        assertEquals(YearMonth.of(2026, 10), history[1].month)
        assertEquals(BigDecimal("500"), history[1].saved)
        assertEquals(70, history[1].targetPercent)
    }

    @Test
    fun `rate is null when the month has no income`() {
        val history = buildSavingsHistory(
            months = listOf(bucket(YearMonth.of(2026, 11), "0", "500")),
            targetPercent = 70,
            capturedAtMillis = 0L,
        )

        assertNull(history.single().ratePercent)
        assertEquals(BigDecimal("-500"), history.single().saved)
    }

    @Test
    fun `overspending keeps a negative rate`() {
        val history = buildSavingsHistory(
            months = listOf(bucket(YearMonth.of(2026, 11), "1000", "1200")),
            targetPercent = 70,
            capturedAtMillis = 0L,
        )

        val rate = history.single().ratePercent
        assertTrue(rate != null && rate < -19f && rate > -21f)
    }

    @Test
    fun `target percent is clamped for stored snapshots`() {
        val history = buildSavingsHistory(
            months = listOf(bucket(YearMonth.of(2026, 11), "1000", "500")),
            targetPercent = 140,
            capturedAtMillis = 0L,
        )

        assertEquals(100, history.single().targetPercent)
    }
}
