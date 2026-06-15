package com.pennywiseai.tracker.ui.screens.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.domain.model.InsightConfidence
import com.pennywiseai.tracker.domain.model.InsightType
import com.pennywiseai.tracker.domain.model.SmartInsight
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PeriodRangeNavigator
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToBehavioralStats: () -> Unit = {},
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
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> }
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val activePeriodRange by viewModel.activePeriodRange.collectAsStateWithLifecycle()
    val uncategorizedTransactionPercentage by viewModel.uncategorizedTransactionPercentage.collectAsStateWithLifecycle()
    val displayInsights = remember(insights) { insights.filterNot { it.type == InsightType.ANOMALY } }
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

            item {
                SectionHeaderV2(title = "Monthly insights")
            }

            item {
                PeriodRangeNavigator(
                    rangeLabel = selectedMonthLabel,
                    onPrevious = viewModel::navigateToPreviousMonth,
                    onNext = viewModel::navigateToNextMonth,
                    canGoNext = canGoNext,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
            }

            if (displayInsights.isEmpty()) {
                item {
                    EmptyInsightsState()
                }
            } else {
                itemsIndexed(
                    items = displayInsights,
                    key = { _, insight -> insight.id }
                ) { index, insight ->
                    InsightCard(
                        insight = insight,
                        index = index,
                        onViewDetails = {
                            onNavigateToTransactions(
                                insight.metadata["category"],
                                insight.metadata["merchant"],
                                insight.metadata["period"] ?: "CUSTOM",
                                insight.metadata["currency"],
                                insight.metadata["transactionType"],
                                insight.metadata["startDate"]?.toLongOrNull(),
                                insight.metadata["endDate"]?.toLongOrNull(),
                                insight.metadata["paymentMode"],
                                insight.metadata["bankName"],
                                insight.metadata["accountLast4"]
                            )
                        }
                    )
                }
            }

            item {
                SectionHeaderV2(title = "Explore more")
            }

            item {
                InsightsNavTile(
                    title = "Behavioral Analytics",
                    subtitle = "Spending habits, time patterns, merchant loyalty",
                    icon = Icons.Default.Insights,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToBehavioralStats
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
    PennyWiseCardV2(
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
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
    onViewDetails: () -> Unit
) {
    val accentColor = insightColor(insight.type)
    var visible by remember(insight.id) { mutableStateOf(false) }
    var showAmount by remember(insight.id) { mutableStateOf(false) }
    val delayMs = (index * 50).coerceAtMost(200)

    LaunchedEffect(insight.id) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280, delayMs)) + slideInVertically(tween(280, delayMs)) { it / 10 }
    ) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "${insightLabel(insight.type)}: ${insight.title}. ${insight.primaryValue}. ${insight.secondaryText}"
                },
            onClick = { showAmount = !showAmount },
            contentPadding = 0.dp
        ) {
            Column {
                // ── Card header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = insightIcon(insight.type),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = insightLabel(insight.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    ConfidencePill(insight.confidence)
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )

                // ── Card body ────────────────────────────────────────────────
                Column(
                    modifier = Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = Spacing.md
                    )
                ) {
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = insight.primaryValue,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        if (insight.secondaryText.isNotBlank()) {
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = insight.secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // ── Breakdown ────────────────────────────────────────────
                    val topItems = insight.metadata["topItems"]
                    if (!topItems.isNullOrEmpty()) {
                        val items = remember(topItems, insight.type) {
                            parseBreakdownItems(topItems, insight.type)
                        }
                        if (items.isNotEmpty()) {
                            val maxMetric = remember(items) {
                                items.maxOf { it.metric }.coerceAtLeast(1f)
                            }
                            Spacer(Modifier.height(Spacing.md))
                            BreakdownSection(
                                title = breakdownTitle(insight.type),
                                items = items,
                                maxMetric = maxMetric,
                                accentColor = accentColor,
                                showAmount = showAmount
                            )
                        }
                    }

                    // ── CTA ──────────────────────────────────────────────────
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onViewDetails,
                            colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
                        ) {
                            Text(
                                text = "View transactions",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
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
    val subtitle = if (uncategorizedTransactionPercentage >= 50) {
        "$uncategorizedTransactionPercentage% of your transactions are uncategorized. Tagging more merchants unlocks better category insights."
    } else {
        "$uncategorizedTransactionPercentage% of your transactions are uncategorized. Improve insights by categorizing a few recent transactions."
    }

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Label,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categorize to unlock insights",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Label,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Quick categorize",
                    modifier = Modifier.padding(start = Spacing.xs),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breakdown section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakdownSection(
    title: String,
    items: List<BreakdownItem>,
    maxMetric: Float,
    accentColor: Color,
    showAmount: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isSystemInDarkTheme()) 0.5f else 0.35f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
            modifier = Modifier
                .padding(bottom = 2.dp)
                .semantics { heading() }
        )
        items.forEachIndexed { index, item ->
            BreakdownRow(item = item, index = index, maxMetric = maxMetric, accentColor = accentColor, showAmount = showAmount)
        }
    }
}

