package com.spendly.tracker.connector.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Unit used to express an analysis window. */
enum class WindowUnit(val label: String) {
    DAYS("days"),
    MONTHS("months"),
    YEARS("years")
}

/**
 * The period of transactions the user wants analysed — "last 30 days", "last 3 months",
 * "last year", …
 */
data class AnalysisWindow(
    val unit: WindowUnit,
    val count: Int
) {
    init {
        require(count > 0) { "count must be positive" }
    }

    /** Inclusive first day covered by the window. */
    fun startDate(today: LocalDate = LocalDate.now()): LocalDate = when (unit) {
        WindowUnit.DAYS -> today.minusDays(count.toLong() - 1)
        WindowUnit.MONTHS -> today.minusMonths(count.toLong()).plusDays(1)
        WindowUnit.YEARS -> today.minusYears(count.toLong()).plusDays(1)
    }

    /** Inclusive last day covered by the window. */
    fun endDate(today: LocalDate = LocalDate.now()): LocalDate = today

    /** Number of days covered by the window (at least one). */
    fun dayCount(today: LocalDate = LocalDate.now()): Int =
        (ChronoUnit.DAYS.between(startDate(today), endDate(today)) + 1).toInt().coerceAtLeast(1)

    /** The equally sized window immediately before this one, used for comparisons. */
    fun previous(today: LocalDate = LocalDate.now()): AnalysisWindow = when (unit) {
        WindowUnit.DAYS -> AnalysisWindow(WindowUnit.DAYS, count)
        WindowUnit.MONTHS -> AnalysisWindow(WindowUnit.MONTHS, count)
        WindowUnit.YEARS -> AnalysisWindow(WindowUnit.YEARS, count)
    }

    fun label(): String = "Last $count ${unit.label}"

    fun startDateOfPrevious(today: LocalDate = LocalDate.now()): LocalDate = when (unit) {
        WindowUnit.DAYS -> startDate(today).minusDays(count.toLong())
        WindowUnit.MONTHS -> startDate(today).minusMonths(count.toLong())
        WindowUnit.YEARS -> startDate(today).minusYears(count.toLong())
    }

    fun endDateOfPrevious(today: LocalDate = LocalDate.now()): LocalDate = startDate(today).minusDays(1)

    companion object {
        val LAST_7_DAYS = AnalysisWindow(WindowUnit.DAYS, 7)
        val LAST_30_DAYS = AnalysisWindow(WindowUnit.DAYS, 30)
        val LAST_3_MONTHS = AnalysisWindow(WindowUnit.MONTHS, 3)
        val LAST_6_MONTHS = AnalysisWindow(WindowUnit.MONTHS, 6)
        val LAST_12_MONTHS = AnalysisWindow(WindowUnit.MONTHS, 12)
        val LAST_YEAR = AnalysisWindow(WindowUnit.YEARS, 1)

        /** Quick picks offered in the UI. */
        val PRESETS: List<AnalysisWindow> = listOf(
            LAST_7_DAYS,
            LAST_30_DAYS,
            LAST_3_MONTHS,
            LAST_6_MONTHS,
            LAST_12_MONTHS
        )

        const val MAX_DAYS = 366
        const val MAX_MONTHS = 36
        const val MAX_YEARS = 10

        fun coerce(unit: WindowUnit, count: Int): AnalysisWindow {
            val safeCount = count.coerceAtLeast(1)
            val max = when (unit) {
                WindowUnit.DAYS -> MAX_DAYS
                WindowUnit.MONTHS -> MAX_MONTHS
                WindowUnit.YEARS -> MAX_YEARS
            }
            return AnalysisWindow(unit, safeCount.coerceAtMost(max))
        }

        fun fromStored(unitId: String?, count: Int?): AnalysisWindow {
            val unit = runCatching { WindowUnit.valueOf(unitId ?: "") }.getOrDefault(WindowUnit.MONTHS)
            return coerce(unit, count ?: 3)
        }
    }
}
