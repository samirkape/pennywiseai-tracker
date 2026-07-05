package com.spendly.tracker.presentation.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.GoalStatus
import com.spendly.tracker.data.database.entity.GoalType
import com.spendly.tracker.domain.model.GoalProgress
import com.spendly.tracker.ui.components.PennyWiseEmptyState
import com.spendly.tracker.ui.components.cards.PennyWiseCardV2
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.cards.SummaryCardV2
import com.spendly.tracker.ui.components.shimmer
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onNavigateBack: () -> Unit = {},
    onGoalClick: (Long) -> Unit = {},
    onCreateGoal: () -> Unit = {},
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(600)
            hasAnimated = true
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Financial Goals",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = onCreateGoal,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "New goal")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            if (uiState.isLoading) {
                items(3) { GoalCardSkeleton() }
                return@LazyColumn
            }

            if (uiState.activeGoals.isEmpty() && uiState.archivedGoals.isEmpty()) {
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Default.EmojiEvents,
                        headline = "No goals yet",
                        description = "Set a savings goal and track your progress toward what matters most",
                        actionLabel = "Create a Goal",
                        onAction = onCreateGoal
                    )
                }
                return@LazyColumn
            }

            if (uiState.activeGoals.isNotEmpty()) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(0L); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        GoalsSummaryCard(
                            activeCount = uiState.activeGoals.size,
                            totalTarget = uiState.totalTargetAmount,
                            totalCurrent = uiState.totalCurrentAmount,
                            totalDailySavingsNeeded = uiState.totalDailySavingsNeeded,
                            currency = uiState.currency
                        )
                    }
                }

                item { SectionHeaderV2(title = "Active Goals") }

                itemsIndexed(
                    items = uiState.activeGoals,
                    key = { _, item -> item.goal.id }
                ) { index, goalProgress ->
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay((index + 1) * 50L); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        GoalCard(
                            goalProgress = goalProgress,
                            onClick = { onGoalClick(goalProgress.goal.id) }
                        )
                    }
                }
            }

            if (uiState.archivedGoals.isNotEmpty()) {
                item {
                    SectionHeaderV2(
                        title = "Archived (${uiState.archivedGoals.size})",
                        action = {
                            TextButton(onClick = { viewModel.toggleShowArchived() }) {
                                Text(
                                    text = if (uiState.showArchived) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )
                }
                if (uiState.showArchived) {
                    items(uiState.archivedGoals, key = { "arch_${it.goal.id}" }) { goalProgress ->
                        GoalCard(
                            goalProgress = goalProgress,
                            onClick = { onGoalClick(goalProgress.goal.id) },
                            dimmed = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsSummaryCard(
    activeCount: Int,
    totalTarget: java.math.BigDecimal,
    totalCurrent: java.math.BigDecimal,
    totalDailySavingsNeeded: java.math.BigDecimal,
    currency: String
) {
    val progress = if (totalTarget > java.math.BigDecimal.ZERO)
        (totalCurrent.toFloat() / totalTarget.toFloat()).coerceIn(0f, 1f)
    else 0f

    val subtitle = buildString {
        append("of ${CurrencyFormatter.formatCurrency(totalTarget, currency)} target")
        if (totalDailySavingsNeeded > java.math.BigDecimal.ZERO) {
            append(" · ${CurrencyFormatter.formatCurrency(totalDailySavingsNeeded, currency)}/day")
        }
    }

    SummaryCardV2(
        title = "$activeCount active ${if (activeCount == 1) "goal" else "goals"}",
        amount = CurrencyFormatter.formatCurrency(totalCurrent, currency),
        subtitle = subtitle,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        sparklineSlot = {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            )
        }
    )
}

@Composable
fun GoalCard(
    goalProgress: GoalProgress,
    onClick: () -> Unit,
    dimmed: Boolean = false
) {
    val goal = goalProgress.goal
    val alpha = if (dimmed) 0.55f else 1f
    val goalColor = parseGoalColor(goal.color)

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        onClick = onClick
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(goalColor.copy(alpha = if (dimmed) 0.08f else 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = goal.goalType.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        tint = goalColor.copy(alpha = alpha)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        when (goal.status) {
                            GoalStatus.COMPLETED -> Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                            GoalStatus.PAUSED -> Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.padding(start = Spacing.xs)
                            ) {
                                Text(
                                    text = "Paused",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                                )
                            }
                            else -> Unit
                        }
                    }
                    Text(
                        text = "${goal.goalType.displayName(goal.customTypeName)} · ${daysLabel(goalProgress.daysRemaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(goal.currentAmount, goal.currency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Text(
                        text = "${goalProgress.progressPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { goalProgress.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = goalColor.copy(alpha = alpha),
                trackColor = goalColor.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun GoalCardSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(placeholderColor)
                        .shimmer()
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                            .background(placeholderColor)
                            .shimmer()
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                            .background(placeholderColor)
                            .shimmer()
                    )
                }
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                        .background(placeholderColor)
                        .shimmer()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(placeholderColor)
                    .shimmer()
            )
        }
    }
}

private fun GoalType.displayName(customTypeName: String? = null): String = when (this) {
    GoalType.SAVINGS -> "Savings"
    GoalType.EMERGENCY_FUND -> "Emergency Fund"
    GoalType.PURCHASE -> "Purchase"
    GoalType.VACATION -> "Vacation"
    GoalType.DEBT_PAYOFF -> "Debt Payoff"
    GoalType.INVESTMENT -> "Investment"
    GoalType.CUSTOM -> customTypeName?.takeIf { it.isNotBlank() } ?: "Custom"
}

private fun GoalType.icon(): ImageVector = when (this) {
    GoalType.SAVINGS -> Icons.Default.Savings
    GoalType.EMERGENCY_FUND -> Icons.Default.HealthAndSafety
    GoalType.PURCHASE -> Icons.Default.ShoppingBag
    GoalType.VACATION -> Icons.Default.BeachAccess
    GoalType.DEBT_PAYOFF -> Icons.Default.CreditCard
    GoalType.INVESTMENT -> Icons.Default.TrendingUp
    GoalType.CUSTOM -> Icons.Default.Star
}

internal fun parseGoalColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF4CAF50)
    }
}

private fun daysLabel(daysRemaining: Int): String = when {
    daysRemaining < 0 -> "Overdue"
    daysRemaining == 0 -> "Today"
    daysRemaining == 1 -> "1 day left"
    daysRemaining < 30 -> "$daysRemaining days left"
    daysRemaining < 365 -> "${daysRemaining / 30}mo left"
    else -> "${daysRemaining / 365}yr left"
}
