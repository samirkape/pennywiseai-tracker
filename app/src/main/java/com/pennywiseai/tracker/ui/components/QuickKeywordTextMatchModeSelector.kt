package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.QuickKeywordTextMatchMode
import com.pennywiseai.tracker.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickKeywordTextMatchModeSelector(
    selected: QuickKeywordTextMatchMode,
    onSelected: (QuickKeywordTextMatchMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = stringResource(textMatchModeLabelRes(selected)),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.quick_keyword_text_match_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                enabled = enabled,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                QuickKeywordTextMatchMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = stringResource(textMatchModeLabelRes(mode)),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(textMatchModeHintRes(mode)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelected(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(textMatchModeHintRes(selected)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun textMatchModeLabelRes(mode: QuickKeywordTextMatchMode): Int = when (mode) {
    QuickKeywordTextMatchMode.CONTAINS_ANY -> R.string.quick_keyword_match_contains_any
    QuickKeywordTextMatchMode.CONTAINS_ALL -> R.string.quick_keyword_match_contains_all
    QuickKeywordTextMatchMode.EQUALS_ONE_OF -> R.string.quick_keyword_match_equals_one_of
    QuickKeywordTextMatchMode.STARTS_WITH_ANY -> R.string.quick_keyword_match_starts_with_any
    QuickKeywordTextMatchMode.ENDS_WITH_ANY -> R.string.quick_keyword_match_ends_with_any
    QuickKeywordTextMatchMode.NOT_CONTAINS_ANY -> R.string.quick_keyword_match_not_contains_any
    QuickKeywordTextMatchMode.REGEX_ANY -> R.string.quick_keyword_match_regex_any
}

@Composable
private fun textMatchModeHintRes(mode: QuickKeywordTextMatchMode): Int = when (mode) {
    QuickKeywordTextMatchMode.CONTAINS_ANY -> R.string.quick_keyword_match_contains_any_hint
    QuickKeywordTextMatchMode.CONTAINS_ALL -> R.string.quick_keyword_match_contains_all_hint
    QuickKeywordTextMatchMode.EQUALS_ONE_OF -> R.string.quick_keyword_match_equals_one_of_hint
    QuickKeywordTextMatchMode.STARTS_WITH_ANY -> R.string.quick_keyword_match_starts_with_any_hint
    QuickKeywordTextMatchMode.ENDS_WITH_ANY -> R.string.quick_keyword_match_ends_with_any_hint
    QuickKeywordTextMatchMode.NOT_CONTAINS_ANY -> R.string.quick_keyword_match_not_contains_any_hint
    QuickKeywordTextMatchMode.REGEX_ANY -> R.string.quick_keyword_match_regex_any_hint
}
