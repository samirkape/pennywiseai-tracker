package com.spendly.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionGroupEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.ui.components.BrandIcon
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.expense_dark
import com.spendly.tracker.ui.theme.expense_light
import com.spendly.tracker.ui.theme.income_dark
import com.spendly.tracker.ui.theme.income_light
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

private const val NOTE_MAX_CHARS = 42

/**
 * Overlapping merchant avatars for group rows (home feed, group detail hero).
 */
@Composable
fun GroupMerchantAvatarStack(
    merchantNames: List<String>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    overlapStep: Dp = 20.dp,
    merchantCategories: Map<String, String>? = null,
) {
    val maxIcons = 3
    val visible = merchantNames.take(maxIcons)
    val extraCount = merchantNames.size - maxIcons

    if (visible.isEmpty()) {
        Box(
            modifier = modifier
                .size(Dimensions.Icon.list)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        return
    }

    Box(modifier = modifier) {
        visible.forEachIndexed { index, name ->
            BrandIcon(
                merchantName = name,
                categoryOverride = merchantCategories?.get(name),
                size = iconSize,
                modifier = Modifier
                    .offset(x = overlapStep * index)
                    .zIndex((maxIcons - index).toFloat())
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
            )
        }
        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = overlapStep * visible.size)
                    .zIndex(0f)
                    .size(iconSize)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(
            modifier = Modifier.width(
                overlapStep * (visible.size - 1).coerceAtLeast(0) +
                    iconSize +
                    if (extraCount > 0) overlapStep else 0.dp
            )
        )
    }
}

@Composable
fun GroupCard(
    group: TransactionGroupEntity,
    transactions: List<TransactionEntity>,
    convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    displayCurrency: String? = null,
    flat: Boolean = false,
    useCategoryIconFallback: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    val currency = displayCurrency ?: transactions.firstOrNull()?.currency ?: "INR"

    val total = remember(transactions, convertedAmounts) {
        transactions.fold(BigDecimal.ZERO) { acc, tx ->
            val amount = convertedAmounts[tx.id] ?: tx.amount
            when (tx.transactionType) {
                TransactionType.EXPENSE, TransactionType.CREDIT -> acc - amount
                TransactionType.INCOME -> acc + amount
                else -> acc
            }
        }
    }

    val totalIncome = remember(transactions, convertedAmounts) {
        transactions.fold(BigDecimal.ZERO) { acc, tx ->
            if (tx.transactionType != TransactionType.INCOME) return@fold acc
            acc + (convertedAmounts[tx.id] ?: tx.amount)
        }
    }
    val totalExpenseCredit = remember(transactions, convertedAmounts) {
        transactions.fold(BigDecimal.ZERO) { acc, tx ->
            if (tx.transactionType != TransactionType.EXPENSE && tx.transactionType != TransactionType.CREDIT) {
                return@fold acc
            }
            acc + (convertedAmounts[tx.id] ?: tx.amount)
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val groupBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)

    val isPositive = total >= BigDecimal.ZERO
    val amountColor = if (isPositive) {
        if (isDark) income_dark else income_light
    } else {
        if (isDark) expense_dark else expense_light
    }

    val sign = if (isPositive) "+" else "-"
    val formattedAmount = "$sign${CurrencyFormatter.formatCurrency(total.abs(), currency)}"

    val merchantOrder = remember(transactions) {
        transactions.map { it.merchantName }.distinct()
    }

    val merchantCategories = if (useCategoryIconFallback) {
        remember(transactions) {
            transactions.associate { it.merchantName to it.category }
        }
    } else {
        null
    }

    val dateRangeText = remember(transactions) {
        if (transactions.isEmpty()) return@remember ""
        val minD = transactions.minOf { it.dateTime.toLocalDate() }
        val maxD = transactions.maxOf { it.dateTime.toLocalDate() }
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        if (minD == maxD) {
            minD.format(fmt)
        } else {
            "${minD.format(fmt)}–${maxD.format(fmt)}"
        }
    }

    val count = transactions.size
    val itemPill = "${count} item${if (count != 1) "s" else ""}"

    val subtitleParts = buildList {
        if (dateRangeText.isNotEmpty()) add(dateRangeText)
        group.note?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
            val truncated = if (note.length > NOTE_MAX_CHARS) note.take(NOTE_MAX_CHARS - 1) + "…" else note
            add(truncated)
        }
    }
    val subtitle = subtitleParts.joinToString(" · ")

    // Flat mode subtitle also includes the item count at the front
    val flatSubtitle = buildList {
        add(itemPill)
        if (dateRangeText.isNotEmpty()) add(dateRangeText)
        group.note?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
            val truncated = if (note.length > NOTE_MAX_CHARS) note.take(NOTE_MAX_CHARS - 1) + "…" else note
            add(truncated)
        }
    }.joinToString(" · ")

    val showSplit =
        totalIncome > BigDecimal.ZERO && totalExpenseCredit > BigDecimal.ZERO
    val splitLine = if (showSplit) {
        "+${CurrencyFormatter.formatCurrency(totalIncome, currency)} · -${CurrencyFormatter.formatCurrency(totalExpenseCredit, currency)}"
    } else null

    val rowModifier = modifier.fillMaxWidth()

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (flat) {
                // Single icon with folder badge — same width as TransactionItem for visual consistency
                Box(contentAlignment = Alignment.BottomEnd) {
                    BrandIcon(
                        merchantName = merchantOrder.firstOrNull() ?: group.name,
                        categoryOverride = merchantCategories?.get(merchantOrder.firstOrNull() ?: ""),
                        size = Dimensions.Icon.list,
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            } else {
                GroupMerchantAvatarStack(
                    merchantNames = merchantOrder,
                    merchantCategories = merchantCategories,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (!flat) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!flat) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = itemPill,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                val displaySubtitle = if (flat) flatSubtitle else subtitle
                if (displaySubtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                if (splitLine != null) {
                    Text(
                        text = splitLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (flat) {
        Box(
            modifier = rowModifier
                .background(groupBg)
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                }
        ) { content() }
    } else {
        PennyWiseCardV2(
            modifier = rowModifier,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
            contentPadding = 0.dp
        ) {
            content()
        }
    }
}
