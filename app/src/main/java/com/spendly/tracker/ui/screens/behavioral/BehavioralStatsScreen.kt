package com.spendly.tracker.ui.screens.behavioral

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.presentation.common.TimePeriod
import com.spendly.tracker.ui.components.PeriodRangeNavigator
import com.spendly.tracker.ui.components.PennyWiseEmptyState
import com.spendly.tracker.ui.components.PennyWiseStandardScaffold
import com.spendly.tracker.ui.components.cards.ListItemCardV2
import com.spendly.tracker.ui.components.cards.PennyWiseCardV2
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.skeleton.BalanceCardSkeleton
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import com.spendly.tracker.utils.DateRangeUtils
import java.math.BigDecimal
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehavioralStatsScreen(
    viewModel: BehavioralStatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToTransaction: (Long) -> Unit = {},
    onNavigateToTransactionsMultiCategory: (
        categories: String,
        period: String?,
        currency: String?,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?,
    ) -> Unit = { _, _, _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val useFinancialMonth by viewModel.useFinancialMonth.collectAsStateWithLifecycle()
    val periodAnchorMonth by viewModel.periodAnchorMonth.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isDark = isSystemInDarkTheme()

    val allowedPeriods = remember(useFinancialMonth) {
        if (useFinancialMonth) {
            listOf(
                TimePeriod.THIS_MONTH,
                TimePeriod.CALENDAR_MONTH,
                TimePeriod.LAST_MONTH,
                TimePeriod.CURRENT_FY,
                TimePeriod.ALL
            )
        } else {
            listOf(
                TimePeriod.THIS_MONTH,
                TimePeriod.LAST_MONTH,
                TimePeriod.CURRENT_FY,
                TimePeriod.ALL
            )
        }
    }

    val activePeriodRange = remember(uiState.periodStart, uiState.periodEnd) {
        if (uiState.periodStart != null && uiState.periodEnd != null) {
            uiState.periodStart!! to uiState.periodEnd!!
        } else {
            null
        }
    }
    val periodRangeLabel = remember(activePeriodRange) {
        activePeriodRange?.let { (start, end) -> DateRangeUtils.formatDateRange(start, end) }
    }
    val showPeriodNavigator = remember(selectedPeriod, periodAnchorMonth) {
        periodAnchorMonth != null &&
            selectedPeriod != TimePeriod.ALL &&
            selectedPeriod != TimePeriod.CURRENT_FY
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    PennyWiseStandardScaffold(
        title = "Behavioral Stats",
        onNavigateBack = onNavigateBack,
        scrollBehavior = scrollBehavior,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState }
        ) {
            // Period Selector
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(allowedPeriods) { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = {
                                Text(
                                    when {
                                        period == TimePeriod.THIS_MONTH && useFinancialMonth -> "Pay Month"
                                        else -> period.label
                                    }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            periodAnchorMonth?.let { anchorMonth ->
                if (showPeriodNavigator && periodRangeLabel != null) {
                    item {
                        PeriodRangeNavigator(
                            rangeLabel = periodRangeLabel,
                            onPrevious = { viewModel.navigateToMonth(anchorMonth.minusMonths(1)) },
                            onNext = { viewModel.navigateToMonth(anchorMonth.plusMonths(1)) },
                            canGoNext = anchorMonth < YearMonth.now(),
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    // Loading skeletons
                    items(3) {
                        BalanceCardSkeleton()
                    }
                }

                uiState.isEmpty -> {
                    item {
                        PennyWiseEmptyState(
                            icon = Icons.Default.Insights,
                            headline = "No spending data",
                            description = "Add some transactions to see your behavioral patterns here."
                        )
                    }
                }

                else -> {
                    // ── Section 0: Spending Forecast ─────────────────────────────
                    uiState.spendingForecast?.let { forecast ->
                        item {
                            SectionHeaderV2(title = "Spending Forecast")
                        }
                        item {
                            SpendingForecastCard(
                                forecast = forecast,
                                currency = uiState.currency,
                                isDark = isDark
                            )
                        }
                    }

                    // ── Section 1: Spending Patterns ─────────────────────────────
                    item {
                        SectionHeaderV2(title = "Spending Patterns")
                    }

                    item {
                        PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Time of Day",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = Spacing.sm)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                uiState.timeOfDayBuckets.forEach { bucket ->
                                    TimeOfDayRow(
                                        bucket = bucket,
                                        currency = uiState.currency,
                                        isDark = isDark
                                    )
                                }
                            }
                        }
                    }

                    item {
                        PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Day of Week",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = Spacing.sm)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                uiState.dayOfWeekBuckets.forEach { bucket ->
                                    DayOfWeekRow(
                                        bucket = bucket,
                                        currency = uiState.currency,
                                        isDark = isDark
                                    )
                                }
                            }
                        }
                    }

                    // ── Section 2: Tag Insights ──────────────────────────────────
                    if (
                        uiState.topTags.isNotEmpty() ||
                        uiState.categoryOverlaps.isNotEmpty() ||
                        uiState.multiCategoryTransactions.isNotEmpty()
                    ) {
                        item {
                            TagInsightsCard(
                                topTags = uiState.topTags,
                                overlaps = uiState.categoryOverlaps,
                                multiTaggedTransactions = uiState.multiCategoryTransactions,
                                currency = uiState.currency,
                                onOverlapClick = { overlap ->
                                    val encoded = listOf(overlap.categoryA, overlap.categoryB)
                                        .joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
                                    onNavigateToTransactionsMultiCategory(
                                        encoded,
                                        TimePeriod.CUSTOM.name,
                                        uiState.currency,
                                        uiState.periodStart?.toEpochDay(),
                                        uiState.periodEnd?.toEpochDay(),
                                    )
                                },
                                onTransactionClick = onNavigateToTransaction,
                            )
                        }
                    }

                    // ── Section 3: Merchant Loyalty ──────────────────────────────
                    if (uiState.topMerchants.isNotEmpty()) {
                        item {
                            SectionHeaderV2(title = "Merchant Loyalty")
                        }
                        items(uiState.topMerchants) { merchant ->
                            MerchantLoyaltyItem(
                                merchant = merchant,
                                currency = uiState.currency
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Spending Forecast Card ──────────────────────────────────────────────────────

@Composable
private fun SpendingForecastCard(
    forecast: SpendingForecast,
    currency: String,
    isDark: Boolean
) {
    val isOverPace = forecast.pace > BigDecimal.ZERO
    val paceColor = if (isOverPace) {
        if (isDark) expense_dark else expense_light
    } else {
        if (isDark) success_dark else success_light
    }
    val progressFraction = (forecast.daysElapsed.toFloat() / forecast.totalDays.toFloat())
        .coerceIn(0f, 1f)
    val projectedRemaining = (forecast.trendForecast - forecast.spentSoFar)
        .coerceAtLeast(BigDecimal.ZERO)
    val headlineLabel = if (forecast.periodIsCurrent) "PROJECTED SPEND" else "PERIOD SPEND"
    val headlineAmount = if (forecast.periodIsCurrent) {
        forecast.trendForecast
    } else {
        forecast.spentSoFar
    }
    val insight = buildForecastInsight(forecast)

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Dimensions.Padding.content,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headlineLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = CurrencyFormatter.formatCurrency(headlineAmount, currency),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (forecast.periodIsCurrent && forecast.daysElapsed > 1) {
                ForecastPacePill(
                    paceAmount = forecast.pace.abs(),
                    currency = currency,
                    isOverPace = isOverPace,
                    paceColor = paceColor,
                )
            }
        }

        if (forecast.periodIsCurrent) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Day ${forecast.daysElapsed} of ${forecast.totalDays}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (forecast.daysRemaining > 0) {
                        Text(
                            text = "${forecast.daysRemaining} days left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        HorizontalDivider(
            thickness = 1.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        ForecastSummaryStrip(
            forecast = forecast,
            currency = currency,
            projectedRemaining = projectedRemaining,
        )

        insight?.let { text ->
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForecastPacePill(
    paceAmount: BigDecimal,
    currency: String,
    isOverPace: Boolean,
    paceColor: Color,
) {
    val prefix = if (isOverPace) "+" else "−"
    val label = if (isOverPace) "Over pace" else "Under pace"
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = paceColor.copy(alpha = 0.12f),
        ) {
            Text(
                text = "$prefix${CurrencyFormatter.formatCurrency(paceAmount, currency)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = paceColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = paceColor,
        )
    }
}

@Composable
private fun ForecastSummaryStrip(
    forecast: SpendingForecast,
    currency: String,
    projectedRemaining: BigDecimal,
) {
    val recentPaceSub = if (forecast.daysElapsed < 7) "per day" else "per day · 7d"
    val showRemaining = forecast.periodIsCurrent && forecast.daysRemaining > 0
    val spentSubLabel = if (forecast.periodIsCurrent) "so far" else "period total"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ForecastStripColumn(
                label = "Spent",
                value = CurrencyFormatter.formatCurrency(forecast.spentSoFar, currency),
                subLabel = spentSubLabel,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(52.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            ForecastStripColumn(
                label = "Recent pace",
                value = CurrencyFormatter.formatCurrency(forecast.recentDailyAvg, currency),
                subLabel = recentPaceSub,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(52.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            if (showRemaining) {
                ForecastStripColumn(
                    label = "Left to spend",
                    value = CurrencyFormatter.formatCurrency(projectedRemaining, currency),
                    subLabel = "at recent pace",
                    modifier = Modifier.weight(1f),
                )
            } else {
                ForecastStripColumn(
                    label = "Period avg",
                    value = CurrencyFormatter.formatCurrency(forecast.overallDailyAvg, currency),
                    subLabel = "per day",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ForecastStripColumn(
    label: String,
    value: String,
    subLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildForecastInsight(forecast: SpendingForecast): String? {
    if (!forecast.periodIsCurrent) return null

    if (forecast.confidence == ForecastConfidence.LOW) {
        return "Early in the period — projection will sharpen as more transactions come in."
    }

    val recentVsOverall = forecast.recentDailyAvg.compareTo(forecast.overallDailyAvg)
    return when {
        recentVsOverall > 0 ->
            "Recent spending is faster than your period average."
        recentVsOverall < 0 ->
            "Recent spending is slower than your period average."
        else -> null
    }
}

// ─── Time of Day Row ────────────────────────────────────────────────────────────

@Composable
private fun TimeOfDayRow(
    bucket: TimeOfDayBucket,
    currency: String,
    isDark: Boolean
) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxBarFraction = bucket.share.coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = bucket.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (maxBarFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(maxBarFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor.copy(alpha = 0.3f + maxBarFraction * 0.7f))
                )
            }
        }
        Text(
            text = CurrencyFormatter.formatCurrency(bucket.totalAmount, currency),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

// ─── Day of Week Row ────────────────────────────────────────────────────────────

@Composable
private fun DayOfWeekRow(
    bucket: DayOfWeekBucket,
    currency: String,
    isDark: Boolean
) {
    val barColor = MaterialTheme.colorScheme.secondary
    val maxBarFraction = bucket.share.coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = bucket.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(36.dp),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (maxBarFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(maxBarFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor.copy(alpha = 0.3f + maxBarFraction * 0.7f))
                )
            }
        }
        Text(
            text = CurrencyFormatter.formatCurrency(bucket.totalAmount, currency),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

// ─── Merchant Loyalty Item ──────────────────────────────────────────────────────

@Composable
private fun MerchantLoyaltyItem(
    merchant: MerchantLoyalty,
    currency: String
) {
    ListItemCardV2(
        title = merchant.name,
        subtitle = "Avg: ${CurrencyFormatter.formatCurrency(merchant.avgAmount, currency)} per visit",
        amount = "${merchant.visitCount}x",
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.list)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = merchant.visitCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}







