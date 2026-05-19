package com.pennywiseai.tracker.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * How far back to scan when applying a keyword rule to existing transactions.
 */
sealed class QuickKeywordApplyScope {

    abstract val logLabel: String

    data object AllTime : QuickKeywordApplyScope() {
        override val logLabel: String = "all time"
    }

    data class LastDays(val days: Int) : QuickKeywordApplyScope() {
        override val logLabel: String = "last $days days"
    }

    data class CustomRange(
        val startDate: LocalDate,
        val endDate: LocalDate,
    ) : QuickKeywordApplyScope() {
        override val logLabel: String = "$startDate..$endDate"
    }

    /**
     * @return inclusive start/end datetimes, or null for all stored transactions.
     */
    fun resolveDateTimeRange(now: LocalDate = LocalDate.now()): Pair<LocalDateTime, LocalDateTime>? =
        when (this) {
            AllTime -> null
            is LastDays -> {
                val start = now.minusDays(days.toLong()).atStartOfDay()
                val end = now.atTime(23, 59, 59)
                start to end
            }
            is CustomRange -> {
                val start = startDate.atStartOfDay()
                val end = endDate.atTime(23, 59, 59)
                start to end
            }
        }

    companion object {
        const val DAYS_30 = 30
        const val DAYS_90 = 90
        const val DAYS_365 = 365
    }
}
