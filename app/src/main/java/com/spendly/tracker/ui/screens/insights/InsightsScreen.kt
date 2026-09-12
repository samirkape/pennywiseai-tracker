package com.spendly.tracker.ui.screens.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.domain.model.InsightConfidence
import com.spendly.tracker.domain.model.InsightType
import com.spendly.tracker.domain.model.SmartInsight
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.PeriodRangeNavigator
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.spendAmber
import com.spendly.tracker.ui.theme.spendAmberBg
import com.spendly.tracker.ui.theme.spendGreen
import com.spendly.tracker.ui.theme.spendGreenBg
import com.spendly.tracker.ui.theme.spendPurple
import com.spendly.tracker.ui.theme.spendPurpleBg
import com.spendly.tracker.ui.theme.spendRed
import com.spendly.tracker.ui.theme.spendRedBg
import com.spendly.tracker.ui.theme.success
import com.spendly.tracker.ui.theme.warning
import com.spendly.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.YearMonth
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToBehavioralStats: (YearMonth) -> Unit = {},
    onNavigateToQuickCategorize: () -> Unit = {},
    onNavigateToTransactions: (
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        transactionType: String?,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?,
        paymentMode: String?,
        bankName: String?,
        accountLast4: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onNavigateToInsightDetail: (insightId: String, anchorMonth: String) -> Unit = { _, _ -> },
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val isLoadingInsights by viewModel.isLoadingInsights.collectAsStateWithLifecycle()
    val lifetimeInsights by viewModel.lifetimeInsights.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val activePeriodRange by viewModel.activePeriodRange.collectAsStateWithLifecycle()
    val uncategorizedTransactionPercentage by viewModel.uncategorizedTransactionPercentage.collectAsStateWithLifecycle()
    val displayInsights = insights
    val selectedMonthLabel = remember(activePeriodRange) {
        DateRangeUtils.formatDateRange(
            activePeriodRange.first,
            activePeriodRange.second,
        )
    }
    val canGoNext = remember(selectedMonth) { selectedMonth.isBefore(YearMonth.now()) }
    val uncategorizedPercentage = uncategorizedTransactionPercentage
    val showCategorizationNudge = remember(uncategorizedPercentage) {
        uncategorizedPercentage != null && uncategorizedPercentage >= 20
    }
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var selectedTab by remember { mutableStateOf(InsightsTab.THIS_MONTH) }

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                title = "Smart Insights",
                scrollBehaviorSmall = scrollBehavior,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.sm,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {

            if (showCategorizationNudge && uncategorizedPercentage != null) {
                item {
                    CategorizationNudgeBanner(
                        uncategorizedTransactionPercentage = uncategorizedPercentage,
                        onClick = onNavigateToQuickCategorize,
                    )
                }
            }

            item(key = "tabs") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    InsightsTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = InsightsTab.entries.size),
                            label = { Text(tab.label) }
                        )
                    }
                }
            }

            when (selectedTab) {
                InsightsTab.THIS_MONTH -> {
                    item(key = "period_nav") {
                        PeriodRangeNavigator(
                            rangeLabel = selectedMonthLabel,
                            onPrevious = viewModel::navigateToPreviousMonth,
                            onNext = viewModel::navigateToNextMonth,
                            canGoNext = canGoNext,
                            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                        )
                    }

                    if (isLoadingInsights) {
                        item(key = "monthly_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (displayInsights.isEmpty()) {
                        item(key = "monthly_empty") {
                            EmptyInsightsState()
                        }
                    } else {
                        val (topInsights, restInsights) = splitTopInsights(displayInsights)
                        item(key = "monthly_carousel") {
                            InsightCarousel(
                                insights = topInsights,
                                onOpenInsightDetail = { onNavigateToInsightDetail(it.id, selectedMonth.toString()) }
                            )
                        }
                        if (restInsights.isNotEmpty()) {
                            item(key = "monthly_rest_label") {
                                Text(
                                    text = "More this period",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs, bottom = 2.dp)
                                )
                            }
                            item(key = "monthly_rest") {
                                CompactInsightList(
                                    insights = restInsights,
                                    onInsightClick = { onNavigateToInsightDetail(it.id, selectedMonth.toString()) }
                                )
                            }
                        }
                    }
                }

                InsightsTab.ALL_TIME -> {
                    if (lifetimeInsights.isEmpty()) {
                        item(key = "lifetime_empty") {
                            EmptyInsightsState()
                        }
                    } else {
                        val (topLifetime, restLifetime) = splitTopInsights(lifetimeInsights)
                        item(key = "lifetime_carousel") {
                            InsightCarousel(
                                insights = topLifetime,
                                modifier = Modifier.padding(top = Spacing.sm),
                                onOpenInsightDetail = { onNavigateToInsightDetail(it.id, selectedMonth.toString()) }
                            )
                        }
                        if (restLifetime.isNotEmpty()) {
                            item(key = "lifetime_rest_label") {
                                Text(
                                    text = "More all-time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs, bottom = 2.dp)
                                )
                            }
                            item(key = "lifetime_rest") {
                                CompactInsightList(
                                    insights = restLifetime,
                                    onInsightClick = { onNavigateToInsightDetail(it.id, selectedMonth.toString()) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeaderV2(
                    title = "Explore more",
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }

            item {
                InsightsNavTile(
                    title = "Behavioral Analytics",
                    subtitle = "Spending habits, time patterns, merchant loyalty",
                    icon = Icons.Default.Insights,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigateToBehavioralStats(selectedMonth) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Period tabs — "This month" vs "All-time" replace what used to be two stacked
// sections in one continuous scroll, so only one context is on screen at a time.
// ─────────────────────────────────────────────────────────────────────────────

private enum class InsightsTab(val label: String) {
    THIS_MONTH("This month"),
    ALL_TIME("All-time"),
}

// ─────────────────────────────────────────────────────────────────────────────
// Insight carousel — the top few insights get a swipeable, story-card treatment
// (à la Cleo/Copilot recap cards) instead of a single static hero, so several
// highlights are reachable without stacking them vertically. Long-tail insights
// stay in the compact list below. Mirrors the HorizontalPager + scale/alpha +
// dot-indicator pattern in ui/components/cards/BudgetCarousel.kt.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightCarousel(
    insights: List<SmartInsight>,
    onOpenInsightDetail: (SmartInsight) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (insights.size == 1) {
        val insight = insights.first()
        InsightCard(
            insight = insight,
            index = 0,
            modifier = modifier,
            onOpenInsightDetail = { onOpenInsightDetail(insight) }
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { insights.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            pageSpacing = Spacing.md
        ) { page ->
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).absoluteValue
            val insight = insights[page]

            InsightCard(
                insight = insight,
                index = page,
                onOpenInsightDetail = { onOpenInsightDetail(insight) },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = lerp(0.95f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.6f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                    }
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(insights.size) { index ->
                val isActive = pagerState.currentPage == index
                val indicatorWidth by animateDpAsState(
                    targetValue = if (isActive) 16.dp else 6.dp,
                    animationSpec = tween(durationMillis = 200),
                    label = "insight_indicator_width_$index"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(indicatorWidth)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightsNavTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    SpendlyCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Insight card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightCard(
    insight: SmartInsight,
    index: Int,
    onOpenInsightDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = insightColor(insight)
    val containerColor = insightContainerColor(insight)
    var visible by remember(insight.id) { mutableStateOf(false) }
    val delayMs = (index * 50).coerceAtMost(200)

    LaunchedEffect(insight.id) { visible = true }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(tween(280, delayMs)) + slideInVertically(tween(280, delayMs)) { it / 10 }
    ) {
        // A full-bleed tonal background (not just an accent strip) plus a large corner radius
        // is what gives this its "flashcard" feel — one vivid stat per card, à la Cleo/Copilot
        // recap cards. Detail (breakdown, comparisons, narrative) lives one tap away in
        // InsightDetailScreen, so this stays a clean glanceable stat instead of a mini table.
        SpendlyCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "${insightLabel(insight.type)}: ${insight.title}. ${insight.primaryValue}. ${insight.secondaryText}"
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            contentPadding = 0.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                // ── Header: icon badge + eyebrow/title, confidence pill trailing ──────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = insightIcon(insight.type),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = insightLabel(insight.type).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Only surface confidence when it's low — "High"/"Medium" on every card
                    // is noise, but a low-confidence read is worth flagging.
                    if (insight.confidence == InsightConfidence.LOW) {
                        ConfidencePill(insight.confidence)
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                // ── Primary value ────────────────────────────────────────
                Text(
                    text = insight.primaryValue,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (insight.secondaryText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = insight.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(Spacing.md))

                // ── CTA chip ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accentColor.copy(alpha = 0.16f))
                            .clickable(onClick = onOpenInsightDetail)
                            .padding(horizontal = Spacing.sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorizationNudgeBanner(
    uncategorizedTransactionPercentage: Int,
    onClick: () -> Unit,
) {
    val accentColor = if (uncategorizedTransactionPercentage >= 50) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    // A slim, text-led banner — no icon badge, no paragraph, no separate button — so this
    // secondary nudge doesn't visually compete with the actual insight cards below it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$uncategorizedTransactionPercentage% of transactions uncategorized",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "Categorize",
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(14.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breakdown row — shared with InsightDetailScreen.kt's own breakdown rendering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun BreakdownRow(
    item: BreakdownItem,
    index: Int,
    maxMetric: Float,
    accentColor: Color,
    showAmount: Boolean
) {
    val progress = (item.metric / maxMetric).coerceIn(0f, 1f)
    val displayValue = if (showAmount) item.amountLabel else item.valueLabel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .semantics { contentDescription = "${index + 1}. ${item.label}. ${displayValue}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Rank
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(16.dp)
        )
        // Name
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Progress bar + label inline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.width(96.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .semantics {
                        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress, 0f..1f)
                    },
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                strokeCap = StrokeCap.Round
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confidence pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfidencePill(confidence: InsightConfidence) {
    val color = when (confidence) {
        InsightConfidence.HIGH -> MaterialTheme.colorScheme.success
        InsightConfidence.MEDIUM -> MaterialTheme.colorScheme.warning
        InsightConfidence.LOW -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = confidence.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Severity — drives which insights get a colored accent strip / tinted background
// so warnings and wins stand out from plain informational stats.
// ─────────────────────────────────────────────────────────────────────────────

internal enum class InsightSeverity { WARNING, POSITIVE, NEUTRAL }

internal fun insightSeverity(insight: SmartInsight): InsightSeverity = when (insight.type) {
    InsightType.ANOMALY, InsightType.LARGEST_EXPENSE -> InsightSeverity.WARNING
    InsightType.SAVINGS_WIN, InsightType.ZERO_SPEND_DAYS, InsightType.LIFETIME_NO_SPEND_STREAK ->
        InsightSeverity.POSITIVE
    InsightType.INCOME_VS_EXPENSE -> if (insight.title.contains("exceeds", ignoreCase = true)) {
        InsightSeverity.WARNING
    } else {
        InsightSeverity.POSITIVE
    }
    else -> InsightSeverity.NEUTRAL
}

/**
 * Splits insights into the top [topCount] (the most urgent/important, shown as a swipeable
 * carousel of full cards) and the remainder (shown as a compact list). Avoids the "wall of
 * identical cards" pattern — only the insights that most deserve attention get the full visual
 * treatment. Priority: warnings first, then by confidence, then positives, keeping original
 * order as a tiebreaker so results stay stable across recompositions.
 */
internal fun splitTopInsights(
    insights: List<SmartInsight>,
    topCount: Int = 3,
): Pair<List<SmartInsight>, List<SmartInsight>> {
    val ranked = insights.withIndex().sortedWith(
        compareBy(
            { severityRank(insightSeverity(it.value)) },
            { confidenceRank(it.value.confidence) },
            { it.index }
        )
    )
    val top = ranked.take(topCount).map { it.value }
    val topIds = top.map { it.id }.toSet()
    val rest = insights.filter { it.id !in topIds }
    return top to rest
}

private fun severityRank(severity: InsightSeverity): Int = when (severity) {
    InsightSeverity.WARNING -> 0
    InsightSeverity.POSITIVE -> 1
    InsightSeverity.NEUTRAL -> 2
}

private fun confidenceRank(confidence: InsightConfidence): Int = when (confidence) {
    InsightConfidence.HIGH -> 0
    InsightConfidence.MEDIUM -> 1
    InsightConfidence.LOW -> 2
}


// ─────────────────────────────────────────────────────────────────────────────
// Compact insight grid — a 2-column grid of icon-badge tiles (same idiom as
// QuickCategorizeScreen's category tiles) instead of divider-separated text rows.
// Manually chunked into rows of 2 rather than a LazyVerticalGrid, since this already
// lives inside a LazyColumn item and shouldn't nest another scrollable. Capped to a
// small default count with a "Show more" expander so the feed reads as a handful of
// curated highlights, not a data dump.
// ─────────────────────────────────────────────────────────────────────────────

private const val COMPACT_LIST_DEFAULT_COUNT = 4

@Composable
private fun CompactInsightList(
    insights: List<SmartInsight>,
    onInsightClick: (SmartInsight) -> Unit,
) {
    var expanded by remember(insights) { mutableStateOf(false) }
    val visibleInsights = if (expanded) insights else insights.take(COMPACT_LIST_DEFAULT_COUNT)
    val hiddenCount = insights.size - visibleInsights.size

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        visibleInsights.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                row.forEach { insight ->
                    CompactInsightTile(
                        insight = insight,
                        onClick = { onInsightClick(insight) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        if (hiddenCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show $hiddenCount more insight${if (hiddenCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactInsightTile(
    insight: SmartInsight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = insightColor(insight)
    // Only warnings/wins get a tinted background — tinting every neutral tile in a
    // 12-tile grid reads as noise, so those keep the flat surface.
    val severity = insightSeverity(insight)
    val containerColor = if (severity == InsightSeverity.NEUTRAL) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        insightContainerColor(insight)
    }

    Surface(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = "${insight.title}. ${insight.primaryValue}. ${insight.secondaryText}"
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = insightIcon(insight.type),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = insight.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Pushes the value to the same baseline across a row regardless of whether
            // the title above it wrapped to one line or two.
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(2.dp))
            Text(
                text = insight.primaryValue,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyInsightsState() {
    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Insights on the way",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Smart highlights appear once enough spending activity is detected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun insightIcon(type: InsightType): ImageVector = when (type) {
    InsightType.ANOMALY -> Icons.Default.Warning
    InsightType.PACE -> Icons.Default.Speed
    InsightType.TOP_GROWER -> Icons.Default.TrendingUp
    InsightType.MERCHANT_JUMP -> Icons.Default.Store
    InsightType.RECURRING_RATIO -> Icons.Default.Autorenew
    InsightType.SAVINGS_WIN -> Icons.Default.CheckCircle
    InsightType.TOP_CATEGORIES -> Icons.Default.Category
    InsightType.LARGEST_EXPENSE -> Icons.Default.Warning
    InsightType.WEEKEND_SPEND -> Icons.Default.WbSunny
    InsightType.INCOME_VS_EXPENSE -> Icons.Default.ShowChart
    InsightType.INVESTMENT_RATIO -> Icons.Default.TrendingUp
    InsightType.NEW_MERCHANTS -> Icons.Default.Explore
    InsightType.MERCHANT_LOYALTY -> Icons.Default.Favorite
    InsightType.TRANSACTION_FREQUENCY -> Icons.Default.Timeline
    InsightType.MONTHLY_COMPARISON -> Icons.Default.CompareArrows
    InsightType.ZERO_SPEND_DAYS -> Icons.Default.EventAvailable
    InsightType.PEAK_SPEND_DAY -> Icons.Default.CalendarToday
    InsightType.SPEND_SPLIT -> Icons.Default.PieChart
    InsightType.LIFETIME_TOP_MERCHANT -> Icons.Default.Store
    InsightType.LIFETIME_TOTAL_SPEND -> Icons.Default.ShowChart
    InsightType.LIFETIME_NO_SPEND_STREAK -> Icons.Default.EventAvailable
    InsightType.LIFETIME_HIGHEST_MONTH -> Icons.Default.TrendingUp
    InsightType.LIFETIME_AVG_MONTHLY_SPEND -> Icons.Default.Speed
    InsightType.LIFETIME_MERCHANT_LOYALTY -> Icons.Default.Favorite
    InsightType.LIFETIME_MEMBER_SINCE -> Icons.Default.AutoAwesome
}

// Money-flow/comparison/summary types read as "purple", merchant/behavior/discovery types
// read as "amber" — the two severity-driven buckets (red/green) are handled separately so
// every insight still gets a vivid, semantically grouped color instead of a muted M3 role.
private fun isMoneyFlowType(type: InsightType): Boolean = when (type) {
    InsightType.MONTHLY_COMPARISON,
    InsightType.LIFETIME_TOTAL_SPEND,
    InsightType.LIFETIME_AVG_MONTHLY_SPEND,
    InsightType.SPEND_SPLIT,
    InsightType.LIFETIME_MEMBER_SINCE -> true
    else -> false
}

@Composable
internal fun insightColor(insight: SmartInsight): Color = when (insightSeverity(insight)) {
    InsightSeverity.WARNING -> MaterialTheme.colorScheme.spendRed
    InsightSeverity.POSITIVE -> MaterialTheme.colorScheme.spendGreen
    InsightSeverity.NEUTRAL -> if (isMoneyFlowType(insight.type)) {
        MaterialTheme.colorScheme.spendPurple
    } else {
        MaterialTheme.colorScheme.spendAmber
    }
}

@Composable
internal fun insightContainerColor(insight: SmartInsight): Color = when (insightSeverity(insight)) {
    InsightSeverity.WARNING -> MaterialTheme.colorScheme.spendRedBg
    InsightSeverity.POSITIVE -> MaterialTheme.colorScheme.spendGreenBg
    InsightSeverity.NEUTRAL -> if (isMoneyFlowType(insight.type)) {
        MaterialTheme.colorScheme.spendPurpleBg
    } else {
        MaterialTheme.colorScheme.spendAmberBg
    }
}

internal fun insightLabel(type: InsightType): String = when (type) {
    InsightType.ANOMALY -> "Unusual spend"
    InsightType.TOP_GROWER -> "Fastest growth"
    InsightType.MERCHANT_JUMP -> "Merchant highlights"
    InsightType.PACE -> "Spending pace"
    InsightType.RECURRING_RATIO -> "Recurring spend"
    InsightType.SAVINGS_WIN -> "Savings win"
    InsightType.TOP_CATEGORIES -> "Top categories"
    InsightType.LARGEST_EXPENSE -> "Big expense"
    InsightType.WEEKEND_SPEND -> "Weekend patterns"
    InsightType.INCOME_VS_EXPENSE -> "Cash flow"
    InsightType.INVESTMENT_RATIO -> "Investments"
    InsightType.NEW_MERCHANTS -> "New discoveries"
    InsightType.MERCHANT_LOYALTY -> "Merchant loyalty"
    InsightType.TRANSACTION_FREQUENCY -> "Activity"
    InsightType.MONTHLY_COMPARISON -> "Month comparison"
    InsightType.ZERO_SPEND_DAYS -> "No-spend days"
    InsightType.PEAK_SPEND_DAY -> "Peak spend day"
    InsightType.SPEND_SPLIT -> "Spend timing"
    InsightType.LIFETIME_TOP_MERCHANT -> "All-time top merchant"
    InsightType.LIFETIME_TOTAL_SPEND -> "Lifetime spend"
    InsightType.LIFETIME_NO_SPEND_STREAK -> "Longest no-spend streak"
    InsightType.LIFETIME_HIGHEST_MONTH -> "Highest month on record"
    InsightType.LIFETIME_AVG_MONTHLY_SPEND -> "All-time monthly average"
    InsightType.LIFETIME_MERCHANT_LOYALTY -> "All-time merchant loyalty"
    InsightType.LIFETIME_MEMBER_SINCE -> "Member since"
}

internal data class BreakdownItem(val label: String, val valueLabel: String, val amountLabel: String, val metric: Float)

internal fun String.stripToFloat(): Float = replace(Regex("[^0-9.-]"), "").toFloatOrNull() ?: 0f
