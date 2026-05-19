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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransferKind
import com.pennywiseai.tracker.domain.model.QuickKeywordExpenseChannel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionTypeFilterChips(
    matchType: TransactionType?,
    matchExpenseChannel: QuickKeywordExpenseChannel?,
    matchTransferKind: String?,
    onMatchTypeChange: (TransactionType?) -> Unit,
    onExpenseChannelChange: (QuickKeywordExpenseChannel?) -> Unit,
    onTransferKindChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showAnyType: Boolean = true,
    title: String? = null,
) {
    val isExpenseFamily = matchType == TransactionType.EXPENSE || matchType == TransactionType.CREDIT

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (showAnyType) {
                TypeFilterChip(
                    label = stringResource(R.string.quick_keyword_type_any),
                    selected = matchType == null,
                    enabled = enabled,
                    onClick = { onMatchTypeChange(null) },
                )
            }
            listOf(
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                TransactionType.TRANSFER,
                TransactionType.INVESTMENT,
            ).forEach { type ->
                val selected = when (type) {
                    TransactionType.EXPENSE -> isExpenseFamily
                    else -> matchType == type
                }
                TypeFilterChip(
                    label = type.name.lowercase(Locale.getDefault())
                        .replaceFirstChar { c -> c.titlecase(Locale.getDefault()) },
                    selected = selected,
                    enabled = enabled,
                    onClick = {
                        onMatchTypeChange(type)
                        if (type == TransactionType.EXPENSE) {
                            onExpenseChannelChange(null)
                        }
                    },
                )
            }
        }

        if (isExpenseFamily) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TypeFilterChip(
                    label = stringResource(R.string.quick_keyword_expense_all),
                    selected = matchType == TransactionType.EXPENSE && matchExpenseChannel == null,
                    enabled = enabled,
                    onClick = {
                        onMatchTypeChange(TransactionType.EXPENSE)
                        onExpenseChannelChange(null)
                    },
                    isSecondary = true,
                )
                listOf(
                    QuickKeywordExpenseChannel.ACCOUNT to stringResource(R.string.txn_type_channel_account),
                    QuickKeywordExpenseChannel.CASH to stringResource(R.string.txn_type_channel_cash),
                    QuickKeywordExpenseChannel.CREDIT_CARD to stringResource(R.string.txn_type_channel_credit_card),
                ).forEach { (channel, label) ->
                    val selected = when (channel) {
                        QuickKeywordExpenseChannel.CREDIT_CARD -> matchType == TransactionType.CREDIT
                        else -> matchType == TransactionType.EXPENSE && matchExpenseChannel == channel
                    }
                    TypeFilterChip(
                        label = label,
                        selected = selected,
                        enabled = enabled,
                        onClick = {
                            onExpenseChannelChange(channel)
                            onMatchTypeChange(
                                if (channel == QuickKeywordExpenseChannel.CREDIT_CARD) {
                                    TransactionType.CREDIT
                                } else {
                                    TransactionType.EXPENSE
                                },
                            )
                        },
                        isSecondary = true,
                    )
                }
            }
        }

        if (matchType == TransactionType.TRANSFER) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TypeFilterChip(
                    label = stringResource(R.string.quick_keyword_transfer_any),
                    selected = matchTransferKind == null,
                    enabled = enabled,
                    onClick = { onTransferKindChange(null) },
                    isSecondary = true,
                )
                listOf(
                    TransferKind.SELF_TRANSFER to stringResource(R.string.txn_type_transfer_self),
                    TransferKind.OTHERS_TRANSFER to stringResource(R.string.txn_type_transfer_others),
                ).forEach { (kind, label) ->
                    TypeFilterChip(
                        label = label,
                        selected = matchTransferKind == kind,
                        enabled = enabled,
                        onClick = { onTransferKindChange(kind) },
                        isSecondary = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeFilterChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    isSecondary: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
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
        colors = if (isSecondary) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
                labelColor = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
                labelColor = MaterialTheme.colorScheme.onSurface,
            )
        },
        border = FilterChipDefaults.filterChipBorder(
            borderWidth = 0.dp,
            selected = selected,
            enabled = enabled,
        ),
    )
}
