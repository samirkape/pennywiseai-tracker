package com.spendly.tracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendly.tracker.R
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.DateRangeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Day-of-month picker for pay-period configuration.
 *
 * Exposes days 1–28 in a 4×7 grid plus a special "Last day of month" pill
 * (persisted as [DateRangeUtils.LAST_DAY_SENTINEL]) and an "exact date" escape
 * hatch via Material's [DatePickerDialog]. The exact-date path is only enabled
 * when [pickExactDateMonth] is provided — used for per-month overrides where
 * the user knows the actual salary date in that specific month.
 *
 * @param selectedDay current value (may be [DateRangeUtils.LAST_DAY_SENTINEL]).
 * @param onDaySelected called with the chosen day (or [DateRangeUtils.LAST_DAY_SENTINEL]).
 * @param pickExactDateMonth optional YearMonth to constrain the exact-date dialog.
 * @param title dialog title, shown at the top.
 * @param description optional helper text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayPeriodDayPickerDialog(
    title: String,
    description: String? = null,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    pickExactDateMonth: YearMonth? = null,
) {
    var showExactDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                (1..28).chunked(7).forEach { rowDays ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowDays.forEach { day ->
                            DayPill(
                                day = day,
                                selected = day == selectedDay,
                                onClick = { onDaySelected(day) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                LastDayPill(
                    selected = selectedDay == DateRangeUtils.LAST_DAY_SENTINEL,
                    onClick = { onDaySelected(DateRangeUtils.LAST_DAY_SENTINEL) }
                )

                if (pickExactDateMonth != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(onClick = { showExactDatePicker = true }) {
                        Text(stringResource(R.string.pay_period_pick_exact_date))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pay_period_cancel))
            }
        }
    )

    if (showExactDatePicker && pickExactDateMonth != null) {
        ExactDatePickerInMonthDialog(
            yearMonth = pickExactDateMonth,
            initialDay = if (selectedDay in 1..pickExactDateMonth.lengthOfMonth()) {
                selectedDay
            } else {
                pickExactDateMonth.lengthOfMonth().coerceAtMost(15)
            },
            onDateSelected = { date ->
                showExactDatePicker = false
                onDaySelected(date.dayOfMonth)
            },
            onDismiss = { showExactDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactDatePickerInMonthDialog(
    yearMonth: YearMonth,
    initialDay: Int,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialDate = yearMonth.atDay(initialDay.coerceIn(1, yearMonth.lengthOfMonth()))
    val initialMillis = initialDate
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val monthStartMillis = yearMonth.atDay(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val monthEndMillis = yearMonth.atEndOfMonth()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis in monthStartMillis..monthEndMillis
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    onDateSelected(date)
                } ?: onDismiss()
            }) {
                Text(stringResource(R.string.pay_period_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pay_period_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun DayPill(
    day: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LastDayPill(
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = stringResource(R.string.pay_period_last_day_of_month),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
    }
}

/** Returns the ordinal suffix for a day number (1 → "st", 2 → "nd", etc.). */
fun ordinalSuffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/**
 * Formats a salary-day value for user-facing display, e.g.
 *   • `1` → "1st"
 *   • `25` → "25th"
 *   • [DateRangeUtils.LAST_DAY_SENTINEL] → "Last day of month"
 */
@Composable
fun formatSalaryDayLabel(day: Int): String {
    return if (day == DateRangeUtils.LAST_DAY_SENTINEL) {
        stringResource(R.string.pay_period_last_day_of_month)
    } else {
        "$day${ordinalSuffix(day)}"
    }
}
