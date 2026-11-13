package com.spendly.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.spendly.tracker.ui.theme.Spacing

/**
 * Prompt shown once per evening when the user opens the app and today's
 * transactions still need a category.
 */
data class DailyReviewPrompt(
    val dateKey: String,
    val todayCount: Int,
    val olderCount: Int,
)

/**
 * End-of-day nudge asking the user to categorize today's transactions.
 * Offers a secondary path into older uncategorized transactions.
 */
@Composable
fun DailyReviewDialog(
    prompt: DailyReviewPrompt,
    onReviewToday: () -> Unit,
    onReviewOlder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val todayLabel = if (prompt.todayCount == 1) {
        "1 transaction from today still needs a category."
    } else {
        "${prompt.todayCount} transactions from today still need a category."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Wrap up your day",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = todayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Add a category and optional tags so your insights stay accurate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (prompt.olderCount > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (prompt.olderCount == 1) {
                                "1 older uncategorized transaction"
                            } else {
                                "${prompt.olderCount} older uncategorized transactions"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onReviewOlder) {
                            Text("Review")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onReviewToday) {
                Text("Review today")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}
