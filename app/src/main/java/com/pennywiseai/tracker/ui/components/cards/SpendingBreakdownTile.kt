package com.pennywiseai.tracker.ui.components.cards

// HTML reference: spending_invested_tiles.html  .hero-card  "Spending" tab
// .card-label "SPENDING"
// .amount-row : amount + delta-badge
// .txn-count "37 transactions"
// .divider
// .breakdown-row  Card | Bank | Cash  (icon+label / amount / txns)
// .cash-note  ℹ info text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

data class SpendingBreakdownData(
    val totalAmount: String,
    val cardTxnCount: Int,
    val cashTxnCount: Int,
    val creditCardAmount: String,
    val creditCardTxns: Int,
    val bankAmount: String,
    val bankTxns: Int,
    val cashAmount: String,
    val cashTxns: Int,
    val footerNote: String,
    val deltaPercent: Float? = null,
    val totalTransactionCount: Int = 0,
    val creditCardPercent: Int = 0,
    val bankPercent: Int = 0,
    val cashPercent: Int = 0,
)

@Composable
fun SpendingBreakdownTile(
    data: SpendingBreakdownData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // .card-label
            Text(
                text = "SPENDING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // .amount-row : amount + delta-badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val isLong = data.totalAmount.length > 14
                Text(
                    text = data.totalAmount,
                    style = if (isLong) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val delta = data.deltaPercent
                if (delta != null) {
                    val isUp = delta >= 0f
                    val bg = if (!isUp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    val fg = if (!isUp) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = fg,
                        )
                        Text(
                            text = "${if (isUp) "+" else ""}${delta.toInt()}% vs last",
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // .txn-count
            if (data.totalTransactionCount > 0) {
                Text(
                    text = "${data.totalTransactionCount} transaction${if (data.totalTransactionCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // .divider
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(14.dp))

            // .breakdown-row  Card | Bank | Cash
            Row(modifier = Modifier.fillMaxWidth()) {
                BreakdownCol(
                    icon = Icons.Outlined.CreditCard,
                    label = "Card",
                    amount = data.creditCardAmount,
                    percent = data.creditCardPercent,
                    modifier = Modifier.weight(1f),
                    onClick = onCardClick,
                )
                VerticalDivider(modifier = Modifier.height(48.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                BreakdownCol(
                    icon = Icons.Outlined.AccountBalance,
                    label = "Bank",
                    amount = data.bankAmount,
                    percent = data.bankPercent,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(modifier = Modifier.height(48.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                BreakdownCol(
                    icon = Icons.Outlined.Wallet,
                    label = "Cash",
                    amount = data.cashAmount,
                    percent = data.cashPercent,
                    modifier = Modifier.weight(1f),
                )
            }

            // .cash-note  ℹ
            if (data.footerNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = data.footerNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownCol(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    percent: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = "$percent%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

