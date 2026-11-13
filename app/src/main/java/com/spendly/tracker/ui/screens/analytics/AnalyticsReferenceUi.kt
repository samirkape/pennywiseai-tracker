package com.spendly.tracker.ui.screens.analytics

import com.spendly.tracker.ui.theme.Dimensions
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.presentation.common.TimePeriod
import com.spendly.tracker.ui.components.BalancePoint
import com.spendly.tracker.ui.components.currencyAnnotated
import com.spendly.tracker.ui.components.headlineMoneySize
import com.spendly.tracker.ui.components.inlineMoneySize
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.expense
import com.spendly.tracker.ui.theme.success
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AnalyticsOverviewTab(val label: String) {
    SPENDING("Spending"),
    OUTFLOW("Outflow"),
    INVESTED("Invested"),
    INCOME("Income"),
}

/** How the delta badge should be coloured: judgment (good/bad) or purely informational. */
private enum class HeroDeltaTone { NEUTRAL, GOOD, BAD }

/** Semantic accent for a composition segment; resolved against the ColorScheme at render time. */
private enum class HeroAccent { EXPENSE, SUCCESS, PRIMARY, SECONDARY, TERTIARY }

private data class AnalyticsHeroMetric(
    val label: String,
    val amount: String,
    val subLabel: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** Handed back to the caller's onMetricClick; survives filtered-out zero rows. */
    val sourceIndex: Int,
    /** 0f..1f share of the composition bar; 0f when the metric is a stat rather than a part. */
    val share: Float = 0f,
    val accent: HeroAccent = HeroAccent.PRIMARY,
)

// ─────────────────────────────────────────────────────────────────────────────
// Period chip row
// ─────────────────────────��───────────────────────────────────────────────────

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
            TimePeriod.LAST_3_MONTHS,
            TimePeriod.LAST_6_MONTHS,
            TimePeriod.THIS_YEAR,
            TimePeriod.CURRENT_FY,
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
                TimePeriod.LAST_3_MONTHS -> "Last 3 months"
                TimePeriod.LAST_6_MONTHS -> "Last 6 months"
                TimePeriod.THIS_YEAR -> "This year"
                TimePeriod.CURRENT_FY -> "This FY"
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

// ─────────────────────────────────────────────────────��───────────────────────
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )

        ReferenceNavButton(onClick = onNext, icon = Icons.AutoMirrored.Filled.ArrowForward, enabled = canGoNext, rotationDegrees = 0f)
    }
}

// ──────────��──────────────────────────────────────────────────────────────────
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
                animationSpec = tween(Dimensions.Animation.quick),
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

