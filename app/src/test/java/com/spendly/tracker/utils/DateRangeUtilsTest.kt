package com.spendly.tracker.utils

import com.spendly.tracker.utils.DateRangeUtils.SalaryDayAdjustment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateRangeUtilsTest {

    // Oct 25, 2026 is a Sunday; salary prepone day = Friday Oct 23 (real-world case).
    private val oct2026 = YearMonth.of(2026, 10)
    private val oct23 = LocalDate.of(2026, 10, 23)

    // ── expectedPayday ─────────────────────────────────────────────────────────

    @Test
    fun expectedPayday_exactKeepsNominalDay() {
        assertEquals(LocalDate.of(2026, 10, 25), expectedPayday(oct2026, 25, SalaryDayAdjustment.EXACT))
    }

    @Test
    fun expectedPayday_precedingShiftsSundayToFriday() {
        assertEquals(oct23, expectedPayday(oct2026, 25, SalaryDayAdjustment.PRECEDING_WEEKDAY))
    }

    @Test
    fun expectedPayday_precedingShiftsSaturdayToFriday() {
        // Dec 25, 2027 is a Saturday → preceding weekday is Dec 24 (Friday)
        assertEquals(LocalDate.of(2027, 12, 24), expectedPayday(YearMonth.of(2027, 12), 25, SalaryDayAdjustment.PRECEDING_WEEKDAY))
    }

    @Test
    fun expectedPayday_followingShiftsSundayToMonday() {
        assertEquals(LocalDate.of(2026, 10, 26), expectedPayday(oct2026, 25, SalaryDayAdjustment.FOLLOWING_WEEKDAY))
    }

    @Test
    fun expectedPayday_weekdayDayIsUnchanged() {
        // Nov 25, 2026 is a Wednesday — no shift under any convention
        val nov = YearMonth.of(2026, 11)
        SalaryDayAdjustment.entries.forEach { adjustment ->
            assertEquals(LocalDate.of(2026, 11, 25), expectedPayday(nov, 25, adjustment))
        }
    }

    @Test
    fun expectedPayday_clampsDayBeyondMonthLength() {
        // Day 31 in November resolves to Nov 30 (Monday) and stays exact
        assertEquals(LocalDate.of(2026, 11, 30), expectedPayday(YearMonth.of(2026, 11), 31, SalaryDayAdjustment.EXACT))
    }

    @Test
    fun expectedPayday_lastDaySentinelAdjustsWeekends() {
        // Feb 28, 2026 is a Saturday → preceding weekday is Feb 27
        assertEquals(LocalDate.of(2026, 2, 27), expectedPayday(YearMonth.of(2026, 2), DateRangeUtils.LAST_DAY_SENTINEL, SalaryDayAdjustment.PRECEDING_WEEKDAY))
    }

    // ── calculateFinancialMonthRange (no overrides) ────────────────────────────

    @Test
    fun financialRange_anchorsOnActualAdjustedPayday() {
        // On Oct 23 (rule payday) the new period becomes current immediately
        val (start, end) = DateRangeUtils.calculateFinancialMonthRange(
            oct23, 25, SalaryDayAdjustment.PRECEDING_WEEKDAY,
        )
        assertEquals(oct23, start)
        assertEquals(LocalDate.of(2026, 11, 24), end) // Nov 25 is a Wednesday, no adjust
    }

    @Test
    fun financialRange_newPeriodCoversDayAfterEarlyPayday() {
        val (start, end) = DateRangeUtils.calculateFinancialMonthRange(
            LocalDate.of(2026, 10, 24), 25, SalaryDayAdjustment.PRECEDING_WEEKDAY,
        )
        assertEquals(oct23, start)
        assertEquals(LocalDate.of(2026, 11, 24), end)
    }

    @Test
    fun financialRange_exactConventionKeepsCalendarAnchoring() {
        val today = LocalDate.of(2026, 10, 23)
        val (start, end) = DateRangeUtils.calculateFinancialMonthRange(today, 25)
        assertEquals(LocalDate.of(2026, 9, 25), start)
        assertEquals(LocalDate.of(2026, 10, 24), end)
    }

    // ── calculateFinancialMonthRangeWithOverrides (anchor fix regression) ──────

    @Test
    fun overrides_currentPeriodContainsTodayImmediatelyAfterAccepting() {
        // Regression: previously the anchor month was chosen from the *default* day (25),
        // so on Oct 23 the accepted override (Oct 23) produced a window Sep 25 – Oct 22
        // that excluded today. It must produce Oct 23 – Nov 24 immediately.
        val (start, end) = DateRangeUtils.calculateFinancialMonthRangeWithOverrides(
            oct23, 25, mapOf("2026-10" to 23),
        )
        assertEquals(oct23, start)
        assertEquals(LocalDate.of(2026, 11, 24), end)
    }

    @Test
    fun overrides_takePrecedenceOverWeekendRule() {
        val (start, _) = DateRangeUtils.calculateFinancialMonthRangeWithOverrides(
            oct23, 25,
            mapOf("2026-10" to 20),
            SalaryDayAdjustment.PRECEDING_WEEKDAY,
        )
        assertEquals(LocalDate.of(2026, 10, 20), start)
    }

    @Test
    fun overrides_previousPeriodEndFollowsNextMonthStart() {
        // Before accepting any override, Sep's period ends the day before Oct's rule payday
        val previousPeriod = DateRangeUtils.financialMonthRangeFor(
            YearMonth.of(2026, 9), 25, emptyMap(), SalaryDayAdjustment.PRECEDING_WEEKDAY,
        )
        assertEquals(LocalDate.of(2026, 9, 25), previousPeriod.first)
        assertEquals(LocalDate.of(2026, 10, 22), previousPeriod.second)
    }

    @Test
    fun overrides_anchorRollsBackWhenOverridePushesPaydayLater() {
        // Salary delayed to Oct 27; on Oct 26 the current period is still the Sep-anchored one
        val (start, end) = DateRangeUtils.calculateFinancialMonthRangeWithOverrides(
            LocalDate.of(2026, 10, 26), 25, mapOf("2026-10" to 27),
        )
        assertEquals(LocalDate.of(2026, 9, 25), start)
        assertEquals(LocalDate.of(2026, 10, 26), end)
    }

    @Test
    fun lastDaySentinel_resolvesEndOfMonthEvenInShortMonths() {
        val (start, _) = DateRangeUtils.calculateFinancialMonthRange(
            LocalDate.of(2026, 2, 20), DateRangeUtils.LAST_DAY_SENTINEL,
        )
        assertEquals(LocalDate.of(2026, 1, 31), start)
    }

    private fun expectedPayday(
        month: YearMonth,
        day: Int,
        adjustment: SalaryDayAdjustment,
    ) = DateRangeUtils.expectedPayday(month, day, adjustment)
}
