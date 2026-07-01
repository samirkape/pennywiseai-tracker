package com.spendly.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.spendly.tracker.R
import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickKeywordCategoryPicker(
    categoryLabel: String,
    onCategorySelected: (String) -> Unit,
    categoryEntities: List<CategoryEntity>,
    usedCategoryNames: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val entityByName = remember(categoryEntities) {
        categoryEntities.associateBy { it.name }
    }
    val allNames = remember(categoryEntities, usedCategoryNames, categoryLabel) {
        (categoryEntities.map { it.name } + usedCategoryNames + categoryLabel)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.quick_keyword_your_categories),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.quick_keyword_category_picker_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allNames.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                allNames.forEach { name ->
                    val entity = entityByName[name]
                    val selected = categoryLabel.equals(name, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { onCategorySelected(name) },
                        enabled = enabled,
                        label = { Text(name) },
                        leadingIcon = entity?.let {
                            {
                                CategoryDot(
                                    color = it.color,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = categoryLabel,
                onValueChange = onCategorySelected,
                label = { Text(stringResource(R.string.quick_keyword_category_label)) },
                placeholder = { Text(stringResource(R.string.quick_keyword_category_hint)) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
            )
            Box {
                TextButton(
                    onClick = { if (enabled) dropdownExpanded = true },
                    enabled = enabled && allNames.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.quick_keyword_category_list))
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    allNames.forEach { name ->
                        val entity = entityByName[name]
                        DropdownMenuItem(
                            text = {
                                if (entity != null) {
                                    CategoryChip(category = entity)
                                } else {
                                    Text(name)
                                }
                            },
                            onClick = {
                                onCategorySelected(name)
                                dropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickKeywordTagsPicker(
    pendingTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    usedTags: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var tagInput by remember { mutableStateOf("") }
    val tagSuggestions = remember(tagInput, usedTags, pendingTags) {
        val query = tagInput.trim()
        val pool = if (query.isBlank()) usedTags else usedTags.filter { it.contains(query, ignoreCase = true) }
        pool.filter { it !in pendingTags }.take(8)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.quick_keyword_tags_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.quick_keyword_tags_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pendingTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                pendingTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        enabled = enabled,
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.quick_keyword_remove_tag,
                                ),
                                modifier = Modifier.size(Dimensions.Icon.small),
                            )
                        },
                    )
                }
            }
        }
        if (usedTags.isNotEmpty()) {
            Text(
                text = stringResource(R.string.quick_keyword_tags_used_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                usedTags
                    .filter { it !in pendingTags }
                    .take(16)
                    .forEach { tag ->
                        SuggestionChip(
                        onClick = {
                            onAddTag(tag)
                            tagInput = ""
                        },
                            enabled = enabled,
                            label = { Text(tag, style = MaterialTheme.typography.bodySmall) },
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
            }
        }
        if (tagSuggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                tagSuggestions.take(8).forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onAddTag(suggestion)
                            tagInput = ""
                        },
                        enabled = enabled,
                        label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                        border = null,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.quick_keyword_tags_add_placeholder)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (tagInput.isNotBlank()) {
                            onAddTag(tagInput)
                            tagInput = ""
                        }
                    },
                ),
            )
            TextButton(
                onClick = {
                    if (tagInput.isNotBlank()) {
                        onAddTag(tagInput)
                        tagInput = ""
                    }
                },
                enabled = enabled && tagInput.isNotBlank(),
            ) {
                Text(stringResource(R.string.quick_keyword_tags_add_button))
            }
        }
    }
}
