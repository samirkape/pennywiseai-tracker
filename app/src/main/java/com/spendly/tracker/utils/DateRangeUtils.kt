package com.spendly.tracker.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Utility functions for date range formatting and financial month calculation
 */
object DateRangeUtils {

    /**
     * Sentinel value meaning "use the last day of the month". Used in [monthStartDay]
     * preference and in [SalaryMonthOverrideEntity.startDay] to express end-of-month
     * salary days without ambiguous Feb 30 clamping.
     */
    const val LAST_DAY_SENTINEL: Int = 0

    /**
     * Resolves [startDay] (which may be the [LAST_DAY_SENTINEL] or a number > the
     * month's length) into a valid day-of-month for [yearMonth].
     */
    fun resolveStartDay(yearMonth: YearMonth, startDay: Int): Int {
        val lengthOfMonth = yearMonth.lengthOfMonth()
        return when {
            startDay == LAST_DAY_SENTINEL -> lengthOfMonth
            startDay < 1 -> 1
            startDay > lengthOfMonth -> lengthOfMonth
            else -> startDay
        }
    }

    /**
     * Returns the start and end of the current financial month given [today] and [startDay].
     * If today is on or after [startDay], the financial month started this calendar month.
     * Otherwise it started in the previous calendar month.
     *
     * Accepts [LAST_DAY_SENTINEL] for end-of-month salary days.
     */
    fun calculateFinancialMonthRange(today: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        val thisMonth = YearMonth.from(today)
        val effectiveThisMonthDay = resolveStartDay(thisMonth, startDay)
        return if (today.dayOfMonth >= effectiveThisMonthDay) {
            val start = thisMonth.atDay(effectiveThisMonthDay)
            val nextMonth = thisMonth.plusMonths(1)
            val nextDay = resolveStartDay(nextMonth, startDay)
            val end = nextMonth.atDay(nextDay).minusDays(1)
            start to end
        } else {
            val prevMonth = thisMonth.minusMonths(1)
            val prevDay = resolveStartDay(prevMonth, startDay)
            val start = prevMonth.atDay(prevDay)
            val end = thisMonth.atDay(effectiveThisMonthDay).minusDays(1)
            start to end
        }
    }

    /**
     * Override-aware version of [calculateFinancialMonthRange].
     *
     * Uses [overrides] (a map of "YYYY-MM" → startDay) to let individual months have a
     * different salary arrival date than the global [defaultStartDay] — useful when the
     * usual date falls on a weekend or holiday.
     *
     * Algorithm:
     * 1. Use [defaultStartDay] to determine which calendar month the current financial
     *    period nominally started in.
     * 2. Look up that month in [overrides]; if an override exists, use its startDay instead.
     * 3. Compute the end as the day before the *next* financial month's start (also honouring
     *    overrides for the next month).
     *
     * Both [defaultStartDay] and override values may be [LAST_DAY_SENTINEL].
     */
    fun calculateFinancialMonthRangeWithOverrides(
        today: LocalDate,
        defaultStartDay: Int,
        overrides: Map<String, Int>
    ): Pair<LocalDate, LocalDate> {
        val thisCalendarMonth = YearMonth.from(today)
        val defaultThisMonthDay = resolveStartDay(thisCalendarMonth, defaultStartDay)
        val startYearMonth: YearMonth = if (today.dayOfMonth >= defaultThisMonthDay) {
            thisCalendarMonth
        } else {
            thisCalendarMonth.minusMonths(1)
        }

        val effectiveStartDayRaw = overrides[startYearMonth.toString()] ?: defaultStartDay
        val safeStartDay = resolveStartDay(startYearMonth, effectiveStartDayRaw)
        val start = startYearMonth.atDay(safeStartDay)

        val nextYearMonth = startYearMonth.plusMonths(1)
        val nextEffectiveStartDayRaw = overrides[nextYearMonth.toString()] ?: defaultStartDay
        val safeNextStartDay = resolveStartDay(nextYearMonth, nextEffectiveStartDayRaw)
        val end = nextYearMonth.atDay(safeNextStartDay).minusDays(1)

        return start to end
    }

    /**
     * Returns the [LocalDate] where the financial period that *contains* [yearMonth] starts,
     * applying [overrides] and falling back to [defaultStartDay].
     *
     * For example, given monthly salary day 25 and [yearMonth] = 2026-11, returns 2026-11-25.
     */
    fun financialMonthStartFor(
        yearMonth: YearMonth,
        defaultStartDay: Int,
        overrides: Map<String, Int> = emptyMap()
    ): LocalDate {
        val raw = overrides[yearMonth.toString()] ?: defaultStartDay
        return yearMonth.atDay(resolveStartDay(yearMonth, raw))
    }

    /**
     * Returns the closed financial period [start, end] whose nominal label is [yearMonth],
     * applying [overrides] for both this and the following month.
     */
    fun financialMonthRangeFor(
        yearMonth: YearMonth,
        defaultStartDay: Int,
        overrides: Map<String, Int> = emptyMap()
    ): Pair<LocalDate, LocalDate> {
        val start = financialMonthStartFor(yearMonth, defaultStartDay, overrides)
        val next = yearMonth.plusMonths(1)
        val end = financialMonthStartFor(next, defaultStartDay, overrides).minusDays(1)
        return start to end
    }

