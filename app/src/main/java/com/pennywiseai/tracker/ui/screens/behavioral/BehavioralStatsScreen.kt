package com.pennywiseai.tracker.ui.screens.behavioral

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.ListItemCardV2
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.skeleton.BalanceCardSkeleton
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehavioralStatsScreen(
    viewModel: BehavioralStatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val useFinancialMonth by viewModel.useFinancialMonth.collectAsStateWithLifecycle()
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Behavioral Stats",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        }
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
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md
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

                    // ── Section 2: Merchant Loyalty ──────────────────────────────
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
    val isOverPace = forecast.pace > java.math.BigDecimal.ZERO
    val paceColor = if (isOverPace) {
        if (isDark) expense_dark else expense_light
    } else {
        if (isDark) success_dark else success_light
    }

    // Progress: how far through the period we are (by days)
    val progressFraction = (forecast.daysElapsed.toFloat() / forecast.totalDays.toFloat())
        .coerceIn(0f, 1f)

    // Trend forecast vs base forecast delta
    val trendDelta = forecast.trendForecast - forecast.baseForecast
    val trendHigher = trendDelta > java.math.BigDecimal.ZERO

    val confidenceLabel = when (forecast.confidence) {
        ForecastConfidence.HIGH   -> "High confidence"
        ForecastConfidence.MEDIUM -> "Medium confidence"
        ForecastConfidence.LOW    -> "Low confidence (${forecast.daysElapsed}d of data)"
    }

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        // ── Projected total headline ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Projected total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(forecast.trendForecast, currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Confidence badge
                Text(
                    text = "● $confidenceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pace indicator
            Column(horizontalAlignment = Alignment.End) {
                val pacePrefix = if (isOverPace) "+" else ""
                val paceLabel = if (isOverPace) "over pace" else "under pace"
                Text(
                    text = "$pacePrefix${CurrencyFormatter.formatCurrency(forecast.pace.abs(), currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = paceColor
                )
                Text(
                    text = paceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = paceColor
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // ── Period progress bar ─────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Day ${forecast.daysElapsed} of ${forecast.totalDays}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (forecast.periodIsCurrent && forecast.daysRemaining > 0) {
                    Text(
                        text = "${forecast.daysRemaining}d remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // ── Two-row stats grid ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ForecastStatCell(
                label = "Spent so far",
                value = CurrencyFormatter.formatCurrency(forecast.spentSoFar, currency),
                modifier = Modifier.weight(1f)
            )
            ForecastStatCell(
                label = "Daily avg (overall)",
                value = CurrencyFormatter.formatCurrency(forecast.overallDailyAvg, currency),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ForecastStatCell(
                label = "Daily avg (last 7d)",
                value = CurrencyFormatter.formatCurrency(forecast.recentDailyAvg, currency),
                valueColor = if (trendHigher) {
                    if (isDark) expense_dark else expense_light
                } else {
                    if (isDark) success_dark else success_light
                },
                modifier = Modifier.weight(1f)
            )
            ForecastStatCell(
                label = "Flat-line forecast",
                value = CurrencyFormatter.formatCurrency(forecast.baseForecast, currency),
                modifier = Modifier.weight(1f)
            )
        }

        // ── Trend narrative ─────────────────────────────────────────────────────
        if (forecast.periodIsCurrent) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            val narrative = buildForecastNarrative(forecast, currency)
            Text(
                text = narrative,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ForecastStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(Spacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

private fun buildForecastNarrative(forecast: SpendingForecast, currency: String): String {
    val recentVsOverall = forecast.recentDailyAvg.compareTo(forecast.overallDailyAvg)
    val acceleration = when {
        recentVsOverall > 0 -> "accelerating — you're spending more than usual lately"
        recentVsOverall < 0 -> "decelerating — you're spending less than usual lately"
        else                 -> "steady — your recent spending matches your overall pace"
    }
    val remaining = if (forecast.daysRemaining > 0)
        "With ${forecast.daysRemaining} days left, "
    else
        ""
    return "${remaining}your spending is $acceleration. The trend forecast reflects this recent pace."
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







