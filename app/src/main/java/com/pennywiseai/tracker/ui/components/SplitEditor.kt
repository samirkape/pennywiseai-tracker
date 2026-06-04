package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

data class SplitItem(
    val id: Long = 0,
    val category: String,
    val amount: BigDecimal,
    /** Additional personal tags for labeling. Does not affect budget accounting. */
    val tags: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditor(
    totalAmount: BigDecimal,
    currency: String,
    splits: List<SplitItem>,
    availableCategories: List<String>,
    onSplitsChanged: (List<SplitItem>) -> Unit,
    onRemoveSplits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val splitsTotal = splits.sumOf { it.amount }
    val remaining = totalAmount - splitsTotal
    val isBalanced = remaining.abs() <= BigDecimal("0.01")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Split Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onRemoveSplits,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove Splits")
                }
            }

            // Split rows
            splits.forEachIndexed { index, split ->
                val isLastSplit = index == splits.size - 1
                SplitRow(
                    split = split,
                    availableCategories = availableCategories.filter { cat ->
                        cat == split.category || splits.none { it.category == cat }
                    },
                    availableTags = availableCategories.filter { it != split.category },
                    onCategoryChanged = { newCategory ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(category = newCategory)
                        onSplitsChanged(newSplits)
                    },
                    onAmountChanged = { newAmount ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(amount = newAmount)
                        // Auto-adjust the last split when editing any non-last split
                        if (!isLastSplit && newSplits.size >= 2) {
                            val sumExceptLast = newSplits.dropLast(1)
                                .fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }
                            val autoAmount = (totalAmount - sumExceptLast)
                                .coerceAtLeast(BigDecimal.ZERO)
                            newSplits[newSplits.size - 1] =
                                newSplits.last().copy(amount = autoAmount)
                        }
                        onSplitsChanged(newSplits)
                    },
                    onTagsChanged = { newTags ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(tags = newTags)
                        onSplitsChanged(newSplits)
                    },
                    onRemove = {
                        if (splits.size > 2) {
                            val newSplits = splits.toMutableList()
                            val removed = newSplits.removeAt(index)
                            if (newSplits.isNotEmpty()) {
                                val lastIdx = newSplits.lastIndex
                                newSplits[lastIdx] = newSplits[lastIdx].copy(
                                    amount = newSplits[lastIdx].amount + removed.amount,
                                )
                            }
                            onSplitsChanged(newSplits)
                        }
                    },
                    canRemove = splits.size > 2,
                    currency = currency
                )
            }

            // Add split button
            OutlinedButton(
                onClick = {
                    val usedCategories = splits.map { it.category }.toSet()
                    val nextCategory = availableCategories.firstOrNull { it !in usedCategories } ?: "Others"
                    val withNew = splits + SplitItem(
                        id = 0,
                        category = nextCategory,
                        amount = BigDecimal.ZERO,
                    )
                    val sumFirst = withNew.dropLast(1).fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }
                    val lastAmt = (totalAmount - sumFirst).coerceAtLeast(BigDecimal.ZERO)
                    onSplitsChanged(withNew.dropLast(1) + withNew.last().copy(amount = lastAmt))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = availableCategories.size > splits.size
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Add Split")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            // Total validation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBalanced) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Balanced",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Not balanced",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    }
                    Text(
                        text = "Total:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(splitsTotal, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isBalanced) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                    if (!isBalanced) {
                        Text(
                            text = if (remaining > BigDecimal.ZERO) {
                                "${CurrencyFormatter.formatCurrency(remaining, currency)} remaining"
                            } else {
                                "${CurrencyFormatter.formatCurrency(remaining.abs(), currency)} over"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SplitRow(
    split: SplitItem,
    availableCategories: List<String>,
    availableTags: List<String>,
    onCategoryChanged: (String) -> Unit,
    onAmountChanged: (BigDecimal) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    currency: String,
    modifier: Modifier = Modifier
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var tagExpanded by remember { mutableStateOf(false) }
    var amountText by remember(split.amount) {
        mutableStateOf(
            if (split.amount == BigDecimal.ZERO) ""
            else split.amount.stripTrailingZeros().toPlainString()
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Category + amount + remove
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    value = split.category,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                onCategoryChanged(category)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            TextField(
                value = amountText,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        amountText = filtered
                        onAmountChanged(filtered.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                    }
                },
                singleLine = true,
                modifier = Modifier.width(120.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = {
                    Text(CurrencyFormatter.getCurrencySymbol(currency), style = MaterialTheme.typography.bodyMedium)
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.size(Dimensions.Component.minTouchTarget)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove split",
                    tint = if (canRemove) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        }

        // Tags: existing tag chips + "+" chip to add more
        val tagsNotYetAdded = availableTags.filter { it !in split.tags }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            split.tags.forEach { tag ->
                SuggestionChip(
                    onClick = { onTagsChanged(split.tags - tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    icon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove tag",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            if (tagsNotYetAdded.isNotEmpty()) {
                Box {
                    SuggestionChip(
                        onClick = { tagExpanded = true },
                        label = { Text("+ tag", style = MaterialTheme.typography.labelSmall) }
                    )
                    DropdownMenu(
                        expanded = tagExpanded,
                        onDismissRequest = { tagExpanded = false }
                    ) {
                        tagsNotYetAdded.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    onTagsChanged(split.tags + tag)
                                    tagExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing split breakdown in view mode (read-only).
 */
@Composable
fun SplitBreakdownCard(
    splits: List<SplitItem>,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Category Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            splits.forEach { split ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = split.category, style = MaterialTheme.typography.bodyMedium)
                        if (split.tags.isNotEmpty()) {
                            Text(
                                text = split.tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(split.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
