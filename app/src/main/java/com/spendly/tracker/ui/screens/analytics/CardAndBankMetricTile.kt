package com.spendly.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.components.PennyWiseCard
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter

@Composable
fun CardAndBankMetricTile(
    summary: CardAndBankSpendSummary,
    currency: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val formattedTotal = CurrencyFormatter.formatCurrency(summary.total, currency)
    val isLongTotal = formattedTotal.length > 14

    PennyWiseCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CARD & BANK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(Spacing.sm),
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "${summary.transactionCount} TXNS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formattedTotal,
                style = if (isLongTotal) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(
                thickness = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                PaymentModeSplitColumn(
                    modifier = Modifier.weight(1f),
                    label = "Credit card",
                    icon = Icons.Default.CreditCard,
                    amount = summary.creditTotal,
                    transactionCount = summary.creditCount,
                    currency = currency,
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = Spacing.sm),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                PaymentModeSplitColumn(
                    modifier = Modifier.weight(1f),
                    label = "Bank account",
                    icon = Icons.Default.AccountBalance,
                    amount = summary.bankTotal,
                    transactionCount = summary.bankCount,
                    currency = currency,
                )
            }

            if (summary.percentOfSpending > 0f) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "${summary.percentOfSpending.toInt()}% of spending · excludes cash",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaymentModeSplitColumn(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    amount: java.math.BigDecimal,
    transactionCount: Int,
    currency: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = CurrencyFormatter.formatCurrency(amount, currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$transactionCount txn${if (transactionCount != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
