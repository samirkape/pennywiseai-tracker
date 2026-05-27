package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.pennywiseai.tracker.ui.theme.Dimensions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.QuickKeywordMatchField
import com.pennywiseai.tracker.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickKeywordMatchFieldChips(
    selected: QuickKeywordMatchField,
    onSelected: (QuickKeywordMatchField) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.quick_keyword_match_field_title),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                QuickKeywordMatchField.ALL_TEXT to R.string.quick_keyword_match_field_all,
                QuickKeywordMatchField.SMS_TEXT to R.string.quick_keyword_match_field_sms,
                QuickKeywordMatchField.MERCHANT to R.string.quick_keyword_match_field_merchant,
                QuickKeywordMatchField.DESCRIPTION to R.string.quick_keyword_match_field_description,
                QuickKeywordMatchField.TAGS to R.string.quick_keyword_match_field_tags,
            ).forEach { (field, labelRes) ->
                FilterChip(
                    selected = selected == field,
                    onClick = { onSelected(field) },
                    label = { Text(stringResource(labelRes)) },
                    enabled = enabled,
                    leadingIcon = if (selected == field) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small),
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}
