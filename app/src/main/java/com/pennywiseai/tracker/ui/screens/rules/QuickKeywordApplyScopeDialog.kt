package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.QuickKeywordApplyScope
import com.pennywiseai.tracker.ui.components.CustomDateRangePickerDialog
import com.pennywiseai.tracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
fun QuickKeywordApplyScope.displayLabel(): String = when (this) {
    QuickKeywordApplyScope.AllTime -> stringResource(R.string.quick_keyword_apply_scope_all)
    is QuickKeywordApplyScope.LastDays -> when (days) {
        QuickKeywordApplyScope.DAYS_30 -> stringResource(R.string.quick_keyword_apply_scope_30d)
        QuickKeywordApplyScope.DAYS_90 -> stringResource(R.string.quick_keyword_apply_scope_90d)
        QuickKeywordApplyScope.DAYS_365 -> stringResource(R.string.quick_keyword_apply_scope_12m)
        else -> stringResource(R.string.quick_keyword_apply_scope_days_fallback, days)
    }
    is QuickKeywordApplyScope.CustomRange -> stringResource(
        R.string.quick_keyword_apply_scope_custom_summary,
        startDate.format(dateFormatter),
        endDate.format(dateFormatter),
    )
}

@Composable
fun QuickKeywordApplyScopeDialog(
    title: String,
    message: String? = null,
    uncategorizedOnly: Boolean,
    onUncategorizedOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (QuickKeywordApplyScope) -> Unit,
    initialScope: QuickKeywordApplyScope = QuickKeywordApplyScope.AllTime,
) {
    var selectedScope by remember { mutableStateOf(initialScope) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val custom = selectedScope as? QuickKeywordApplyScope.CustomRange
        CustomDateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                selectedScope = QuickKeywordApplyScope.CustomRange(start, end)
                showDatePicker = false
            },
            initialStartDate = custom?.startDate,
            initialEndDate = custom?.endDate,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.quick_keyword_apply_scope_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ScopeChip(
                        label = stringResource(R.string.quick_keyword_apply_scope_all),
                        selected = selectedScope is QuickKeywordApplyScope.AllTime,
                        onClick = { selectedScope = QuickKeywordApplyScope.AllTime },
                    )
                    ScopeChip(
                        label = stringResource(R.string.quick_keyword_apply_scope_30d),
                        selected = selectedScope is QuickKeywordApplyScope.LastDays &&
                            (selectedScope as QuickKeywordApplyScope.LastDays).days ==
                            QuickKeywordApplyScope.DAYS_30,
                        onClick = {
                            selectedScope = QuickKeywordApplyScope.LastDays(QuickKeywordApplyScope.DAYS_30)
                        },
                    )
                    ScopeChip(
                        label = stringResource(R.string.quick_keyword_apply_scope_90d),
                        selected = selectedScope is QuickKeywordApplyScope.LastDays &&
                            (selectedScope as QuickKeywordApplyScope.LastDays).days ==
                            QuickKeywordApplyScope.DAYS_90,
                        onClick = {
                            selectedScope = QuickKeywordApplyScope.LastDays(QuickKeywordApplyScope.DAYS_90)
                        },
                    )
                    ScopeChip(
                        label = stringResource(R.string.quick_keyword_apply_scope_12m),
                        selected = selectedScope is QuickKeywordApplyScope.LastDays &&
                            (selectedScope as QuickKeywordApplyScope.LastDays).days ==
                            QuickKeywordApplyScope.DAYS_365,
                        onClick = {
                            selectedScope = QuickKeywordApplyScope.LastDays(QuickKeywordApplyScope.DAYS_365)
                        },
                    )
                    ScopeChip(
                        label = stringResource(R.string.quick_keyword_apply_scope_custom),
                        selected = selectedScope is QuickKeywordApplyScope.CustomRange,
                        onClick = { showDatePicker = true },
                    )
                }
                if (selectedScope is QuickKeywordApplyScope.CustomRange) {
                    val range = selectedScope as QuickKeywordApplyScope.CustomRange
                    Text(
                        text = stringResource(
                            R.string.quick_keyword_apply_scope_custom_summary,
                            range.startDate.format(dateFormatter),
                            range.endDate.format(dateFormatter),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = uncategorizedOnly,
                        onCheckedChange = onUncategorizedOnlyChange,
                    )
                    Text(
                        text = stringResource(R.string.quick_keyword_apply_uncategorized_only),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedScope) }) {
                Text(stringResource(R.string.quick_keyword_apply_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pay_period_cancel))
            }
        },
    )
}

@Composable
private fun ScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
