package com.pennywiseai.tracker.presentation.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.GoalStatus
import com.pennywiseai.tracker.domain.model.GoalProgress
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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
            FloatingActionButton(
                onClick = onCreateGoal,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New goal")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (uiState.activeGoals.isEmpty() && uiState.archivedGoals.isEmpty()) {
                item { GoalsEmptyState(onCreateGoal = onCreateGoal) }
            } else {
                if (uiState.activeGoals.isNotEmpty()) {
                    item {
                        GoalsSummaryCard(
                            activeCount = uiState.activeGoals.size,
                            totalTarget = uiState.totalTargetAmount,
                            totalCurrent = uiState.totalCurrentAmount,
                            currency = uiState.currency
                        )
                    }
                    items(uiState.activeGoals, key = { it.goal.id }) { goalProgress ->
                        GoalCard(
                            goalProgress = goalProgress,
                            onClick = { onGoalClick(goalProgress.goal.id) }
                        )
                    }
                }

                if (uiState.archivedGoals.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleShowArchived() }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Archived (${uiState.archivedGoals.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (uiState.showArchived) "Hide" else "Show",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
}

@Composable
private fun GoalsSummaryCard(
    activeCount: Int,
    totalTarget: java.math.BigDecimal,
    totalCurrent: java.math.BigDecimal,
    currency: String
) {
    val progress = if (totalTarget > java.math.BigDecimal.ZERO)
        (totalCurrent.toFloat() / totalTarget.toFloat()).coerceIn(0f, 1f)
    else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$activeCount active ${if (activeCount == 1) "goal" else "goals"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(progress * 100).toInt()}% overall",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = CurrencyFormatter.formatCurrency(totalCurrent, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "of ${CurrencyFormatter.formatCurrency(totalTarget, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun GoalCard(
    goalProgress: GoalProgress,
    onClick: () -> Unit,
    dimmed: Boolean = false
) {
    val goal = goalProgress.goal
    val alpha = if (dimmed) 0.6f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha)
        )
    ) {
        Column(modifier = Modifier.padding(Dimensions.Padding.content)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Text(
                        text = goal.goalType.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }
                if (goal.status == GoalStatus.COMPLETED) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { goalProgress.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = parseGoalColor(goal.color),
                trackColor = parseGoalColor(goal.color).copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = CurrencyFormatter.formatCurrency(goal.currentAmount, goal.currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = "${goalProgress.progressPercent.toInt()}% · ${daysLabel(goalProgress.daysRemaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(goal.targetAmount, goal.currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
    }
}

@Composable
private fun GoalsEmptyState(onCreateGoal: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = "No goals yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Set a savings goal and track your progress",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Button(onClick = onCreateGoal) {
            Text("Create a Goal")
        }
    }
}

private fun com.pennywiseai.tracker.data.database.entity.GoalType.displayName(): String = when (this) {
    com.pennywiseai.tracker.data.database.entity.GoalType.SAVINGS -> "Savings"
    com.pennywiseai.tracker.data.database.entity.GoalType.EMERGENCY_FUND -> "Emergency Fund"
    com.pennywiseai.tracker.data.database.entity.GoalType.PURCHASE -> "Purchase"
    com.pennywiseai.tracker.data.database.entity.GoalType.VACATION -> "Vacation"
    com.pennywiseai.tracker.data.database.entity.GoalType.DEBT_PAYOFF -> "Debt Payoff"
    com.pennywiseai.tracker.data.database.entity.GoalType.INVESTMENT -> "Investment"
    com.pennywiseai.tracker.data.database.entity.GoalType.CUSTOM -> "Custom"
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
