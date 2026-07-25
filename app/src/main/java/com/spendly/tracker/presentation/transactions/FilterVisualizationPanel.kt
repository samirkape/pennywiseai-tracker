package com.spendly.tracker.presentation.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.components.BalanceChart
import com.spendly.tracker.ui.components.BalancePoint
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter

/**
 * Inline chart panel displayed in the Transactions screen.
 *
 * Two tabs:
 *  - **Breakdown**: category breakdown — default, most immediately useful.
 *  - **Trend**: smooth line chart across the filtered date range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterVisualizationPanel(
    data: FilterVisualizationData,
    modifier: Modifier = Modifier,
    activeCategory: String? = null,
    onCategoryClick: (String) -> Unit = {},
    onClearCategory: () -> Unit = {}
) {
    // Default to Breakdown — more immediately actionable.
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    val tabs = listOf("Trend", "Breakdown")
    val trendAvailable = data.trendPoints.size >= 2
    val breakdownAvailable = data.categoryItems.isNotEmpty()

    LaunchedEffect(trendAvailable, breakdownAvailable) {
        if (selectedTab == 0 && !trendAvailable && breakdownAvailable) selectedTab = 1
        if (selectedTab == 1 && !breakdownAvailable && trendAvailable) selectedTab = 0
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.md) // explicit bottom breathing room
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (activeCategory != null) {
                    // Drill-down state: show back navigation
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearCategory)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to all categories",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = activeCategory,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap to see all categories",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Normal state
                    Column {
                        Text(
                            text = "${data.dominantTypeLabel} Overview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedTab == 1 && breakdownAvailable) {
                            Text(
                                text = "Top ${data.categoryItems.size} categories",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
                    tabs.forEachIndexed { index, label ->
                        val enabled = if (index == 0) trendAvailable else breakdownAvailable
                        SegmentedButton(
                            selected = selectedTab == index,
                            onClick = { if (enabled) selectedTab = index },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                            label = { Text(text = label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "viz_tab_content"
            ) { tab ->
                when (tab) {
                    0 -> TrendTabContent(data = data)
                    else -> BreakdownTabContent(data = data, onCategoryClick = onCategoryClick)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trend tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrendTabContent(data: FilterVisualizationData) {
    if (data.trendPoints.size < 2) {
        EmptyVizPlaceholder("Not enough data points for trend")
        return
    }
    val balancePoints = remember(data.trendPoints, data.currency) {
        data.trendPoints.map { BalancePoint(it.dateTime, it.amount, data.currency) }
    }
    BalanceChart(
        primaryCurrency = data.currency,
        balanceHistory = balancePoints,
        modifier = Modifier.fillMaxWidth(),
        height = 200,
        smooth = false
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Breakdown tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakdownTabContent(
    data: FilterVisualizationData,
    onCategoryClick: (String) -> Unit
) {
    if (data.categoryItems.isEmpty()) {
        EmptyVizPlaceholder("No category data available")
        return
    }

    // "Others" is the catch-all — push it to the bottom so specific categories lead.
    val sorted = remember(data.categoryItems) {
        val others = data.categoryItems.filter { it.name.equals("Others", ignoreCase = true) }
        val specific = data.categoryItems.filter { !it.name.equals("Others", ignoreCase = true) }
        specific + others
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sorted.forEach { item ->
            CategoryBreakdownRow(
                item = item,
                currency = data.currency,
                onClick = { onCategoryClick(item.name) }
            )
        }
    }
}

/**
 * Compact 2-line row:
 *
 *   ● Name                         ₹Amount  →
 *     [████████░░░░░░░]  47% · 94 txns
 *
 * Tapping drills down to show only that category's transactions.
 */
@Composable
private fun CategoryBreakdownRow(
    item: FilterCategoryItem,
    currency: String,
    onClick: () -> Unit
) {
    val categoryColor: Color = CategoryMapping.categories[item.name]?.color
        ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Color dot aligned to name cap-height
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(categoryColor)
        )

        Column(modifier = Modifier.weight(1f)) {

            // ── Line 1: Name  ·  Amount  ·  Arrow ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = CurrencyFormatter.formatAbbreviated(item.amount.toDouble(), currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Filter by ${item.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // ── Line 2: Bar  ·  % · txn count ────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(categoryColor.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.percentage.coerceIn(0.03f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(categoryColor)
                    )
                }
                Text(
                    text = "${(item.percentage * 100).toInt()}% · ${item.transactionCount} txns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVizPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}