@Composable
private fun BreakdownRow(
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
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyInsightsState() {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
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

private fun insightIcon(type: InsightType): ImageVector = when (type) {
    InsightType.ANOMALY -> Icons.Default.Warning
    InsightType.PACE -> Icons.Default.Speed
    InsightType.TOP_GROWER -> Icons.Default.TrendingUp
    InsightType.MERCHANT_JUMP -> Icons.Default.Store
    InsightType.RECURRING_RATIO -> Icons.Default.Autorenew
    InsightType.SAVINGS_WIN -> Icons.Default.CheckCircle
}

@Composable
private fun insightColor(type: InsightType): Color = when (type) {
    InsightType.TOP_GROWER -> MaterialTheme.colorScheme.tertiary
    InsightType.MERCHANT_JUMP -> MaterialTheme.colorScheme.primary
    InsightType.RECURRING_RATIO -> MaterialTheme.colorScheme.secondary
    InsightType.SAVINGS_WIN -> MaterialTheme.colorScheme.success
    InsightType.PACE -> MaterialTheme.colorScheme.warning
    InsightType.ANOMALY -> MaterialTheme.colorScheme.error
}

private fun insightLabel(type: InsightType): String = when (type) {
    InsightType.ANOMALY -> "Unusual spend"
    InsightType.TOP_GROWER -> "Fastest growth"
    InsightType.MERCHANT_JUMP -> "Merchant highlights"
    InsightType.PACE -> "Spending pace"
    InsightType.RECURRING_RATIO -> "Recurring spend"
    InsightType.SAVINGS_WIN -> "Savings win"
}

private fun breakdownTitle(type: InsightType): String = when (type) {
    InsightType.ANOMALY -> "Top categories"
    InsightType.TOP_GROWER -> "Growing categories"
    InsightType.MERCHANT_JUMP -> "Top merchants"
    InsightType.PACE -> "Spend pattern"
    InsightType.RECURRING_RATIO -> "Recurring merchants"
    InsightType.SAVINGS_WIN -> "Savings breakdown"
}

private data class BreakdownItem(val label: String, val valueLabel: String, val amountLabel: String, val metric: Float)

private fun parseBreakdownItems(topItems: String, type: InsightType): List<BreakdownItem> {
    return topItems.split("|").take(5).mapNotNull { raw ->
        val parts = raw.split(":")
        if (parts.size < 2) return@mapNotNull null
        val label = parts[0]
        val valueLabel = parts[1]
        val amountLabel = when (type) {
            InsightType.TOP_GROWER -> {
                // Format: "Category:Percentage%:₹Amount"
                val currentAmount = parts.getOrNull(2)?.stripToFloat() ?: 0f
                val percentage = parts[1].stripToFloat()
                // Calculate previous month amount and growth delta
                val previousAmount = if (percentage > 0) currentAmount / (1 + percentage / 100) else currentAmount
                val delta = currentAmount - previousAmount
                val sign = if (delta >= 0) "+" else ""
                "${sign}₹${delta.toInt()}"
            }
            InsightType.SAVINGS_WIN -> {
                // Format: "Category:↓percentage%:₹Amount" (amount is already the delta)
                parts.getOrNull(2) ?: valueLabel
            }
            else -> valueLabel
        }
        val metric: Float = when (type) {
            InsightType.SAVINGS_WIN -> parts.getOrNull(2)?.stripToFloat() ?: 0f
            else -> parts.getOrNull(1)?.stripToFloat() ?: 0f
        }
        BreakdownItem(label, valueLabel, amountLabel, metric)
    }
}

private fun String.stripToFloat(): Float = replace(Regex("[^0-9.-]"), "").toFloatOrNull() ?: 0f
