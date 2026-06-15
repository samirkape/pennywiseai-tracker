package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class AnalyticsOverviewTab(val label: String) {
    OUTFLOW("Outflow"),
    SPENDING("Spending"),
    INVESTED("Invested"),
}

private data class AnalyticsHeroMetric(
    val label: String,
    val amount: String,
    val subLabel: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

// ─────────────────────────────────────────────────────────────────────────────
// Period chip row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferencePeriodChipRow(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit,
    onCustomSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = remember {
        listOf(
            TimePeriod.THIS_MONTH,
            TimePeriod.CALENDAR_MONTH,
            TimePeriod.LAST_MONTH,
            TimePeriod.CUSTOM,
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(periods.size) { index ->
            val period = periods[index]
            val label = when (period) {
                TimePeriod.THIS_MONTH -> "Pay month"
                TimePeriod.CALENDAR_MONTH -> "Calendar month"
                TimePeriod.LAST_MONTH -> "Last month"
                TimePeriod.CUSTOM -> "Custom"
                else -> period.label
            }
            val selected = selectedPeriod == period

            Surface(
                onClick = {
                    if (period == TimePeriod.CUSTOM) onCustomSelected() else onPeriodSelected(period)
                },
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 0.5.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date navigator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferenceDateNavigator(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReferenceNavButton(onClick = onPrevious, icon = Icons.AutoMirrored.Filled.ArrowForward, rotationDegrees = 180f)

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )

        ReferenceNavButton(onClick = onNext, icon = Icons.AutoMirrored.Filled.ArrowForward, enabled = canGoNext, rotationDegrees = 0f)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Segmented tab control
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferenceOverviewTabs(
    selectedTab: AnalyticsOverviewTab,
    onTabSelected: (AnalyticsOverviewTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AnalyticsOverviewTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val bgColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainer,
                animationSpec = tween(200),
                label = "tabBg",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bgColor)
                    .then(
                        if (selected) Modifier.border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(9.dp),
                        ) else Modifier
                    )
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    fontSize = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero summary card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferenceHeroCard(
    selectedTab: AnalyticsOverviewTab,
    periodOutflow: PeriodOutflowSummary?,
    investmentInsights: InvestmentInsights?,
    paymentModeBreakdown: PaymentModeBreakdown?,
    currency: String,
    onMetricClick: ((metricIndex: Int) -> Unit)? = null,
    onTotalClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hero = remember(selectedTab, periodOutflow, investmentInsights, paymentModeBreakdown, currency) {
        when (selectedTab) {
            AnalyticsOverviewTab.OUTFLOW -> {
                val s = periodOutflow ?: return@remember null
                AnalyticsHeroState(
                    label = "TOTAL OUTFLOW",
                    amount = CurrencyFormatter.formatCurrency(s.total, currency),
                    delta = s.deltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.deltaPercent ?: 0f) >= 0f,
                    transactionCount = "${s.transactionCount} transactions",
                    metrics = listOf(
                        AnalyticsHeroMetric("Spend", CurrencyFormatter.formatCurrency(s.spending, currency), "${s.spendingTransactionCount} txns", Icons.Default.Receipt),
                        AnalyticsHeroMetric("Invested", CurrencyFormatter.formatCurrency(s.invested, currency), "${s.investmentTransactionCount} txns", Icons.Default.ShowChart),
                        AnalyticsHeroMetric("CC Pay", CurrencyFormatter.formatCurrency(s.ccBillPayment, currency), "${s.ccBillPaymentTransactionCount} txns", Icons.Default.CreditCard),
                    ),
                )
            }
            AnalyticsOverviewTab.SPENDING -> {
                val s = periodOutflow ?: return@remember null
                val cb = paymentModeBreakdown?.cardAndBank
                val cash = paymentModeBreakdown?.cash
                AnalyticsHeroState(
                    label = "TOTAL SPENDING",
                    amount = CurrencyFormatter.formatCurrency(s.spending, currency),
                    delta = s.spendingDeltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.spendingDeltaPercent ?: 0f) >= 0f,
                    transactionCount = "${s.spendingTransactionCount} transactions",
                    metrics = listOf(
                        AnalyticsHeroMetric("Card", CurrencyFormatter.formatCurrency(cb?.creditTotal ?: BigDecimal.ZERO, currency), "${cb?.creditCount ?: 0} txns", Icons.Default.CreditCard),
                        AnalyticsHeroMetric("Bank", CurrencyFormatter.formatCurrency(cb?.bankTotal ?: BigDecimal.ZERO, currency), "${cb?.bankCount ?: 0} txns", Icons.Default.AccountBalance),
                        AnalyticsHeroMetric("Cash", CurrencyFormatter.formatCurrency(cash?.total ?: BigDecimal.ZERO, currency), "${cash?.transactionCount ?: 0} txns", Icons.Default.Wallet),
                    ),
                )
            }
            AnalyticsOverviewTab.INVESTED -> {
                val s = investmentInsights ?: return@remember null
                val avg = if (s.transactionCount > 0)
                    CurrencyFormatter.formatCurrency(s.totalInvested.divide(BigDecimal(s.transactionCount), 2, RoundingMode.HALF_UP), currency)
                else CurrencyFormatter.formatCurrency(BigDecimal.ZERO, currency)
                AnalyticsHeroState(
                    label = "TOTAL INVESTED",
                    amount = CurrencyFormatter.formatCurrency(s.totalInvested, currency),
                    delta = s.deltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.deltaPercent ?: 0f) >= 0f,
                    transactionCount = "${s.transactionCount} transactions",
                    metrics = listOf(
                        AnalyticsHeroMetric("SIPs", s.recurringCount.toString(), if (s.recurringCount == 1) "recurring plan" else "recurring plans", Icons.Default.Payments),
                        AnalyticsHeroMetric("Largest", CurrencyFormatter.formatCurrency(s.largestInvestment, currency), "1 transaction", Icons.Default.ShowChart),
                        AnalyticsHeroMetric("Average", avg, "per txn", Icons.Default.Receipt),
                    ),
                )
            }
        }
    } ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // Label
            Text(
                text = hero.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.1.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Amount + delta badge on same row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onTotalClick != null) Modifier.clickable(onClick = onTotalClick) else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hero.amount,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                hero.delta?.let { deltaText ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (hero.deltaIncreasing) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = deltaText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = hero.transactionCount,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(14.dp))

            // Breakdown row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                hero.metrics.forEachIndexed { index, metric ->
                    HeroMetricColumn(
                        metric = metric,
                        modifier = Modifier.weight(1f),
                        onClick = onMetricClick?.let { handler -> { handler(index) } },
                    )
                    if (index != hero.metrics.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(52.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Balance trend card (compact custom chart)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferenceTrendCard(
    balanceHistory: List<BalancePoint>,
    modifier: Modifier = Modifier,
) {
    if (balanceHistory.size < 2) return

    val sortedHistory = remember(balanceHistory) { balanceHistory.sortedBy { it.timestamp } }
    val values = remember(sortedHistory) { sortedHistory.map { it.balance.toDouble() } }
    val footerLabels = remember(sortedHistory) { buildFooterLabels(sortedHistory) }
    val color = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val innerDotColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(color, CircleShape),
                )
                Text(
                    text = "Spend Trend",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Canvas chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val lPad = 4f; val rPad = 4f; val tPad = 8f; val bPad = 12f
                val dw = size.width - lPad - rPad
                val dh = size.height - tPad - bPad
                val minV = values.minOrNull() ?: 0.0
                val maxV = values.maxOrNull() ?: 0.0
                val range = if (maxV - minV <= 0.0) 1.0 else maxV - minV

                fun pt(i: Int): Offset {
                    val x = if (sortedHistory.lastIndex == 0) lPad + dw / 2f
                    else lPad + dw * (i.toFloat() / sortedHistory.lastIndex)
                    val y = tPad + dh * (1f - ((values[i] - minV) / range).toFloat())
                    return Offset(x, y)
                }

                val linePath = Path().apply {
                    val p0 = pt(0); moveTo(p0.x, p0.y)
                    // Cubic catmull-rom through points for a smooth curve
                    for (i in 1..sortedHistory.lastIndex) {
                        val prev = pt((i - 1).coerceAtLeast(0))
                        val cur = pt(i)
                        val next = pt((i + 1).coerceAtMost(sortedHistory.lastIndex))
                        val prev2 = pt((i - 2).coerceAtLeast(0))
                        val cp1x = prev.x + (cur.x - prev2.x) / 6f
                        val cp1y = prev.y + (cur.y - prev2.y) / 6f
                        val cp2x = cur.x - (next.x - prev.x) / 6f
                        val cp2y = cur.y - (next.y - prev.y) / 6f
                        cubicTo(cp1x, cp1y, cp2x, cp2y, cur.x, cur.y)
                    }
                }

                val fillPath = Path().apply {
                    val first = pt(0)
                    moveTo(first.x, size.height - bPad)
                    lineTo(first.x, first.y)
                    for (i in 1..sortedHistory.lastIndex) {
                        val prev = pt((i - 1).coerceAtLeast(0))
                        val cur = pt(i)
                        val next = pt((i + 1).coerceAtMost(sortedHistory.lastIndex))
                        val prev2 = pt((i - 2).coerceAtLeast(0))
                        val cp1x = prev.x + (cur.x - prev2.x) / 6f
                        val cp1y = prev.y + (cur.y - prev2.y) / 6f
                        val cp2x = cur.x - (next.x - prev.x) / 6f
                        val cp2y = cur.y - (next.y - prev.y) / 6f
                        cubicTo(cp1x, cp1y, cp2x, cp2y, cur.x, cur.y)
                    }
                    val last = pt(sortedHistory.lastIndex)
                    lineTo(last.x, size.height - bPad)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.04f), color.copy(alpha = 0f)),
                        startY = 0f, endY = size.height,
                    ),
                )
                drawPath(path = linePath, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                // Highlight first and last point
                listOf(0, sortedHistory.lastIndex).forEach { i ->
                    val p = pt(i)
                    drawCircle(color = color, radius = 4.5f, center = p)
                    drawCircle(color = innerDotColor, radius = 2f, center = p)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Footer labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                footerLabels.forEachIndexed { index, lbl ->
                    Text(
                        text = lbl,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            footerLabels.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top merchants card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferenceMerchantCard(
    merchants: List<MerchantData>,
    currency: String,
    onMerchantClick: (MerchantData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sorted = remember(merchants) { merchants.sortedByDescending { it.amount } }
    val visible = if (expanded) sorted else sorted.take(3)
    val hiddenCount = (sorted.size - visible.size).coerceAtLeast(0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column {
            visible.forEachIndexed { index, merchant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMerchantClick(merchant) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MerchantAvatar(merchant.name)

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = merchant.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = buildString {
                                append(merchant.transactionCount)
                                append(if (merchant.transactionCount == 1) " txn" else " txns")
                                val category = if (merchant.isSubscription) "Subscription" else null
                                if (category != null) append(" · $category")
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = CurrencyFormatter.formatCurrency(merchant.amount, currency),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (index != visible.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    )
                }
            }

            if (hiddenCount > 0 || expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "Show less" else "$hiddenCount more merchants",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroMetricColumn(
    metric: AnalyticsHeroMetric,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = metric.icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = metric.label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = metric.amount,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = metric.subLabel,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReferenceNavButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    rotationDegrees: Float = 0f,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer(rotationZ = rotationDegrees),
            )
        }
    }
}

@Composable
private fun MerchantAvatar(merchantName: String) {
    val initials = remember(merchantName) {
        merchantName
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { merchantName.take(2).uppercase() }
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class AnalyticsHeroState(
    val label: String,
    val amount: String,
    val delta: String?,
    val deltaIncreasing: Boolean,
    val transactionCount: String,
    val metrics: List<AnalyticsHeroMetric>,
)

private fun formatDelta(deltaPercent: Float): String {
    val absolute = abs(deltaPercent).toInt()
    val prefix = if (deltaPercent > 0f) "+" else "-"
    return "$prefix$absolute% vs last"
}

private fun buildFooterLabels(points: List<BalancePoint>): List<String> {
    if (points.isEmpty()) return emptyList()
    val indices = listOf(0, points.lastIndex / 3, (points.lastIndex * 2) / 3, points.lastIndex)
        .distinct().sorted()
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("dd MMM")
    return indices.map { i ->
        val date = points[i].timestamp.toLocalDate()
        if (date == today) "Today" else date.format(fmt)
    }
}

