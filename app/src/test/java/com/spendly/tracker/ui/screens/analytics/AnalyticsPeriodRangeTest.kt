package com.spendly.tracker.ui.screens.analytics

import com.spendly.tracker.presentation.common.TimePeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Period-over-period comparison windows used by the Analytics hero cards.
 */
class AnalyticsPeriodRangeTest {

    private fun previousPeriod(
        range: Pair<LocalDate, LocalDate>,
        period: TimePeriod = TimePeriod.THIS_MONTH,
        isMonthNavigation: Boolean = false,
        monthStartDay: Int = 25,
        useFinancialMonth: Boolean = true,
    ): Pair<LocalDate, LocalDate> = previousPeriodRange(
        dateRange = range,
        period = period,
        isMonthNavigation = isMonthNavigation,
        monthStartDay = monthStartDay,
        useFinancialMonth = useFinancialMonth,
        monthStartOverrides = emptyMap(),
        useFixedBudgetPeriodEnd = false,
        budgetPeriodEndDay = 31,
    )

    @Test
    fun `pay month uses the real boundaries of the previous pay month`() {
        // Pay month Sep 25 - Oct 24 (30 days) is preceded by Aug 25 - Sep 24 (31 days).
        val current = LocalDate.of(2026, 9, 25) to LocalDate.of(2026, 10, 24)

        val previous = previousPeriod(current, monthStartDay = 25)

        assertEquals(LocalDate.of(2026, 8, 25), previous.first)
        assertEquals(LocalDate.of(2026, 9, 24), previous.second)
    }

    @Test
    fun `calendar month uses the previous calendar month`() {
        val current = LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 21)

        val previous = previousPeriod(current, useFinancialMonth = false)

        assertEquals(LocalDate.of(2026, 9, 1), previous.first)
        assertEquals(LocalDate.of(2026, 9, 30), previous.second)
    }

    @Test
    fun `last month uses the month before it`() {
        val current = LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30)

        val previous = previousPeriod(current, period = TimePeriod.LAST_MONTH, useFinancialMonth = false)

        assertEquals(LocalDate.of(2026, 8, 1), previous.first)
        assertEquals(LocalDate.of(2026, 8, 31), previous.second)
    }

    @Test
    fun `navigated month keeps month boundaries`() {
        val current = LocalDate.of(2026, 8, 25) to LocalDate.of(2026, 9, 24)

        val previous = previousPeriod(current, period = TimePeriod.CUSTOM, isMonthNavigation = true)

        assertEquals(LocalDate.of(2026, 7, 25), previous.first)
        assertEquals(LocalDate.of(2026, 8, 24), previous.second)
    }

    @Test
    fun `rolling ranges fall back to an equal length window`() {
        val current = LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 10, 21)

        val previous = previousPeriod(current, period = TimePeriod.LAST_3_MONTHS, useFinancialMonth = false)

        assertEquals(LocalDate.of(2026, 5, 11), previous.first)
        assertEquals(LocalDate.of(2026, 7, 31), previous.second)
    }

    @Test
    fun `elapsed slice covers the days elapsed in the running period`() {
        val previous = LocalDate.of(2026, 8, 25) to LocalDate.of(2026, 9, 24)
        val current = LocalDate.of(2026, 9, 25) to LocalDate.of(2026, 10, 24)

        val aligned = elapsedAlignedRange(previous, current, today = LocalDate.of(2026, 10, 21))

        // 27 of 30 days elapsed -> first 27 days of the previous period.
        assertEquals(LocalDate.of(2026, 8, 25), aligned.first)
        assertEquals(LocalDate.of(2026, 9, 20), aligned.second)
    }

    @Test
    fun `elapsed slice keeps the whole previous period once the current one ended`() {
        val previous = LocalDate.of(2026, 8, 25) to LocalDate.of(2026, 9, 24)
        val current = LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30)

        val aligned = elapsedAlignedRange(previous, current, today = LocalDate.of(2026, 10, 21))

        assertEquals(previous, aligned)
    }
}