// ───────────────────────────────────────────────────��─────────────────────────
// Hero summary card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsReferenceHeroCard(
    selectedTab: AnalyticsOverviewTab,
    periodOutflow: PeriodOutflowSummary?,
    investmentInsights: InvestmentInsights?,
    paymentModeBreakdown: PaymentModeBreakdown?,
    incomeSummary: PeriodIncomeSummary?,
    currency: String,
    onMetricClick: ((metricIndex: Int) -> Unit)? = null,
    onTotalClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hero = remember(selectedTab, periodOutflow, investmentInsights, paymentModeBreakdown, incomeSummary, currency) {
        // Outflow mixes spending (up = bad) with investing (up = good), so the aggregate is reported
        // neutrally; only segment-level moves earn a good/bad colour. Flat deltas are neutral too.
        fun tone(delta: Float?, invert: Boolean): HeroDeltaTone {
            val d = delta ?: return HeroDeltaTone.NEUTRAL
            if (abs(d).roundToInt() == 0) return HeroDeltaTone.NEUTRAL
            val good = if (d >= 0f) !invert else invert
            return if (good) HeroDeltaTone.GOOD else HeroDeltaTone.BAD
        }

        fun pct(part: BigDecimal, whole: BigDecimal): Int =
            if (whole > BigDecimal.ZERO)
                part.multiply(BigDecimal(100)).divide(whole, 0, RoundingMode.HALF_UP).toInt()
            else 0

        fun share(part: BigDecimal, whole: BigDecimal): Float =
            if (whole > BigDecimal.ZERO) part.divide(whole, 4, RoundingMode.HALF_UP).toFloat() else 0f

        fun txns(count: Int) = "$count transaction${if (count != 1) "s" else ""}"

        when (selectedTab) {
            AnalyticsOverviewTab.OUTFLOW -> {
                val s = periodOutflow ?: return@remember null
                val segments = s.spending + s.invested + s.ccBillPayment
                AnalyticsHeroState(
                    label = "TOTAL OUTFLOW",
                    amount = CurrencyFormatter.formatCurrencyWhole(s.total, currency),
                    delta = s.deltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.deltaPercent ?: 0f) >= 0f,
                    deltaTone = HeroDeltaTone.NEUTRAL,
                    insight = buildString {
                        append(txns(s.transactionCount))
                        if (s.invested > BigDecimal.ZERO) {
                            append(" · ${CurrencyFormatter.formatCurrencyWhole(s.invested, currency)} invested")
                            append(" (${pct(s.invested, segments)}% of outflow)")
                        }
                    },
                    metrics = listOf(
                        AnalyticsHeroMetric(
                            label = "Spend",
                            amount = CurrencyFormatter.formatCurrencyWhole(s.spending, currency),
                            subLabel = "${pct(s.spending, segments)}% · ${s.spendingTransactionCount} txns",
                            icon = Icons.Default.Receipt,
                            sourceIndex = 0,
                            share = share(s.spending, segments),
                            accent = HeroAccent.EXPENSE,
                        ),
                        AnalyticsHeroMetric(
                            label = "Invested",
                            amount = CurrencyFormatter.formatCurrencyWhole(s.invested, currency),
                            subLabel = "${pct(s.invested, segments)}% · ${s.investmentTransactionCount} txns",
                            icon = Icons.Default.ShowChart,
                            sourceIndex = 1,
                            share = share(s.invested, segments),
                            accent = HeroAccent.SUCCESS,
                        ),
                        AnalyticsHeroMetric(
                            label = "CC Pay",
                            amount = CurrencyFormatter.formatCurrencyWhole(s.ccBillPayment, currency),
                            subLabel = "${pct(s.ccBillPayment, segments)}% · ${s.ccBillPaymentTransactionCount} txns",
                            icon = Icons.Default.CreditCard,
                            sourceIndex = 2,
                            share = share(s.ccBillPayment, segments),
                            accent = HeroAccent.PRIMARY,
                        ),
                    ),
                    isComposition = true,
                )
            }
            AnalyticsOverviewTab.SPENDING -> {
                val s = periodOutflow ?: return@remember null
                val cb = paymentModeBreakdown?.cardAndBank
                val cash = paymentModeBreakdown?.cash
                val cardTotal = cb?.creditTotal ?: BigDecimal.ZERO
                val bankTotal = cb?.bankTotal ?: BigDecimal.ZERO
                val cashTotal = cash?.total ?: BigDecimal.ZERO
                val modes = cardTotal + bankTotal + cashTotal
                val average = if (s.spendingTransactionCount > 0)
                    s.spending.divide(BigDecimal(s.spendingTransactionCount), 2, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
                AnalyticsHeroState(
                    label = "TOTAL SPENDING",
                    amount = CurrencyFormatter.formatCurrencyWhole(s.spending, currency),
                    delta = s.spendingDeltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.spendingDeltaPercent ?: 0f) >= 0f,
                    deltaTone = tone(s.spendingDeltaPercent, invert = true),
                    insight = "${txns(s.spendingTransactionCount)} · " +
                        CurrencyFormatter.formatCurrencyWhole(average, currency) + " average",
                    metrics = listOf(
                        AnalyticsHeroMetric(
                            label = "Card",
                            amount = CurrencyFormatter.formatCurrencyWhole(cardTotal, currency),
                            subLabel = "${pct(cardTotal, modes)}% · ${cb?.creditCount ?: 0} txns",
                            icon = Icons.Default.CreditCard,
                            sourceIndex = 0,
                            share = share(cardTotal, modes),
                            accent = HeroAccent.PRIMARY,
                        ),
                        AnalyticsHeroMetric(
                            label = "Bank",
                            amount = CurrencyFormatter.formatCurrencyWhole(bankTotal, currency),
                            subLabel = "${pct(bankTotal, modes)}% · ${cb?.bankCount ?: 0} txns",
                            icon = Icons.Default.AccountBalance,
                            sourceIndex = 1,
                            share = share(bankTotal, modes),
                            accent = HeroAccent.TERTIARY,
                        ),
                        AnalyticsHeroMetric(
                            label = "Cash",
                            amount = CurrencyFormatter.formatCurrencyWhole(cashTotal, currency),
                            subLabel = "${pct(cashTotal, modes)}% · ${cash?.transactionCount ?: 0} txns",
                            icon = Icons.Default.Wallet,
                            sourceIndex = 2,
                            share = share(cashTotal, modes),
                            accent = HeroAccent.SECONDARY,
                        ),
                    ),
                    isComposition = true,
                )
            }
            AnalyticsOverviewTab.INCOME -> {
                val s = incomeSummary ?: return@remember null
                val average = if (s.transactionCount > 0)
                    s.total.divide(BigDecimal(s.transactionCount), 2, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
                AnalyticsHeroState(
                    label = "TOTAL INCOME",
                    amount = CurrencyFormatter.formatCurrencyWhole(s.total, currency),
                    delta = s.deltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.deltaPercent ?: 0f) >= 0f,
                    deltaTone = tone(s.deltaPercent, invert = false),
                    insight = buildString {
                        append(txns(s.transactionCount))
                        append(" · ")
                        append(
                            if (s.net >= BigDecimal.ZERO)
                                "${CurrencyFormatter.formatCurrencyWhole(s.net, currency)} left after outflow"
                            else
                                "${CurrencyFormatter.formatCurrencyWhole(s.net.abs(), currency)} more out than in",
                        )
                    },
                    metrics = listOf(
                        AnalyticsHeroMetric(
                            label = "Largest",
                            amount = CurrencyFormatter.formatCurrencyWhole(s.largest, currency),
                            subLabel = "1 transaction",
                            icon = Icons.Default.ShowChart,
                            sourceIndex = 0,
                        ),
                        AnalyticsHeroMetric(
                            label = "Average",
                            amount = CurrencyFormatter.formatCurrencyWhole(average, currency),
                            subLabel = "per txn",
                            icon = Icons.Default.Receipt,
                            sourceIndex = 1,
                        ),
                        AnalyticsHeroMetric(
                            label = "Net",
                            amount = CurrencyFormatter.formatCurrencyWhole(s.net, currency),
                            subLabel = "income - outflow",
                            icon = Icons.Default.Wallet,
                            sourceIndex = 2,
                        ),
                    ),
                    isComposition = false,
                )
            }
            AnalyticsOverviewTab.INVESTED -> {
                val s = investmentInsights ?: return@remember null
                val average = if (s.transactionCount > 0)
                    s.totalInvested.divide(BigDecimal(s.transactionCount), 2, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
                AnalyticsHeroState(
                    label = "TOTAL INVESTED",
                    amount = CurrencyFormatter.formatCurrencyWhole(s.totalInvested, currency),
                    delta = s.deltaPercent?.let { formatDelta(it) },
                    deltaIncreasing = (s.deltaPercent ?: 0f) >= 0f,
                    deltaTone = tone(s.deltaPercent, invert = false),
                    insight = buildString {
                        append(txns(s.transactionCount))
                        when {
                            s.topCategory != null ->
                                append(" · ${s.topCategory} ${s.topCategoryPercentage.toInt()}%")
                            s.recurringCount > 0 ->
                                append(" · ${s.recurringCount} recurring SIP${if (s.recurringCount != 1) "s" else ""}")
                        }
                    },
                    metrics = listOf(
                        AnalyticsHeroMetric(
                            label = "SIPs",
                            amount = s.recurringCount.toString(),
                            subLabel = if (s.recurringCount == 1) "recurring plan" else "recurring plans",
                            icon = Icons.Default.Payments,
                            sourceIndex = 0,
                        ),
                        AnalyticsHeroMetric(
                            label = "Largest",
                            amount = CurrencyFormatter.formatCurrencyWhole(s.largestInvestment, currency),
                            subLabel = "1 transaction",
                            icon = Icons.Default.ShowChart,
                            sourceIndex = 1,
                        ),
                        AnalyticsHeroMetric(
                            label = "Average",
                            amount = CurrencyFormatter.formatCurrencyWhole(average, currency),
                            subLabel = "per txn",
                            icon = Icons.Default.Receipt,
                            sourceIndex = 2,
                        ),
                    ),
                    isComposition = false,
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
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header: the single card-level tap target. The breakdown panel below owns its own
            // targets, so nothing is nested and each zone stays visually distinct.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (onTotalClick != null) Modifier.clickable(onClick = onTotalClick) else Modifier),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = hero.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.1.sp,
                    )
                    if (onTotalClick != null) {
                        // Disclosure affordance so the tappable zone is legible at a glance.
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Stepped size + tabular figures: long totals shrink instead of being clipped,
                    // and digits don't jitter when the value tweens between periods.
                    val headlineSp = headlineMoneySize(hero.amount.length)
                    Text(
                        text = currencyAnnotated(hero.amount, headlineSp),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = headlineSp.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    hero.delta?.let { deltaText -> DeltaBadge(hero, deltaText) }
                }

                hero.insight?.let { insight ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = insight,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (hero.isComposition) {
                HeroCompositionPanel(metrics = hero.metrics, onClick = onMetricClick)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    hero.metrics.forEach { metric ->
                        HeroMetricColumn(
                            metric = metric,
                            modifier = Modifier.weight(1f),
                            onClick = onMetricClick?.let { handler -> { handler(metric.sourceIndex) } },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Delta badge. The outflow aggregate is always neutral: an increase can mean healthy investing,
 * so only segments where direction is unambiguous (spending up, investing down) get a judgement
 * colour. Flat movement is neutral regardless of tab.
 */
@Composable
private fun DeltaBadge(hero: AnalyticsHeroState, deltaText: String) {
    val flat = deltaText.trimStart('+', '-') == "0%"
    val tone = if (flat) HeroDeltaTone.NEUTRAL else hero.deltaTone
    val foreground = when (tone) {
        HeroDeltaTone.GOOD -> MaterialTheme.colorScheme.success
        HeroDeltaTone.BAD -> MaterialTheme.colorScheme.expense
        HeroDeltaTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = when (tone) {
        HeroDeltaTone.GOOD -> MaterialTheme.colorScheme.success.copy(alpha = 0.15f)
        HeroDeltaTone.BAD -> MaterialTheme.colorScheme.expense.copy(alpha = 0.15f)
        HeroDeltaTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    flat -> Icons.Default.Remove
                    hero.deltaIncreasing -> Icons.AutoMirrored.Filled.TrendingUp
                    else -> Icons.AutoMirrored.Filled.TrendingDown
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = foreground,
            )
            Text(
                text = "$deltaText vs last",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
            )
        }
    }
}

@Composable
private fun accentColor(accent: HeroAccent): Color = when (accent) {
    HeroAccent.EXPENSE -> MaterialTheme.colorScheme.expense
    HeroAccent.SUCCESS -> MaterialTheme.colorScheme.success
    HeroAccent.PRIMARY -> MaterialTheme.colorScheme.primary
    HeroAccent.SECONDARY -> MaterialTheme.colorScheme.secondary
    HeroAccent.TERTIARY -> MaterialTheme.colorScheme.tertiary
}

/**
 * Composition panel: a 100% stacked bar for instant proportion reading, with a legend underneath
 * for exact values. Zero-value segments are dropped so the panel doesn't reflow dead space.
 */
@Composable
private fun HeroCompositionPanel(
    metrics: List<AnalyticsHeroMetric>,
    onClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val parts = metrics.filter { it.share > 0f }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (parts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                parts.forEach { metric ->
                    Box(
                        modifier = Modifier
                            .weight(metric.share)
                            .fillMaxHeight()
                            .background(accentColor(metric.accent)),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            (parts.ifEmpty { metrics }).forEach { metric ->
                HeroLegendItem(
                    metric = metric,
                    modifier = Modifier.weight(1f),
                    onClick = onClick?.let { handler -> { handler(metric.sourceIndex) } },
                )
            }
        }
    }
}

@Composable
private fun HeroLegendItem(
    metric: AnalyticsHeroMetric,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val amountSp = inlineMoneySize(metric.amount.length)
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accentColor(metric.accent), CircleShape),
            )
            Text(
                text = metric.label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = currencyAnnotated(metric.amount, amountSp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = amountSp.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = metric.subLabel,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    val amountSp = inlineMoneySize(metric.amount.length)
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Size ladder instead of ellipsis — money is never truncated.
        Text(
            text = currencyAnnotated(metric.amount, amountSp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = amountSp.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = metric.subLabel,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surfaceContainer,
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
    val deltaTone: HeroDeltaTone,
    /** One line of context the date range + delta badge don't already say. */
    val insight: String?,
    val metrics: List<AnalyticsHeroMetric>,
    /** True when the metrics are parts of a whole → stacked bar + legend instead of stat columns. */
    val isComposition: Boolean,
)

private fun formatDelta(deltaPercent: Float): String {
    // Round rather than truncate: 30.9% must not render as 30%, and -0.4% must not render as "-0%".
    val absolute = abs(deltaPercent).roundToInt()
    val prefix = when {
        deltaPercent > 0f -> "+"
        deltaPercent < 0f -> "-"
        else -> ""
    }
    return "$prefix$absolute%"
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

