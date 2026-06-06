package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.pennywiseai.tracker.presentation.common.PaymentMode
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
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
    onOutflowClick: ((Set<TransactionTypeFilter>) -> Unit)? = null,
    onInvestmentClick: () -> Unit,
    onCardAndBankClick: () -> Unit,
    onCashClick: () -> Unit,
    onTileDetailClick: ((String) -> Unit)? = null,
    onOutflowBreakdownRowClick: ((TransactionTypeFilter) -> Unit)? = null,
    onTileChanged: (String) -> Unit = {},
    compactMode: Boolean = true,
    showInlineBreakdown: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val showOutflowTile = periodOutflow != null
    val spendingTopLabel = if (showOutflowTile) "SPENDING" else "TOTAL"

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
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                itemsIndexed(tiles, key = { _, item -> item.key }) { index, item ->
                    FilterChip(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        label = {
                            Text(
                                text = item.tabLabel(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
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
            }
        }
    }
}

private fun AnalyticsSummaryTileEntry.tabLabel(): String = when (this) {
    is AnalyticsSummaryTileEntry.Outflow -> "Outflow"
    is AnalyticsSummaryTileEntry.CardAndBank -> "Card+Bank"
    is AnalyticsSummaryTileEntry.Metric -> when (key) {
        "spending" -> "Spending"
        "payment_cash" -> "Cash"
        "investments" -> "Invested"
        else -> "Metric"
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
