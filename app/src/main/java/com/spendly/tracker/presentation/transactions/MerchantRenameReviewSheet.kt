package com.spendly.tracker.presentation.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.R
import com.spendly.tracker.domain.model.ConfidenceTier
import com.spendly.tracker.ui.components.BrandIcon
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TierColorExact = Color(0xFF388E3C)
private val TierColorClose = Color(0xFFF57C00)
private val TierColorFuzzy = Color(0xFFD84315)

private fun ConfidenceTier.accentColor() = when (this) {
    ConfidenceTier.EXACT -> TierColorExact
    ConfidenceTier.CLOSE -> TierColorClose
    ConfidenceTier.FUZZY -> TierColorFuzzy
}

private fun ConfidenceTier.label() = when (this) {
    ConfidenceTier.EXACT -> "Identical"
    ConfidenceTier.CLOSE -> "Similar"
    ConfidenceTier.FUZZY -> "Possible match"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantRenameGroupedSheet(
    state: MerchantRenameGroupedState,
    onApprove: () -> Unit,
    onSkip: () -> Unit,
    onOpenTransaction: (Long) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entry = state.current

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
            // Header
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

            // Progress bar + counter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { if (state.totalCount > 0) state.reviewIndex.toFloat() / state.totalCount else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = entry?.tier?.accentColor() ?: MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.merchant_rename_fuzzy_progress,
                        state.reviewIndex + 1,
                        state.totalCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Flash-card
            if (entry != null) {
                AnimatedContent(
                    targetState = entry.candidate.transactionId,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(tween(180))
                            )
                    },
                    label = "rename_flash_card",
                ) { transactionId ->
                    key(transactionId) {
                        RenameFlashCard(
                            entry = entry,
                            newMerchantName = state.newMerchantName,
                            onOpenTransaction = { onOpenTransaction(entry.candidate.transactionId) },
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = entry.tier.accentColor(),
                        ),
                    ) {
                        Text(stringResource(R.string.merchant_rename_review_rename))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameFlashCard(
    entry: RenameQueueEntry,
    newMerchantName: String,
    onOpenTransaction: () -> Unit,
) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")
    val matchPercent = (entry.candidate.similarityScore * 100).roundToInt()
    val tierColor = entry.tier.accentColor()

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
            // Tier badge + match score
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tierColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = entry.tier.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.merchant_rename_review_match_score, matchPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Brand icons with arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BrandIcon(
                    merchantName = entry.candidate.currentMerchantName,
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

            // Merchant names
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = entry.candidate.currentMerchantName,
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

            // Transaction detail row
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
                            text = entry.candidate.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = entry.candidate.dateTime.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(
                            entry.candidate.amount,
                            entry.candidate.currency.ifEmpty { "INR" },
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
