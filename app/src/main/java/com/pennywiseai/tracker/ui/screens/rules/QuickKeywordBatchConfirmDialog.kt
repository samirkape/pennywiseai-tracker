package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.QuickKeywordBatchChange
import com.pennywiseai.tracker.domain.model.QuickKeywordBatchPreview
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

@Composable
fun QuickKeywordBatchConfirmDialog(
    preview: QuickKeywordBatchPreview,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        title = { Text(stringResource(R.string.quick_keyword_batch_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(
                        R.string.quick_keyword_batch_confirm_summary,
                        preview.willUpdate,
                        preview.keywordMatched,
                        preview.applyScope.displayLabel(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (preview.alreadyLabeled > 0) {
                    Text(
                        text = stringResource(
                            R.string.quick_keyword_batch_confirm_skip_labeled,
                            preview.alreadyLabeled,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.sampleChanges.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.quick_keyword_batch_confirm_samples),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    preview.sampleChanges.forEach { change ->
                        BatchChangeSampleCard(change = change)
                    }
                }
                Text(
                    text = stringResource(R.string.quick_keyword_batch_confirm_undo_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isApplying && preview.willUpdate > 0,
            ) {
                Text(
                    if (isApplying) {
                        stringResource(R.string.quick_keyword_batch_confirm_applying)
                    } else {
                        stringResource(
                            R.string.quick_keyword_batch_confirm_apply,
                            preview.willUpdate,
                        )
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text(stringResource(R.string.pay_period_cancel))
            }
        },
    )
}

@Composable
private fun BatchChangeSampleCard(change: QuickKeywordBatchChange) {
    val dateLabel = change.dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(
                    R.string.quick_keyword_batch_sample_header,
                    CurrencyFormatter.formatCurrency(change.amount, change.after.currency),
                    dateLabel,
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            change.matchedKeyword?.let { keyword ->
                Text(
                    text = stringResource(R.string.quick_keyword_batch_sample_keyword, keyword),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (change.merchantChanges) {
                InlineFieldDiff(
                    label = stringResource(R.string.quick_keyword_batch_field_merchant),
                    before = change.beforeMerchant,
                    after = change.afterMerchant,
                )
            }
            if (change.categoryChanges) {
                InlineFieldDiff(
                    label = stringResource(R.string.quick_keyword_batch_field_category),
                    before = change.beforeCategory,
                    after = change.afterCategory,
                )
            }
            if (change.typeChanges) {
                InlineFieldDiff(
                    label = stringResource(R.string.quick_keyword_batch_field_type),
                    before = change.beforeType.name.lowercase().replaceFirstChar { it.uppercase() },
                    after = change.afterType.name.lowercase().replaceFirstChar { it.uppercase() },
                )
            }
        }
    }
}

@Composable
private fun InlineFieldDiff(
    label: String,
    before: String,
    after: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = before,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = after,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
