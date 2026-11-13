package com.spendly.tracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spendly.tracker.R
import com.spendly.tracker.domain.service.SalaryPayPeriodDetector
import com.spendly.tracker.utils.DateRangeUtils
import java.time.format.DateTimeFormatter

private val salaryDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val rangeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun PayPeriodSalarySuggestionDialog(
    suggestion: SalaryPayPeriodDetector.Suggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formattedDate = suggestion.salaryDate.format(salaryDateFormatter)
    val newRange = DateRangeUtils.formatDateRange(
        suggestion.newRange.first,
        suggestion.newRange.second,
        rangeFormatter,
    )
    val currentRange = DateRangeUtils.formatDateRange(
        suggestion.currentRange.first,
        suggestion.currentRange.second,
        rangeFormatter,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.pay_period_suggestion_title, formattedDate))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.pay_period_suggestion_message,
                    formattedDate,
                    newRange,
                    currentRange,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.pay_period_suggestion_use, formattedDate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pay_period_suggestion_dismiss))
            }
        },
    )
}
