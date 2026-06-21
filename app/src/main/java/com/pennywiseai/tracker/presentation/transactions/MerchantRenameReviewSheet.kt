package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.ConfidenceTier
import com.pennywiseai.tracker.domain.model.TransactionRenameCandidate
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ── Tier accent colors ────────────────────────────────────────────────────────
private val TierColorExact  = Color(0xFF388E3C) // green  — safe to bulk apply
private val TierColorClose  = Color(0xFFF57C00) // amber  — check before applying
private val TierColorFuzzy  = Color(0xFFD84315) // deep orange — review individually

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantRenameGroupedSheet(
    state: MerchantRenameGroupedState,
    onApplyTier: (ConfidenceTier) -> Unit,
    onSkipTier: (ConfidenceTier) -> Unit,
    onApproveFuzzy: () -> Unit,
    onSkipFuzzy: () -> Unit,
    onOpenTransaction: (Long) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.merchant_rename_grouped_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.merchant_rename_grouped_subtitle,
                            state.newMerchantName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            // ── Exact tier ──────────────────────────────────────────────────
            state.exactGroup?.let { group ->
                TierSection(
                    label = stringResource(R.string.merchant_rename_tier_exact),
                    group = group,
                    description = stringResource(R.string.merchant_rename_tier_exact_desc),
                    tierColor = TierColorExact,
                    showOpenButton = false,
                    onApply = { onApplyTier(ConfidenceTier.EXACT) },
                    onSkip = { onSkipTier(ConfidenceTier.EXACT) },
                    onOpenTransaction = onOpenTransaction,
                )
            }

            // ── Close tier ──────────────────────────────────────────────────
            state.closeGroup?.let { group ->
                if (state.exactGroup != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TierSection(
                    label = stringResource(R.string.merchant_rename_tier_close),
                    group = group,
                    description = stringResource(R.string.merchant_rename_tier_close_desc),
                    tierColor = TierColorClose,
                    showOpenButton = true,
                    onApply = { onApplyTier(ConfidenceTier.CLOSE) },
                    onSkip = { onSkipTier(ConfidenceTier.CLOSE) },
                    onOpenTransaction = onOpenTransaction,
                )
            }

            // ── Fuzzy tier (individual flash-card review) ───────────────────
            state.fuzzyGroup?.let { group ->
                if (state.exactGroup != null || state.closeGroup != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                FuzzyTierSection(
                    group = group,
                    currentTransaction = state.currentFuzzyTransaction,
                    reviewIndex = state.fuzzyReviewIndex,
                    newMerchantName = state.newMerchantName,
                    tierColor = TierColorFuzzy,
                    onApprove = onApproveFuzzy,
                    onSkip = onSkipFuzzy,
                    onOpenTransaction = onOpenTransaction,
                )
            }
        }
    }
}

// ── Bulk tier section (Exact / Close) ─────────────────────────────────────────

@Composable
private fun TierSection(
    label: String,
    group: RenameGroup,
    description: String,
    tierColor: Color,
    showOpenButton: Boolean,
    onApply: () -> Unit,
    onSkip: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    // Map each unique merchant name → first matching transaction ID for the "open" action
    val sampleRows: List<Pair<String, Long>> = remember(group) {
        group.transactions
            .groupBy { it.currentMerchantName }
            .entries
            .take(3)
            .map { (name, txns) -> name to txns.first().transactionId }
    }

    // Left accent bar + rounded card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = tierColor.copy(alpha = 0.07f),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tierColor,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tierColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "${group.count}",
                            style = MaterialTheme.typography.labelMedium,
                            color = tierColor,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Merchant sample rows
                sampleRows.forEach { (merchantName, transactionId) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = merchantName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showOpenButton) {
                            IconButton(
                                onClick = { onOpenTransaction(transactionId) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = stringResource(R.string.merchant_rename_open_transaction_cd),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = tierColor,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                when {
                    group.isApplying -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimensions.Icon.small),
                                color = tierColor,
                            )
                            Text(
                                text = stringResource(R.string.merchant_rename_applying),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    group.approved -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = tierColor,
                            )
                            Text(
                                text = stringResource(R.string.merchant_rename_tier_applied, group.count),
                                style = MaterialTheme.typography.labelMedium,
                                color = tierColor,
                            )
                        }
                    }
                    group.skipped -> {
                        Text(
                            text = stringResource(R.string.merchant_rename_tier_skipped),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            OutlinedButton(
                                onClick = onSkip,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.merchant_rename_skip_tier))
                            }
                            Button(
                                onClick = onApply,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = tierColor),
                            ) {
                                Text(stringResource(R.string.merchant_rename_apply_tier, group.count))
                            }
                        }
                    }
                }
            }
        }
        // Colored left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(tierColor)
        )
    }
}

// ── Fuzzy individual flash-card section ───────────────────────────────────────

@Composable
private fun FuzzyTierSection(
    group: RenameGroup,
    currentTransaction: TransactionRenameCandidate?,
    reviewIndex: Int,
    newMerchantName: String,
    tierColor: Color,
    onApprove: () -> Unit,
    onSkip: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = tierColor.copy(alpha = 0.07f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.merchant_rename_tier_fuzzy),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tierColor,
                    )
                    Text(
                        text = stringResource(
                            R.string.merchant_rename_fuzzy_progress,
                            reviewIndex + 1,
                            group.count,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = tierColor,
                    )
                }

                Text(
                    text = stringResource(R.string.merchant_rename_tier_fuzzy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (currentTransaction != null) {
                    AnimatedContent(
                        targetState = currentTransaction.transactionId,
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(tween(180))
                                )
                        },
                        label = "fuzzy_flash_card",
                    ) { transactionId ->
                        key(transactionId) {
                            TransactionRenameFlashCard(
                                transaction = currentTransaction,
                                newMerchantName = newMerchantName,
                                tierColor = tierColor,
                                onOpenTransaction = { onOpenTransaction(currentTransaction.transactionId) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.merchant_rename_review_skip))
                        }
                        Button(
                            onClick = onApprove,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = tierColor),
                        ) {
                            Text(stringResource(R.string.merchant_rename_review_rename))
                        }
                    }
                }
            }
        }
        // Colored left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(tierColor)
        )
    }
}

// ── Flash card (reused from previous implementation) ──────────────────────────

@Composable
private fun TransactionRenameFlashCard(
    transaction: TransactionRenameCandidate,
    newMerchantName: String,
    tierColor: Color,
    onOpenTransaction: () -> Unit,
) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")
    val matchPercent = (transaction.similarityScore * 100).roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.merchant_rename_review_match_score, matchPercent),
                style = MaterialTheme.typography.labelMedium,
                color = tierColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BrandIcon(
                    merchantName = transaction.currentMerchantName,
                    size = 40.dp,
                    showBackground = true,
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BrandIcon(
                    merchantName = newMerchantName,
                    size = 40.dp,
                    showBackground = true,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = transaction.currentMerchantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.merchant_rename_review_arrow_to, newMerchantName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = transaction.dateTime.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(
                            transaction.amount,
                            transaction.currency.ifEmpty { "INR" },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onOpenTransaction,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = stringResource(R.string.merchant_rename_open_transaction_cd),
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = tierColor,
                        )
                    }
                }
            }
        }
    }
}
