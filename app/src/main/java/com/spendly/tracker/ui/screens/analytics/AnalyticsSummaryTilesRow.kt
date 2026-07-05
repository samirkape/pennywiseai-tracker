package com.spendly.tracker.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.spendly.tracker.presentation.common.PaymentMode
import com.spendly.tracker.presentation.common.TransactionTypeFilter
import com.spendly.tracker.ui.components.cards.SpendingBreakdownData
import com.spendly.tracker.ui.components.cards.SpendingBreakdownTile
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private sealed interface AnalyticsSummaryTileEntry {
    val key: String

    data class Outflow(
        override val key: String,
        val summary: PeriodOutflowSummary,
        val onClick: ((Set<TransactionTypeFilter>) -> Unit)?,
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

    data class SpendingBreakdown(
        override val key: String,
        val data: SpendingBreakdownData,
        val onClick: (() -> Unit)?,
    ) : AnalyticsSummaryTileEntry
}

@Composable
fun AnalyticsSummaryTilesRow(
    currency: String,
    periodOutflow: PeriodOutflowSummary?,
    investmentInsights: InvestmentInsights?,
    paymentModeBreakdown: PaymentModeBreakdown?,
    onOutflowClick: ((Set<TransactionTypeFilter>) -> Unit)? = null,
    onInvestmentClick: () -> Unit,
    onSpendingBreakdownClick: () -> Unit,
    onTileDetailClick: ((String) -> Unit)? = null,
    onOutflowBreakdownRowClick: ((TransactionTypeFilter) -> Unit)? = null,
    onTileChanged: (String) -> Unit = {},
    compactMode: Boolean = true,
    showInlineBreakdown: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val showOutflowTile = periodOutflow != null

    val tiles = buildList {
        periodOutflow?.let { outflow ->
            add(
                AnalyticsSummaryTileEntry.Outflow(
                    key = "outflow",
                    summary = outflow,
                    onClick = onOutflowClick,
                ),
            )
        }


        paymentModeBreakdown?.let { breakdown ->
            val cardAndBank = breakdown.cardAndBank
            val cash = breakdown.cash
            if (cardAndBank != null || cash != null) {
                val totalAmount = (cardAndBank?.total ?: BigDecimal.ZERO) + (cash?.total ?: BigDecimal.ZERO)
                val cardTxnCount = cardAndBank?.creditCount ?: 0
                val cashTxnCount = cash?.transactionCount ?: 0
                val creditCardAmount = CurrencyFormatter.formatCurrency(cardAndBank?.creditTotal ?: BigDecimal.ZERO, breakdown.currency)
                val creditCardTxns = cardAndBank?.creditCount ?: 0
                val bankAmount = CurrencyFormatter.formatCurrency(cardAndBank?.bankTotal ?: BigDecimal.ZERO, breakdown.currency)
                val bankTxns = cardAndBank?.bankCount ?: 0
                val cashAmount = CurrencyFormatter.formatCurrency(cash?.total ?: BigDecimal.ZERO, breakdown.currency)
                val cashTxns = cash?.transactionCount ?: 0
                val cashPercent = cash?.percentOfTotal ?: 0f
                val footerNote = if (cashPercent > 0f) {
                    "Cash is ${cashPercent.toInt()}% of spend · excl. investments"
                } else {
                    "Excl. investments"
                }

                fun pctOf(part: BigDecimal): Int =
                    if (totalAmount > BigDecimal.ZERO)
                        part.multiply(BigDecimal(100))
                            .divide(totalAmount, 0, RoundingMode.HALF_UP)
                            .toInt()
                    else 0
                val creditPct = pctOf(cardAndBank?.creditTotal ?: BigDecimal.ZERO)
                val bankPct = pctOf(cardAndBank?.bankTotal ?: BigDecimal.ZERO)
                val cashPct = pctOf(cash?.total ?: BigDecimal.ZERO)

                add(
                    AnalyticsSummaryTileEntry.SpendingBreakdown(
                        key = "spending_breakdown",
                        data = SpendingBreakdownData(
                            totalAmount = CurrencyFormatter.formatCurrency(totalAmount, breakdown.currency),
                            cardTxnCount = cardTxnCount,
                            cashTxnCount = cashTxnCount,
                            creditCardAmount = creditCardAmount,
                            creditCardTxns = creditCardTxns,
                            bankAmount = bankAmount,
                            bankTxns = bankTxns,
                            cashAmount = cashAmount,
                            cashTxns = cashTxns,
                            footerNote = footerNote,
                            deltaPercent = breakdown.deltaPercent,
                            totalTransactionCount = breakdown.totalTransactionCount,
                            creditCardPercent = creditPct,
                            bankPercent = bankPct,
                            cashPercent = cashPct,
                        ),
                        onClick = onSpendingBreakdownClick,
                    ),
                )
            }
        }

        investmentInsights?.let { insights ->
            val delta = insights.deltaPercent
            val hasRecurring = insights.recurringCount > 0
            val topMerchantName = insights.topMerchants.firstOrNull()?.name
            add(
                AnalyticsSummaryTileEntry.Metric(
                    key = "investments",
                    content = AnalyticsMetricTileContent(
                        topLabel = "INVESTED",
                        primaryValue = CurrencyFormatter.formatCurrency(insights.totalInvested, insights.currency),
                        transactionCount = insights.transactionCount,
                        countBadgeIcon = Icons.AutoMirrored.Filled.ShowChart,
                        bottomLeftLabel = if (hasRecurring) "Recurring SIPs" else "Largest single",
                        bottomLeftValue = if (hasRecurring) {
                            "${insights.recurringCount} SIP${if (insights.recurringCount != 1) "s" else ""}"
                        } else {
                            CurrencyFormatter.formatCurrency(insights.largestInvestment, insights.currency)
                        },
                        bottomLeftSuffix = if (!hasRecurring) "1 transaction" else null,
                        bottomRightCaption = "Average per txn",
                        bottomRightPill = if (insights.transactionCount > 0) {
                            val avg = insights.totalInvested.divide(
                                java.math.BigDecimal(insights.transactionCount), 2, java.math.RoundingMode.HALF_UP
                            )
                            AnalyticsTilePill.Labeled(
                                text = CurrencyFormatter.formatCurrency(avg, insights.currency),
                            )
                        } else null,
                        deltaPercent = delta,
                        txnCountSuffix = topMerchantName?.let { "· via $it" },
                    ),
                    onClick = onInvestmentClick,
                ),
            )
        }
    }

    if (tiles.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { tiles.size })
    val scope = rememberCoroutineScope()
    val tileKeys = tiles.map { it.key }

    LaunchedEffect(tileKeys) {
        pagerState.scrollToPage(0)
    }

    LaunchedEffect(pagerState.currentPage, tiles.size) {
        if (tiles.isNotEmpty()) {
            onTileChanged(tiles[pagerState.currentPage].key)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (tiles.size > 1) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xs),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tiles.forEachIndexed { index, item ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 34.dp)
                                .background(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    if (pagerState.currentPage != index) {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = item.tabLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            val indicatorPos = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val connectorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                val tabWidth = size.width / tiles.size
                val cx = (indicatorPos + 0.5f) * tabWidth
                val hw = tabWidth * 0.18f
                drawLine(
                    color = connectorColor,
                    start = Offset(cx - hw, size.height),
                    end = Offset(cx + hw, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
            pageSpacing = 0.dp,
            userScrollEnabled = tiles.size > 1,
            beyondViewportPageCount = 1,
        ) { page ->
            val tile = tiles[page]
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).absoluteValue

            val tileModifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val t = (1f - pageOffset.coerceIn(0f, 1f))
                    scaleX = lerp(0.97f, 1f, t)
                    scaleY = lerp(0.97f, 1f, t)
                    alpha = lerp(0.78f, 1f, t)
                }

            val detailNavigate = onTileDetailClick?.let { navigate -> { navigate(tile.key) } }

            when (tile) {
                is AnalyticsSummaryTileEntry.Outflow -> PeriodOutflowMetricTile(
                    outflow = tile.summary,
                    currency = tile.summary.currency,
                    onClick = tile.onClick,
                    compactMode = compactMode,
                    showInlineBreakdown = showInlineBreakdown,
                    showMetricFilter = showInlineBreakdown,
                    onDetailNavigate = detailNavigate,
                    onBreakdownRowClick = onOutflowBreakdownRowClick,
                    modifier = tileModifier,
                )
                is AnalyticsSummaryTileEntry.Metric -> AnalyticsMetricTile(
                    content = tile.content,
                    onClick = detailNavigate ?: tile.onClick,
                    modifier = tileModifier,
                )
                is AnalyticsSummaryTileEntry.CardAndBank -> CardAndBankMetricTile(
                    summary = tile.summary,
                    currency = currency,
                    onClick = detailNavigate ?: tile.onClick,
                    modifier = tileModifier,
                )
                is AnalyticsSummaryTileEntry.SpendingBreakdown -> SpendingBreakdownTile(
                    data = tile.data,
                    onClick = detailNavigate ?: tile.onClick,
                    modifier = tileModifier,
                )
            }
        }
    }
}

private fun AnalyticsSummaryTileEntry.tabLabel(): String = when (this) {
    is AnalyticsSummaryTileEntry.Outflow -> "Outflow"
    is AnalyticsSummaryTileEntry.CardAndBank -> "Card+Bank"
    is AnalyticsSummaryTileEntry.SpendingBreakdown -> "Spending"
    is AnalyticsSummaryTileEntry.Metric -> when (key) {
        "spending" -> "Spending"
        "payment_cash" -> "Cash"
        "investments" -> "Invested"
        else -> "Metric"
    }
}