    /**
     * Budget period for "pay month" mode.
     *
     * When [useFixedEndDay] is **false**, the period end is implicit (day before the next
     * period start), using [calculateFinancialMonthRange] or
     * [calculateFinancialMonthRangeWithOverrides].
     *
     * When [useFixedEndDay] is **true**, [fixedEndDayOfMonth] repeats every cycle (same
     * rules as [customDomPeriodStartingInMonth]): start on [startDay] in month *M*, end on
     * [fixedEndDayOfMonth] in *M* if not before the start, otherwise in *M+1*. Values may be
     * [LAST_DAY_SENTINEL] for last-day-of-month. Per-month overrides are **not** applied when
     * the fixed end is enabled.
     */
    fun calculateBudgetPeriodRange(
        today: LocalDate,
        startDay: Int,
        useFixedEndDay: Boolean,
        fixedEndDayOfMonth: Int,
        overrides: Map<String, Int> = emptyMap()
    ): Pair<LocalDate, LocalDate> {
        if (!useFixedEndDay) {
            return if (overrides.isEmpty()) {
                calculateFinancialMonthRange(today, startDay)
            } else {
                calculateFinancialMonthRangeWithOverrides(today, startDay, overrides)
            }
        }
        return findContainingCustomDomPeriod(today, startDay, fixedEndDayOfMonth)
    }

    /**
     * One cycle starting in [anchorMonth]: [startDay] … [endDay] (see [calculateBudgetPeriodRange]).
     */
    fun customDomPeriodStartingInMonth(
        anchorMonth: YearMonth,
        startDay: Int,
        endDay: Int
    ): Pair<LocalDate, LocalDate> {
        val startResolved = resolveStartDay(anchorMonth, startDay)
        val start = anchorMonth.atDay(startResolved)
        val endSameMonth = anchorMonth.atDay(resolveStartDay(anchorMonth, endDay))
        // Roll end into next month when it is on/before the start day. This treats
        // `endDom == startDom` as "monthly cycle ending on the next month's same DOM"
        // (e.g. Apr 24 -> May 24), which matches user intent for recurring pay months.
        val end = if (endSameMonth.isAfter(start)) {
            endSameMonth
        } else {
            val nextYm = anchorMonth.plusMonths(1)
            nextYm.atDay(resolveStartDay(nextYm, endDay))
        }
        return start to end
    }

    private fun findContainingCustomDomPeriod(today: LocalDate, startDay: Int, endDay: Int): Pair<LocalDate, LocalDate> {
        val ymToday = YearMonth.from(today)
        // Prefer the cycle that actually contains [today].
        for (offset in -4L..4L) {
            val ym = ymToday.plusMonths(offset)
            val (s, e) = customDomPeriodStartingInMonth(ym, startDay, endDay)
            if (!today.isBefore(s) && !today.isAfter(e)) return s to e
        }
        // No cycle contains today (e.g. end-DOM is just a few days after start-DOM and
        // today falls in the gap). Surface the nearest cycle whose start is on/before
        // today, so the configured DOMs are still reflected in the UI rather than
        // silently falling back to the implicit financial range.
        var best: Pair<LocalDate, LocalDate>? = null
        for (offset in -4L..4L) {
            val ym = ymToday.plusMonths(offset)
            val candidate = customDomPeriodStartingInMonth(ym, startDay, endDay)
            if (!candidate.first.isAfter(today)) {
                if (best == null || candidate.first.isAfter(best.first)) {
                    best = candidate
                }
            }
        }
        return best ?: customDomPeriodStartingInMonth(ymToday, startDay, endDay)
    }

    private val defaultFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val yearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /**
     * Formats a date range as a compact label string.
     * Used for displaying custom date ranges in filter chips and UI.
     *
     * When both dates are in the current year the year is omitted ("Jan 1 - Jan 31").
     * When both dates share the same past year the year is shown once at the end
     * ("Dec 25 - Jan 24, 2025").
     * When the dates span two different past years each side carries its own year
     * ("Dec 25, 2024 - Jan 24, 2025").
     *
     * @param startDate The start date of the range
     * @param endDate The end date of the range
     * @param formatter Optional custom formatter (defaults to "MMM d" pattern)
     * @return Formatted string like "Jan 1 - Jan 31" or "Dec 25 - Jan 24, 2025"
     */
    fun formatDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
        formatter: DateTimeFormatter = defaultFormatter
    ): String {
        val currentYear = LocalDate.now().year
        val startYear = startDate.year
        val endYear = endDate.year
        return when {
            // Both in current year – no year suffix (existing behaviour)
            startYear == currentYear && endYear == currentYear ->
                "${startDate.format(formatter)} - ${endDate.format(formatter)}"
            // Same past year – append year once at the end
            startYear == endYear ->
                "${startDate.format(formatter)} - ${endDate.format(yearFormatter)}"
            // Spanning two different years – show year on each side
            else ->
                "${startDate.format(yearFormatter)} - ${endDate.format(yearFormatter)}"
        }
    }

    /**
     * Formats an optional date range pair as a compact label string.
     * Returns null if the pair is null.
     *
     * @param dateRange Optional pair of start and end dates
     * @param formatter Optional custom formatter (defaults to "MMM d" pattern)
     * @return Formatted string or null if dateRange is null
     */
    fun formatDateRange(
        dateRange: Pair<LocalDate, LocalDate>?,
        formatter: DateTimeFormatter = defaultFormatter
    ): String? {
        return dateRange?.let { (start, end) ->
            formatDateRange(start, end, formatter)
        }
    }
}
