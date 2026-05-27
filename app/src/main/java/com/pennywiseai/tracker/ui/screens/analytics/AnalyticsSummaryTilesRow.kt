package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.presentation.common.PaymentMode
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

private sealed interface AnalyticsSummaryTileEntry {
    val key: String

    data class Outflow(
        override val key: String,
        val summary: PeriodOutflowSummary,
        val onClick: (() -> Unit)?,
    ) : AnalyticsSummaryTileEntry

    data class Metric(
        override val key: String,
        val content: AnalyticsMetricTileContent,
        val onClick: (() -> Unit)?,
    ) : AnalyticsSummaryTileEntry

    data class CardAndBank(
        override val key: String,
        val summary: CardAndBankSpendSummary,
        val onClick: (() -> Unit)?,
    ) : AnalyticsSummaryTileEntry
}

@Composable
fun AnalyticsSummaryTilesRow(
    spendingTotal: BigDecimal,
    spendingTransactionCount: Int,
    spendingAverage: BigDecimal,
    spendingTopCategory: String?,
    spendingTopCategoryPercentage: Float,
    currency: String,
    periodOutflow: PeriodOutflowSummary?,
    investmentInsights: InvestmentInsights?,
    paymentModeBreakdown: PaymentModeBreakdown?,
    onSpendingClick: () -> Unit,
    onOutflowClick: (() -> Unit)? = null,
    onInvestmentClick: () -> Unit,
    onCardAndBankClick: () -> Unit,
    onCashClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val tileWidth = screenWidth * 0.88f
    val showOutflowTile = periodOutflow != null && periodOutflow.invested > BigDecimal.ZERO
    val spendingTopLabel = if (showOutflowTile) "SPENDING" else "TOTAL"

    val tiles = buildList {
        periodOutflow?.takeIf { it.invested > BigDecimal.ZERO }?.let { outflow ->
            add(
                AnalyticsSummaryTileEntry.Outflow(
                    key = "outflow",
                    summary = outflow,
                    onClick = onOutflowClick,
                ),
            )
        }

        if (spendingTotal > BigDecimal.ZERO || spendingTransactionCount > 0) {
            add(
                AnalyticsSummaryTileEntry.Metric(
                    key = "spending",
                    content = AnalyticsMetricTileContent(
                        topLabel = spendingTopLabel,
                        primaryValue = CurrencyFormatter.formatCurrency(spendingTotal, currency),
                        transactionCount = spendingTransactionCount,
                        countBadgeIcon = Icons.Default.Receipt,
                        bottomLeftLabel = "AVERAGE",
                        bottomLeftValue = CurrencyFormatter.formatCurrency(
                            if (spendingTransactionCount > 0) spendingAverage else BigDecimal.ZERO,
                            currency,
                        ),
                        bottomLeftSuffix = " /day",
                        bottomRightCaption = if (spendingTopCategory != null && spendingTopCategoryPercentage > 0) {
                            "${spendingTopCategoryPercentage.toInt()}% of total"
                        } else {
                            null
                        },
                        bottomRightPill = spendingTopCategory?.takeIf {
                            spendingTopCategoryPercentage > 0
                        }?.let { AnalyticsTilePill.Category(it) },
                    ),
                    onClick = onSpendingClick,
                ),
            )
        }

        paymentModeBreakdown?.cardAndBank?.let { summary ->
            add(
                AnalyticsSummaryTileEntry.CardAndBank(
                    key = "card_and_bank",
                    summary = summary,
                    onClick = onCardAndBankClick,
                ),
            )
        }

        paymentModeBreakdown?.cash?.let { cash ->
            add(cashPaymentModeTile(cash, paymentModeBreakdown.currency, onCashClick))
        }

        investmentInsights?.let { insights ->
            val delta = insights.deltaPercent
            val hasRecurring = insights.recurringCount > 0
            add(
                AnalyticsSummaryTileEntry.Metric(
                    key = "investments",
                    content = AnalyticsMetricTileContent(
                        topLabel = "INVESTED",
                        primaryValue = CurrencyFormatter.formatCurrency(insights.totalInvested, insights.currency),
                        transactionCount = insights.transactionCount,
                        countBadgeIcon = Icons.AutoMirrored.Filled.ShowChart,
                        bottomLeftLabel = if (hasRecurring) "RECURRING" else "LARGEST",
                        bottomLeftValue = if (hasRecurring) {
                            "${insights.recurringCount} SIP${if (insights.recurringCount != 1) "s" else ""}"
                        } else {
                            CurrencyFormatter.formatCurrency(insights.largestInvestment, insights.currency)
                        },
                        bottomRightCaption = when {
                            delta != null -> {
                                val sign = if (delta >= 0f) "+" else ""
                                "$sign${delta.toInt()}% vs last period"
                            }
                            insights.topCategory != null && insights.topCategoryPercentage > 0 ->
                                "${insights.topCategoryPercentage.toInt()}% of invested"
                            else -> null
                        },
                        bottomRightPill = insights.topCategory?.takeIf {
                            insights.topCategoryPercentage > 0
                        }?.let { AnalyticsTilePill.Category(it) },
                    ),
                    onClick = onInvestmentClick,
                ),
            )
        }
    }

    if (tiles.isEmpty()) return

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tileKeys = tiles.map { it.key }

    LaunchedEffect(tileKeys) {
        listState.scrollToItem(0)
    }

    val showNavButtons = tiles.size > 1

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNavButtons) {
            IconButton(
                onClick = {
                    scope.launch {
                        val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                },
                enabled = listState.canScrollBackward,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous summary",
                    tint = if (listState.canScrollBackward) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = if (showNavButtons) {
                PaddingValues(horizontal = Spacing.xs)
            } else {
                PaddingValues(end = Dimensions.Padding.content)
            },
        ) {
            items(tiles, key = { it.key }) { tile ->
                when (tile) {
                    is AnalyticsSummaryTileEntry.Outflow -> PeriodOutflowMetricTile(
                        outflow = tile.summary,
                        currency = tile.summary.currency,
                        onClick = tile.onClick,
                        modifier = Modifier.width(tileWidth),
                    )
                    is AnalyticsSummaryTileEntry.Metric -> AnalyticsMetricTile(
                        content = tile.content,
                        onClick = tile.onClick,
                        modifier = Modifier.width(tileWidth),
                    )
                    is AnalyticsSummaryTileEntry.CardAndBank -> CardAndBankMetricTile(
                        summary = tile.summary,
                        currency = currency,
                        onClick = tile.onClick,
                        modifier = Modifier.width(tileWidth),
                    )
                }
            }
        }

        if (showNavButtons) {
            IconButton(
                onClick = {
                    scope.launch {
                        val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(tiles.lastIndex)
                        listState.animateScrollToItem(target)
                    }
                },
                enabled = listState.canScrollForward,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next summary",
                    tint = if (listState.canScrollForward) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

private fun cashPaymentModeTile(
    stat: PaymentModeStat,
    currency: String,
    onCashClick: () -> Unit,
): AnalyticsSummaryTileEntry.Metric {
    val average = if (stat.transactionCount > 0) {
        stat.total.divide(BigDecimal(stat.transactionCount), 2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    return AnalyticsSummaryTileEntry.Metric(
        key = "payment_cash",
        content = AnalyticsMetricTileContent(
            topLabel = PaymentMode.CASH.label.uppercase(),
            primaryValue = CurrencyFormatter.formatCurrency(stat.total, currency),
            transactionCount = stat.transactionCount,
            countBadgeIcon = Icons.Default.Payments,
            bottomLeftLabel = "AVERAGE",
            bottomLeftValue = CurrencyFormatter.formatCurrency(average, currency),
            bottomLeftSuffix = " /txn",
            bottomRightCaption = "${stat.percentOfTotal.toInt()}% of spend",
            bottomRightPill = AnalyticsTilePill.Labeled(
                text = PaymentMode.CASH.label,
                icon = Icons.Default.Payments,
            ),
        ),
        onClick = onCashClick,
    )
}
