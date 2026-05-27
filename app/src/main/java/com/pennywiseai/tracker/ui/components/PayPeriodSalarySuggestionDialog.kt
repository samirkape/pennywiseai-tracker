package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.service.SalaryPayPeriodDetector
import java.time.format.DateTimeFormatter

private val salaryDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
fun PayPeriodSalarySuggestionDialog(
    suggestion: SalaryPayPeriodDetector.Suggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formattedDate = suggestion.salaryDate.format(salaryDateFormatter)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.pay_period_suggestion_title, formattedDate))
        },
        text = {
            Text(
                text = stringResource(R.string.pay_period_suggestion_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.pay_period_suggestion_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pay_period_suggestion_dismiss))
            }
        },
    )
}
