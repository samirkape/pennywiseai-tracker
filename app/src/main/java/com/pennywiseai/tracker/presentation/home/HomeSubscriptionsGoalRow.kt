package com.pennywiseai.tracker.presentation.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.cardBorder
import com.pennywiseai.tracker.ui.theme.spendAmber
import com.pennywiseai.tracker.ui.theme.spendAmberBg
import com.pennywiseai.tracker.ui.theme.spendGreen
import com.pennywiseai.tracker.ui.theme.textMuted
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun HomeSubscriptionsGoalRow(
    activeSubscriptionCount: Int,
    totalSubscriptionAmount: BigDecimal,
    upcomingSubscriptions: List<SubscriptionEntity>,
    currency: String,
    activeGoals: List<GoalEntity>,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Note: IntrinsicSize.Max cannot be used here because GoalsPagerCard contains
    // HorizontalPager (SubcomposeLayout) which doesn't support intrinsic measurement.
    // Instead, both cards use defaultMinSize to stay visually consistent.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubscriptionsMiniCard(
            activeCount = activeSubscriptionCount,
            totalAmount = totalSubscriptionAmount,
            currency = currency,
            upcoming = upcomingSubscriptions,
            onClick = onNavigateToSubscriptions,
            modifier = Modifier.weight(1f)
        )
        GoalsPagerCard(
            goals = activeGoals,
            currency = currency,
            onClick = onNavigateToGoals,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Subscriptions tile ────────────────────────────────────────────────────────

@Composable
private fun SubscriptionsMiniCard(
    activeCount: Int,
    totalAmount: BigDecimal,
    currency: String,
    upcoming: List<SubscriptionEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today    = LocalDate.now()
    val tomorrow = today.plusDays(1)

    PennyWiseCardV2(
        modifier = modifier.defaultMinSize(minHeight = 220.dp),
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null,
                tint = MaterialTheme.colorScheme.spendAmber, modifier = Modifier.size(15.dp))
            Text("SUBSCRIPTIONS", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.spendAmber, letterSpacing = 0.66.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Text(CurrencyFormatter.formatCurrency(totalAmount, currency),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
        Text("$activeCount active · monthly", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted,
            modifier = Modifier.padding(top = Spacing.xs, bottom = 10.dp))

        if (upcoming.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.cardBorder,
                thickness = 0.5.dp, modifier = Modifier.padding(bottom = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcoming.forEach { sub -> SubscriptionRenewalRow(sub, today, tomorrow) }
            }
        }
    }
}

@Composable
private fun SubscriptionRenewalRow(
    sub: SubscriptionEntity,
    today: LocalDate,
    tomorrow: LocalDate
) {
    val isToday    = sub.nextPaymentDate == today
    val isTomorrow = sub.nextPaymentDate == tomorrow
    val dateFmt    = DateTimeFormatter.ofPattern("MMM d")

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(sub.merchantName, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            isToday -> Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.spendAmberBg, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Today", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.spendAmber)
            }
            isTomorrow -> Text("Tomorrow", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted)
            sub.nextPaymentDate != null -> Text(sub.nextPaymentDate.format(dateFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted)
        }
    }
}

// ── Goals pager tile ──────────────────────────────────────────────────────────

@Composable
private fun GoalsPagerCard(
    goals: List<GoalEntity>,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greenColor = MaterialTheme.colorScheme.spendGreen
    val trackColor = MaterialTheme.colorScheme.cardBorder

    PennyWiseCardV2(
        modifier = modifier.defaultMinSize(minHeight = 220.dp),
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        if (goals.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null,
                    tint = greenColor, modifier = Modifier.size(15.dp))
                Text("GOALS", style = MaterialTheme.typography.labelSmall,
                    color = greenColor, letterSpacing = 0.66.sp)
            }
            Text("Set a savings goal\nto get started",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
        } else {
            val pagerState = rememberPagerState(pageCount = { goals.size })

            // Header: icon + label + animated dots
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null,
                    tint = greenColor, modifier = Modifier.size(15.dp))
                Text("GOALS", style = MaterialTheme.typography.labelSmall,
                    color = greenColor, letterSpacing = 0.66.sp,
                    modifier = Modifier.weight(1f))
                if (goals.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        repeat(goals.size) { i ->
                            val isActive = pagerState.currentPage == i
                            val dotSize by animateDpAsState(
                                targetValue = if (isActive) 6.dp else 4.dp,
                                animationSpec = tween(200), label = "dot$i")
                            Box(modifier = Modifier.size(dotSize).background(
                                if (isActive) greenColor else MaterialTheme.colorScheme.cardBorder,
                                CircleShape))
                        }
                    }
                }
            }

            // Fixed height on pager is required — SubcomposeLayout can't provide
            // intrinsic height, so a fixed size short-circuits the query safely.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(165.dp)
            ) { page ->
                val goal = goals[page]
                val progress = if (goal.targetAmount > BigDecimal.ZERO)
                    (goal.currentAmount.toFloat() / goal.targetAmount.toFloat() * 100)
                        .toInt().coerceIn(0, 100)
                else 0
                val rawOffset = (pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction
                val pageOffset = rawOffset.absoluteValue

                Column(
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        alpha  = lerp(0.65f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        scaleX = lerp(0.88f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        scaleY = lerp(0.88f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GoalDonutContent(goal, progress, currency, greenColor, trackColor)
                }
            }
        }
    }
}

@Composable
private fun GoalDonutContent(
    goal: GoalEntity,
    progress: Int,
    currency: String,
    greenColor: androidx.compose.ui.graphics.Color,
    trackColor: androidx.compose.ui.graphics.Color
) {
    val dateFmt = DateTimeFormatter.ofPattern("MMM yyyy")

    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val inset  = strokeWidth / 2f
            val arcSz  = Size(size.width - strokeWidth, size.height - strokeWidth)
            val arcOff = Offset(inset, inset)
            drawArc(trackColor, -90f, 360f, false, arcOff, arcSz,
                style = Stroke(strokeWidth, cap = StrokeCap.Round))
            val sweep = (progress / 100f) * 360f
            if (sweep > 0f)
                drawArc(greenColor, -90f, sweep, false, arcOff, arcSz,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Text("$progress%", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }

    Spacer(Modifier.height(Spacing.sm))

    Text(goal.name, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(Spacing.xs))

    Text(CurrencyFormatter.formatCurrency(goal.currentAmount, currency),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    Text(
        text = "of ${CurrencyFormatter.formatCurrency(goal.targetAmount, currency)} · ${goal.targetDate.format(dateFmt)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.textMuted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

