package com.pennywiseai.tracker.presentation.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.cardBorder
import com.pennywiseai.tracker.ui.theme.spendAmber
import com.pennywiseai.tracker.ui.theme.spendGreen
import com.pennywiseai.tracker.ui.theme.spendPurple
import com.pennywiseai.tracker.ui.theme.spendRed
import com.pennywiseai.tracker.ui.theme.textMuted
import kotlinx.coroutines.delay

@Composable
fun SmartInsightsCard(
    insights: List<SpendInsight>,
    onInsightAction: (SpendInsight) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardDesc = if (insights.isEmpty()) {
        "Smart insights: no unusual patterns in your spending"
    } else {
        "Smart insights: ${insights.size} item${if (insights.size > 1) "s" else ""}. " +
            insights.joinToString(". ") { "${it.title}: ${it.body}" }
    }

    val pagerState = rememberPagerState { insights.size.coerceAtLeast(1) }

    // Auto-advance every 4 seconds when there are multiple insights
    if (insights.size > 1) {
        LaunchedEffect(pagerState.pageCount) {
            while (true) {
                delay(7_000)
                val next = (pagerState.currentPage + 1) % pagerState.pageCount
                pagerState.animateScrollToPage(next, animationSpec = tween(600))
            }
        }
    }

    PennyWiseCardV2(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDesc },
        contentPadding = 0.dp
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.spendPurple,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "SMART INSIGHTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.spendPurple,
                    letterSpacing = 0.66.sp
                )
            }
            // Dot indicators — one per insight
            if (insights.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(insights.size) { index ->
                        val isActive = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isActive) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.spendPurple
                                    else MaterialTheme.colorScheme.spendPurple.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.cardBorder,
            thickness = 0.5.dp
        )

        // ── Body ──────────────────────────────────────────────────────────────
        if (insights.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.textMuted
                )
                Text(
                    text = "All clear",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "No unusual patterns in your spending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = true
            ) { page ->
                InsightRow(
                    insight = insights[page],
                    onAction = { onInsightAction(insights[page]) }
                )
            }
        }
    }
}

@Composable
private fun InsightRow(
    insight: SpendInsight,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = insight.severity.toAccentColor()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 56.dp)
            .padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar — full row height
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(Spacing.sm))

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = insight.type.toIcon(),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                text = insight.body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                modifier = Modifier.padding(start = 16.dp + Spacing.xs) // align under title (icon width + gap)
            )
        }

        // Optional CTA
        if (insight.actionLabel != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = insight.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun InsightSeverity.toAccentColor(): Color = when (this) {
    InsightSeverity.ALERT   -> MaterialTheme.colorScheme.spendRed
    InsightSeverity.CAUTION -> MaterialTheme.colorScheme.spendAmber
    InsightSeverity.INFO    -> MaterialTheme.colorScheme.spendGreen
}

private fun InsightType.toIcon(): ImageVector = when (this) {
    InsightType.PACE_PREDICTION       -> Icons.Outlined.TrendingUp
    InsightType.CATEGORY_SPIKE        -> Icons.Outlined.BarChart
    InsightType.SUBSCRIPTION_UPCOMING -> Icons.Outlined.Refresh
    InsightType.GOAL_MILESTONE        -> Icons.Outlined.EmojiEvents
    InsightType.WEEK_TREND            -> Icons.Outlined.CompareArrows
    InsightType.LOW_REMAINING         -> Icons.Outlined.Warning
}
