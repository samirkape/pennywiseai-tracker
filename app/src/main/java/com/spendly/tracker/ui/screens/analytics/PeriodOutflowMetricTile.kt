package com.spendly.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.presentation.common.TransactionTypeFilter
import com.spendly.tracker.presentation.common.perDayAverage
import com.spendly.tracker.ui.components.AnimatedCurrencyText
import com.spendly.tracker.ui.components.currencyAnnotated
import com.spendly.tracker.ui.components.headlineMoneySize
import com.spendly.tracker.ui.components.inlineMoneySize
import com.spendly.tracker.ui.theme.expense
import com.spendly.tracker.ui.theme.success
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

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
    /** Length of the period the outflow covers, used for the per-day figure. 0 = unknown. */
    periodDays: Int = 0,
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

    val segmentTotal = metricRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount }
    val formattedTotal = CurrencyFormatter.formatCurrency(outflow.total, currency)
    val headlineSp = headlineMoneySize(formattedTotal.length)
    val roundedDelta = outflow.deltaPercent?.roundToInt()
    val average = if (outflow.transactionCount > 0) {
        outflow.total.divide(BigDecimal(outflow.transactionCount), 2, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO
    val perDay = perDayAverage(outflow.total, periodDays)

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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // ── Header: the card's single tap target. The breakdown row below owns its own targets,
            // so the two zones never overlap or double-fire.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (cardClick != null) Modifier.clickable(onClick = cardClick) else Modifier),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "TOTAL OUTFLOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cardClick != null) {
                        // Disclosure affordance so the tappable zone is legible at a glance.
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stepped size + tabular figures: long totals shrink instead of being ellipsized,
                // and digits don't jitter when the value tweens between periods.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AnimatedCurrencyText(
                        text = currencyAnnotated(formattedTotal, headlineSp),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = headlineSp.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (roundedDelta != null) {
                        OutflowDeltaBadge(roundedDelta)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val insight = buildString {
                    append("${outflow.transactionCount} transaction${if (outflow.transactionCount != 1) "s" else ""}")
                    if (outflow.transactionCount > 0) {
                        append(" · ${CurrencyFormatter.formatCurrency(average, currency)} average")
                    }
                    if (perDay != null) {
                        append(" · ${CurrencyFormatter.formatCurrency(perDay, currency)}/day")
                    }
                }
                Text(
                    text = insight,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(14.dp))

            // ── Composition bar: proportions at a glance; the columns below carry exact values.
            if (segmentTotal > BigDecimal.ZERO) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    metricRows.forEach { row ->
                        val share = row.amount
                            .divide(segmentTotal, 4, RoundingMode.HALF_UP)
                            .toFloat()
                            .coerceAtLeast(0.001f)
                        Box(
                            modifier = Modifier
                                .weight(share)
                                .fillMaxHeight()
                                .background(row.option.accentColor()),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Breakdown row: exact amounts + share + counts, each column a drill-down target.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                metricRows.forEachIndexed { index, row ->
                    val percent = if (segmentTotal > BigDecimal.ZERO)
                        row.amount.multiply(BigDecimal(100))
                            .divide(segmentTotal, 0, RoundingMode.HALF_UP)
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

/**
 * Delta badge for the aggregate outflow figure. Deliberately neutral: an increase can mean healthy
 * investing, so the card's headline number never gets a good/bad judgement colour.
 */
@Composable
private fun OutflowDeltaBadge(roundedDelta: Int) {
    val flat = roundedDelta == 0
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = when {
                    flat -> Icons.Default.Remove
                    roundedDelta > 0 -> Icons.AutoMirrored.Filled.TrendingUp
                    else -> Icons.AutoMirrored.Filled.TrendingDown
                },
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = color,
            )
            Text(
                text = "${if (roundedDelta > 0) "+" else ""}$roundedDelta% vs last",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
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
    val amount = CurrencyFormatter.formatCurrency(row.amount, currency)
    val amountSp = inlineMoneySize(amount.length)
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                imageVector = row.option.icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = row.option.accentColor(),
            )
            Text(
                text = row.option.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Size ladder instead of ellipsis — money is never truncated.
        Text(
            text = currencyAnnotated(amount, amountSp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = amountSp.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
        Text(
            text = "$percentOfTotal% · ${row.transactionCount} txn${if (row.transactionCount != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun OutflowMetricOption.accentColor(): Color = when (this) {
    OutflowMetricOption.SPENDING -> MaterialTheme.colorScheme.expense
    OutflowMetricOption.INVESTED -> MaterialTheme.colorScheme.success
    OutflowMetricOption.CC_PAYMENT -> MaterialTheme.colorScheme.primary
}

private enum class OutflowMetricOption(val label: String, val icon: ImageVector) {
    SPENDING("Spend", Icons.Default.Receipt),
    INVESTED("Invested", Icons.Default.AccountBalance),
    CC_PAYMENT("CC Pay", Icons.Default.CreditCard),
}

private data class OutflowMetricRow(val option: OutflowMetricOption, val amount: BigDecimal, val transactionCount: Int)
