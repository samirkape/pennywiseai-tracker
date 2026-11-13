package com.spendly.tracker.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class SavingsReminderScheduleTest {

    private val mondayMorning = LocalDateTime.of(2026, 11, 2, 8, 0)

    @Test
    fun `fires later the same day when the hour is still ahead`() {
        val next = nextSavingsReminderRun(
            now = mondayMorning,
            schedule = SavingsReminderSchedule(setOf(DayOfWeek.MONDAY), 9),
        )

        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), next)
    }

    @Test
    fun `moves to next week when the hour already passed`() {
        val next = nextSavingsReminderRun(
            now = LocalDateTime.of(2026, 11, 2, 10, 0),
            schedule = SavingsReminderSchedule(setOf(DayOfWeek.MONDAY), 9),
        )

        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), next)
    }

    @Test
    fun `uses the next chosen weekday`() {
        val next = nextSavingsReminderRun(
            now = mondayMorning,
            schedule = SavingsReminderSchedule(setOf(DayOfWeek.FRIDAY), 18),
        )

        assertEquals(LocalDateTime.of(2026, 11, 6, 18, 0), next)
    }

    @Test
    fun `multiple days pick the earliest upcoming occurrence`() {
        val next = nextSavingsReminderRun(
            now = mondayMorning,
            schedule = SavingsReminderSchedule(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                9,
            ),
        )

        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), next)
    }

    @Test
    fun `multiple days skip today once the hour passed`() {
        val next = nextSavingsReminderRun(
            now = LocalDateTime.of(2026, 11, 2, 10, 0),
            schedule = SavingsReminderSchedule(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                9,
            ),
        )

        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), next)
    }

    @Test
    fun `multiple days wrap to next week when every occurrence passed`() {
        val next = nextSavingsReminderRun(
            now = LocalDateTime.of(2026, 11, 6, 10, 0),
            schedule = SavingsReminderSchedule(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                9,
            ),
        )

        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), next)
    }

    @Test
    fun `builds a schedule from persisted values with normalization`() {
        val schedule = SavingsReminderSchedule.from(isoDays = setOf(1, 9, 3), hour = 42)

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), schedule.daysOfWeek)
        assertEquals(23, schedule.normalizedHour)
    }

    @Test
    fun `empty or invalid days fall back to Monday`() {
        val empty = SavingsReminderSchedule.from(isoDays = emptySet(), hour = 9)
        val invalid = SavingsReminderSchedule.from(isoDays = setOf(0, 9), hour = 9)

        assertEquals(setOf(DayOfWeek.MONDAY), empty.normalizedDays)
        assertEquals(setOf(DayOfWeek.MONDAY), invalid.normalizedDays)
    }
}
