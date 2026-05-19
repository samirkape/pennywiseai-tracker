package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleMatcher
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun QuickKeywordApplyResultDialog(
    batch: BatchApplyResult,
    diagnostics: QuickKeywordRuleMatcher.BatchStats?,
    onDismiss: () -> Unit,
    undoMinutesRemaining: Int? = null,
    onUndo: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_keyword_run_result_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.quick_keyword_run_result_message,
                        batch.totalUpdated,
                        batch.totalProcessed,
                    ),
                )
                if (diagnostics != null && diagnostics.alreadyHadLabels > 0) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(
                            R.string.quick_keyword_run_result_already_labeled,
                            diagnostics.alreadyHadLabels,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (diagnostics != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(
                            R.string.quick_keyword_run_result_scope_note,
                            diagnostics.applyScope.displayLabel(),
                            diagnostics.transactionsInRange,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    if (diagnostics.typeOverwritten > 0) {
                        Text(
                            text = stringResource(
                                R.string.quick_keyword_run_result_type_overwritten,
                                diagnostics.typeOverwritten,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                    if (diagnostics.uncategorizedOnly) {
                        Text(
                            text = stringResource(
                                R.string.quick_keyword_run_result_uncategorized_note,
                                diagnostics.poolSize,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                    Text(
                        text = stringResource(
                            R.string.quick_keyword_run_result_detail,
                            diagnostics.keywordMatched,
                            diagnostics.alreadyHadLabels,
                            diagnostics.typeRejected,
                            diagnostics.noKeywordHit,
                            diagnostics.emptySearchText,
                            diagnostics.withSmsBody,
                            diagnostics.poolSize,
                            if (diagnostics.sampleFailures.isNotEmpty()) {
                                "\nExamples:\n" + diagnostics.sampleFailures.take(3).joinToString("\n")
                            } else {
                                ""
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.quick_keyword_run_result_logcat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.quick_keyword_run_result_ok))
            }
        },
        dismissButton = if (undoMinutesRemaining != null && undoMinutesRemaining > 0 && onUndo != null) {
            {
                TextButton(onClick = onUndo) {
                    Text(
                        stringResource(
                            R.string.quick_keyword_run_result_undo,
                            undoMinutesRemaining,
                        ),
                    )
                }
            }
        } else {
            null
        },
    )
}
