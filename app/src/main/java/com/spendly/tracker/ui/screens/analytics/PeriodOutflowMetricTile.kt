package com.spendly.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.presentation.common.TransactionTypeFilter
import com.spendly.tracker.ui.theme.expense_dark
import com.spendly.tracker.ui.theme.expense_light
import com.spendly.tracker.ui.theme.income_dark
import com.spendly.tracker.ui.theme.income_light
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun PeriodOutflowMetricTile(
    outflow: PeriodOutflowSummary,
    currency: String,
    onClick: ((selectedTypes: Set<TransactionTypeFilter>) -> Unit)?,
    compactMode: Boolean = true,
    showInlineBreakdown: Boolean = true,
    showMetricFilter: Boolean = true,
    onDetailNavigate: (() -> Unit)? = null,
    onBreakdownRowClick: ((TransactionTypeFilter) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val metricRows = buildList {
        add(OutflowMetricRow(OutflowMetricOption.SPENDING, outflow.spending, outflow.spendingTransactionCount))
        if (outflow.invested > BigDecimal.ZERO) {
            add(OutflowMetricRow(OutflowMetricOption.INVESTED, outflow.invested, outflow.investmentTransactionCount))
        }
        if (outflow.ccBillPayment > BigDecimal.ZERO) {
            add(OutflowMetricRow(OutflowMetricOption.CC_PAYMENT, outflow.ccBillPayment, outflow.ccBillPaymentTransactionCount))
        }
    }

    val formattedTotal = CurrencyFormatter.formatCurrency(outflow.total, currency)
    val isLongTotal = formattedTotal.length > 14
    val delta = outflow.deltaPercent

    val cardClick: (() -> Unit)? = onClick?.let { handler ->
        {
            handler(metricRows.map {
                when (it.option) {
                    OutflowMetricOption.SPENDING -> TransactionTypeFilter.EXPENSE
                    OutflowMetricOption.INVESTED -> TransactionTypeFilter.INVESTMENT
                    OutflowMetricOption.CC_PAYMENT -> TransactionTypeFilter.CC_BILL_PAYMENT
                }
            }.toSet())
        }
    } ?: onDetailNavigate

    Card(
        modifier = modifier.fillMaxWidth().then(
            if (cardClick != null) Modifier.clickable(onClick = cardClick) else Modifier
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // .card-label
            Text(
                text = "TOTAL OUTFLOW",
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
                Text(
                    text = formattedTotal,
                    style = if (isLongTotal) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (delta != null) {
                    val isUp = delta >= 0f
                    val isDark = isSystemInDarkTheme()
                    // outflow up = bad (red), outflow down = good (green)
                    val fg = if (!isUp) {
                        if (isDark) income_dark else income_light
                    } else {
                        if (isDark) expense_dark else expense_light
                    }
                    val bg = fg.copy(alpha = 0.15f)
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
            Text(
                text = "${outflow.transactionCount} transaction${if (outflow.transactionCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // .divider
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(14.dp))

            // .breakdown-row 3-col
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                metricRows.forEachIndexed { index, row ->
                    val percent = if (outflow.total > BigDecimal.ZERO)
                        row.amount.multiply(BigDecimal(100))
                            .divide(outflow.total, 0, RoundingMode.HALF_UP)
                            .toInt()
                    else 0
                    BreakdownItem(
                        row = row,
                        currency = currency,
                        percentOfTotal = percent,
                        modifier = Modifier.weight(1f),
                        onClick = onBreakdownRowClick?.let { h ->
                            {
                                h(when (row.option) {
                                    OutflowMetricOption.SPENDING -> TransactionTypeFilter.EXPENSE
                                    OutflowMetricOption.INVESTED -> TransactionTypeFilter.INVESTMENT
                                    OutflowMetricOption.CC_PAYMENT -> TransactionTypeFilter.CC_BILL_PAYMENT
                                })
                            }
                        },
                    )
                    if (index != metricRows.lastIndex) {
                        VerticalDivider(
                            modifier = Modifier.height(48.dp).padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownItem(
    row: OutflowMetricRow,
    currency: String,
    percentOfTotal: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(imageVector = row.option.icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = row.option.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = CurrencyFormatter.formatCurrency(row.amount, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = "$percentOfTotal%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class OutflowMetricOption(val label: String, val icon: ImageVector) {
    SPENDING("Spend", Icons.Default.Receipt),
    INVESTED("Invested", Icons.Default.AccountBalance),
    CC_PAYMENT("CC Pay", Icons.Default.CreditCard),
}

private data class OutflowMetricRow(val option: OutflowMetricOption, val amount: BigDecimal, val transactionCount: Int)
