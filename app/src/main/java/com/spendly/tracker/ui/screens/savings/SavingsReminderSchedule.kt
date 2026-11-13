package com.spendly.tracker.ui.screens.savings

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Default day for the savings-goal reminder (ISO day-of-week, Monday). */
const val DEFAULT_SAVINGS_REMINDER_DAY = 1

/** Default days for the savings-goal reminder, used when nothing is persisted yet. */
val DEFAULT_SAVINGS_REMINDER_DAYS: Set<Int> = setOf(DEFAULT_SAVINGS_REMINDER_DAY)

/** Default hour for the savings-goal reminder. */
const val DEFAULT_SAVINGS_REMINDER_HOUR = 9

/** When the user wants the savings-goal nudge to arrive. */
data class SavingsReminderSchedule(
    val daysOfWeek: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    val hour: Int = DEFAULT_SAVINGS_REMINDER_HOUR,
) {
    val normalizedHour: Int get() = hour.coerceIn(0, 23)

    /** Selected days in ISO order; never empty so a schedule always has a next run. */
    val normalizedDays: Set<DayOfWeek>
        get() = daysOfWeek.ifEmpty { setOf(DayOfWeek.MONDAY) }

    companion object {
        fun from(isoDays: Set<Int>, hour: Int): SavingsReminderSchedule =
            SavingsReminderSchedule(
                daysOfWeek = isoDays
                    .mapNotNull { day -> runCatching { DayOfWeek.of(day) }.getOrNull() }
                    .toSet(),
                hour = hour.coerceIn(0, 23),
            )

        fun from(isoDayOfWeek: Int, hour: Int): SavingsReminderSchedule =
            from(setOf(isoDayOfWeek), hour)
    }
}

/**
 * Next time the reminder should fire at or after [now]: the earliest selected day,
 * moving a day to next week when its chosen hour already passed today.
 */
fun nextSavingsReminderRun(
    now: LocalDateTime,
    schedule: SavingsReminderSchedule,
): LocalDateTime {
    val hour = schedule.normalizedHour
    return schedule.normalizedDays.map { day ->
        var next = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(day))
            .atTime(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        next
    }.minOrNull() ?: now
}
