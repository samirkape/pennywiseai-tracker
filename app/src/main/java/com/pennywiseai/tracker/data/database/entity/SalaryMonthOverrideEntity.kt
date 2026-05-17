package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a per-month override for the salary/financial month start day.
 *
 * [yearMonth] is the calendar month in "YYYY-MM" format (e.g. "2026-05") that the salary
 * actually arrived in. [startDay] is the actual day-of-month the salary arrived, which may
 * differ from the global default when the usual date falls on a weekend or holiday.
 */
@Entity(tableName = "salary_month_overrides")
data class SalaryMonthOverrideEntity(
    @PrimaryKey
    @ColumnInfo(name = "year_month")
    val yearMonth: String,

    @ColumnInfo(name = "start_day")
    val startDay: Int
)
