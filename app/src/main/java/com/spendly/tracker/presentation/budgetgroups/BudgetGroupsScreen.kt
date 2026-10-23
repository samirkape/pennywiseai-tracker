package com.spendly.tracker.presentation.budgetgroups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spendly.tracker.data.repository.BudgetGroupSpending
import com.spendly.tracker.data.repository.BudgetOverallSummary
import com.spendly.tracker.ui.components.CategoryIcon
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.ui.utils.LocalWindowSizeInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.SolidColor
import com.spendly.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.*
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetGroupsScreen(
    viewModel: BudgetGroupsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToGroupEdit: (Long) -> Unit = {},
    onNavigateToCategory: (category: String, yearMonth: String, currency: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

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
                title = "Budgets",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            if (uiState.hasGroups) {
                FloatingActionButton(onClick = { onNavigateToGroupEdit(-1L) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Budget")
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (!uiState.hasGroups) {
            EmptyBudgetState(
                modifier = Modifier.hazeSource(hazeState).background(MaterialTheme.colorScheme.background).padding(paddingValues),
                onSmartDefaults = { viewModel.runSmartDefaults() },
                onCreateNew = { onNavigateToGroupEdit(-1L) }
            )
        } else {
            BudgetGroupsContent(
                modifier = Modifier.hazeSource(hazeState).background(MaterialTheme.colorScheme.background),
                topPadding = paddingValues.calculateTopPadding(),
                uiState = uiState,
                onPreviousMonth = { viewModel.selectPreviousMonth() },
                onNextMonth = { viewModel.selectNextMonth() },
                onToggleMonthMode = { viewModel.toggleMonthViewMode() },
                onGroupClick = { groupId -> onNavigateToGroupEdit(groupId) },
                onDeleteGroup = { groupId -> viewModel.deleteGroup(groupId) },
                onMoveGroupUp = { groupId -> viewModel.moveGroupUp(groupId) },
                onMoveGroupDown = { groupId -> viewModel.moveGroupDown(groupId) },
                onCategoryClick = { category ->
                    val now = java.time.YearMonth.now()
                    val isCurrentMonth =
                        uiState.selectedYear == now.year && uiState.selectedMonth == now.monthValue
                    val period = if (uiState.useFinancialMonth && isCurrentMonth) {
                        com.spendly.tracker.presentation.common.TimePeriod.THIS_MONTH.name
                    } else {
                        "%04d-%02d".format(uiState.selectedYear, uiState.selectedMonth)
                    }
                    onNavigateToCategory(category, period, uiState.currency)
                }
            )
        }
    }
}

@Composable
private fun EmptyBudgetState(
    modifier: Modifier = Modifier,
    onSmartDefaults: () -> Unit,
    onCreateNew: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        SpendlyCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            contentPadding = Dimensions.Padding.empty
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Set Up Your Budget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Organize your spending into budgets to track where your money goes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onSmartDefaults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Use Smart Defaults")
                }

                OutlinedButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Custom Budget")
                }
            }
        }
    }
}

@Composable
private fun BudgetGroupsContent(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    uiState: BudgetGroupsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleMonthMode: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onMoveGroupUp: (Long) -> Unit,
    onMoveGroupDown: (Long) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val summary = uiState.summary ?: return
    val isCurrentMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth) == YearMonth.now()
    val groupCount = summary.groups.size
    var deleteGroupId by remember { mutableStateOf<Long?>(null) }
    var deleteGroupName by remember { mutableStateOf("") }

    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(350)
            hasAnimated = true
        }
    }

    val lazyListState = rememberLazyListState()
    val windowSizeInfo = LocalWindowSizeInfo.current
    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            top = Dimensions.Padding.content + topPadding,
            bottom = windowSizeInfo.bottomNavBarPadding + Spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
    ) {
        // Month Selector
        item {
            val visible = remember { mutableStateOf(hasAnimated) }
            LaunchedEffect(Unit) {
                if (!hasAnimated) { delay(0); visible.value = true }
            }
            AnimatedVisibility(
                visible = visible.value,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { slideOffsetPx },
                    animationSpec = tween(300)
                )
            ) {
                MonthSelector(
                    periodLabel = uiState.periodLabel,
                    isCurrentMonth = isCurrentMonth,
                    useFinancialMonth = uiState.useFinancialMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onToggleMode = onToggleMonthMode
                )
            }
        }

        // Hero Summary Tile
        item {
            val visible = remember { mutableStateOf(hasAnimated) }
            LaunchedEffect(Unit) {
                if (!hasAnimated) { delay(50); visible.value = true }
            }
            AnimatedVisibility(
                visible = visible.value,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { slideOffsetPx },
                    animationSpec = tween(300)
                )
            ) {
                BudgetHeroTile(summary = summary, currency = uiState.currency)
            }
        }

        if (groupCount > 0) {
            item {
                Text(
                    text = "Your budgets",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        }

        // Budget Cards
        itemsIndexed(
            items = summary.groups,
            key = { _, group -> group.group.budget.id }
        ) { index, groupSpending ->
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
                BudgetCard(
                    groupSpending = groupSpending,
                    currency = uiState.currency,
                    isFirst = index == 0,
                    isLast = index == groupCount - 1,
                    onClick = { onGroupClick(groupSpending.group.budget.id) },
                    onDelete = {
                        deleteGroupId = groupSpending.group.budget.id
                        deleteGroupName = groupSpending.group.budget.name
                    },
                    onMoveUp = { onMoveGroupUp(groupSpending.group.budget.id) },
                    onMoveDown = { onMoveGroupDown(groupSpending.group.budget.id) },
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    // Delete confirmation dialog
    if (deleteGroupId != null) {
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text("Delete Budget") },
            text = { Text("Are you sure you want to delete \"$deleteGroupName\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteGroupId?.let { onDeleteGroup(it) }
                        deleteGroupId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(
    periodLabel: String,
    isCurrentMonth: Boolean,
    useFinancialMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleMode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous month",
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            FilledTonalIconButton(
                onClick = onNext,
                enabled = !isCurrentMonth,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Next month",
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
            SegmentedButton(
                selected = useFinancialMonth,
                onClick = { if (!useFinancialMonth) onToggleMode() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Pay Period", style = MaterialTheme.typography.labelSmall) }
            )
            SegmentedButton(
                selected = !useFinancialMonth,
                onClick = { if (useFinancialMonth) onToggleMode() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Calendar", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val budget = groupSpending.group.budget
    var expanded by remember { mutableStateOf(false) }

    val pctUsed = groupSpending.percentageUsed
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO

    val statusColor: Color = budgetStatusColor(pctUsed)

    SpendlyCardV2(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            // Row 1: Budget name + semantic status chip + single overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = budget.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(Spacing.sm))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (groupSpending.totalBudget > BigDecimal.ZERO) {
                        BudgetStatusChip(status = budgetStatusFor(pctUsed))
                    }
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Budget options",
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                onClick = {
                                    showMenu = false
                                    onMoveUp()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                                },
                                enabled = !isFirst
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                onClick = {
                                    showMenu = false
                                    onMoveDown()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                },
                                enabled = !isLast
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(Spacing.sm))

                // Row 2: Usage gauge (capped at 100%; over-budget state is carried by
                // the tinted track, status chip and the amounts below)
                BudgetGauge(percentageUsed = pctUsed)

                Spacer(modifier = Modifier.height(Spacing.md))

                // Row 3: Remaining amount (hero)
                val remainingAbs = groupSpending.remaining.abs()
                Text(
                    text = if (isOverBudget) {
                        "${CurrencyFormatter.formatCurrency(remainingAbs, currency)} over budget"
                    } else {
                        "${CurrencyFormatter.formatCurrency(groupSpending.remaining.coerceAtLeast(BigDecimal.ZERO), currency)} remaining"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Row 4: Single supporting caption - spent, budget and pace in one line
                val spentOfBudget =
                    "Spent ${CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)} of ${CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)}"
                val subtitleText = when {
                    isOverBudget -> "$spentOfBudget \u00B7 ${pctUsed.toInt()}% used"
                    groupSpending.daysRemaining == 0 -> "$spentOfBudget \u00B7 Period ended"
                    else -> "$spentOfBudget \u00B7 ${CurrencyFormatter.formatCurrency(groupSpending.dailyAllowance, currency)}/day \u00B7 ${groupSpending.daysRemaining} days left"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (groupSpending.isTrackingAllExpenses) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Spent ${CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Tracking all expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable category list + pace chart
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Per-budget spending pace chart
                    if (groupSpending.dailyCumulativeSpending.size >= 2 && groupSpending.dailyBudgetPace.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        SpendingPaceChart(
                            cumulativeSpending = groupSpending.dailyCumulativeSpending,
                            budgetPace = groupSpending.dailyBudgetPace,
                            currency = currency
                        )
                    }

                    if (groupSpending.categorySpending.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    groupSpending.categorySpending.forEach { catSpending ->
                        val catPctUsed = catSpending.percentageUsed
                        val catStatusColor: Color = budgetStatusColor(catPctUsed)

                        val categoryInfo = CategoryMapping.categories[catSpending.categoryName]
                            ?: CategoryMapping.categories["Others"]!!

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Spacing.sm))
                                .clickable { onCategoryClick(catSpending.categoryName) }
                                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Category icon in colored circle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(categoryInfo.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryIcon(
                                    category = catSpending.categoryName,
                                    size = 18.dp,
                                    tint = categoryInfo.color
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = catSpending.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (catSpending.budgetAmount > BigDecimal.ZERO) {
                                        Text(
                                            text = "${catPctUsed.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = catStatusColor
                                        )
                                    }
                                }

                                if (catSpending.budgetAmount > BigDecimal.ZERO) {
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    val catBarShape = RoundedCornerShape(50)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(catBarShape)
                                            .background(catStatusColor.copy(alpha = 0.15f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = (catPctUsed / 100f).coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .clip(catBarShape)
                                                .background(catStatusColor)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = "${CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency)} of ${CurrencyFormatter.formatCurrency(catSpending.budgetAmount, currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/** Semantic usage state shared by chips, gauges and hero colors. */
private enum class BudgetStatus(val label: String) {
    ON_TRACK("On track"),
    NEAR_LIMIT("Near limit"),
    BREACHED("Breached")
}

private fun budgetStatusFor(percentageUsed: Float): BudgetStatus = when {
    percentageUsed >= 100f -> BudgetStatus.BREACHED
    percentageUsed >= 80f -> BudgetStatus.NEAR_LIMIT
    else -> BudgetStatus.ON_TRACK
}

@Composable
private fun budgetStatusColor(percentageUsed: Float): Color = when (budgetStatusFor(percentageUsed)) {
    BudgetStatus.BREACHED -> MaterialTheme.colorScheme.error
    BudgetStatus.NEAR_LIMIT -> MaterialTheme.colorScheme.tertiary
    BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
}

@Composable
private fun BudgetStatusChip(
    status: BudgetStatus,
    modifier: Modifier = Modifier
) {
    StatusPill(text = status.label, status = status, modifier = modifier)
}

/**
 * Tonal pill carrying both the usage magnitude and the semantic status color,
 * e.g. "325% used" in the breach tint. Replaces bare percentages so the number
 * always has an interpretation.
 */
@Composable
private fun PercentUsedChip(
    percentageUsed: Float,
    modifier: Modifier = Modifier
) {
    StatusPill(
        text = "${percentageUsed.toInt()}% used",
        status = budgetStatusFor(percentageUsed),
        modifier = modifier
    )
}

@Composable
private fun StatusPill(
    text: String,
    status: BudgetStatus,
    modifier: Modifier = Modifier
) {
    val containerColor = when (status) {
        BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primaryContainer
        BudgetStatus.NEAR_LIMIT -> MaterialTheme.colorScheme.tertiaryContainer
        BudgetStatus.BREACHED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.onPrimaryContainer
        BudgetStatus.NEAR_LIMIT -> MaterialTheme.colorScheme.onTertiaryContainer
        BudgetStatus.BREACHED -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
        )
    }
}

/**
 * Usage gauge capped at 100%. Once a budget is breached the whole track tints to the
 * breach color so the "exceeded" state reads instantly; the magnitude of the breach is
 * carried by the amounts and the "% used" label rather than an unbounded bar.
 */
@Composable
private fun BudgetGauge(
    percentageUsed: Float,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp
) {
    val statusColor = budgetStatusColor(percentageUsed)
    val isBreached = percentageUsed >= 100f

    var targetProgress by remember(percentageUsed) { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 800),
        label = "gaugeProgress"
    )
    LaunchedEffect(percentageUsed) {
        targetProgress = (percentageUsed / 100f).coerceIn(0f, 1f)
    }

    val barShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(barShape)
            .background(statusColor.copy(alpha = if (isBreached) 0.22f else 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = animatedProgress)
                .fillMaxHeight()
                .clip(barShape)
                .background(statusColor)
        )
    }
}

@Composable
private fun BudgetHeroTile(
    summary: BudgetOverallSummary,
    currency: String,
    modifier: Modifier = Modifier
) {
    val hasLimitBudgets = summary.totalLimitBudget > BigDecimal.ZERO
    val overallPct = if (hasLimitBudgets) {
        (summary.totalLimitSpent.toFloat() / summary.totalLimitBudget.toFloat() * 100f).coerceAtLeast(0f)
    } else 0f
    val isOverBudget = hasLimitBudgets && summary.totalLimitSpent > summary.totalLimitBudget
    val statusColor: Color = budgetStatusColor(overallPct)
    val hasSavings = summary.savingsRate > 0f || summary.netSavings > BigDecimal.ZERO
    val hasDailyAllowance = summary.dailyAllowance > BigDecimal.ZERO

    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    val onHeroMuted = onHero.copy(alpha = 0.72f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overall budget",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onHero
                )
                if (hasLimitBudgets) {
                    PercentUsedChip(percentageUsed = overallPct)
                }
            }

            if (hasLimitBudgets) {
                val remainingAbs = (summary.totalLimitBudget - summary.totalLimitSpent).abs()
                // Amount and suffix form one typographic unit; the suffix stays small
                // so a long amount never wraps into two lines of display type
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isOverBudget) {
                            CurrencyFormatter.formatCurrency(remainingAbs, currency)
                        } else {
                            CurrencyFormatter.formatCurrency(summary.totalLimitBudget - summary.totalLimitSpent, currency)
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isOverBudget) "over budget" else "remaining",
                        style = MaterialTheme.typography.titleSmall,
                        color = onHeroMuted
                    )
                }

                Text(
                    text = "Spent ${CurrencyFormatter.formatCurrency(summary.totalLimitSpent, currency)} of ${CurrencyFormatter.formatCurrency(summary.totalLimitBudget, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHeroMuted
                )

                if (hasSavings || hasDailyAllowance) {
                    HorizontalDivider(color = onHero.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        if (hasSavings) {
                            HeroStat(
                                label = "Net saved",
                                value = CurrencyFormatter.formatCurrency(summary.netSavings, currency),
                                sub = "${summary.savingsRate.toInt()}% saved",
                                valueColor = onHero,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hasDailyAllowance) {
                            HeroStat(
                                label = "Daily left",
                                value = CurrencyFormatter.formatCurrency(summary.dailyAllowance, currency),
                                sub = "${summary.daysRemaining} days left",
                                valueColor = onHero,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Slim usage accent anchoring the card
                BudgetGauge(percentageUsed = overallPct)
            }
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun SpendingPaceChart(
    cumulativeSpending: List<Double>,
    budgetPace: List<Double>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val themeColors = MaterialTheme.colorScheme
    val isOverPace = cumulativeSpending.lastOrNull()?.let { actual ->
        budgetPace.lastOrNull()?.let { pace -> actual > pace }
    } ?: false
    val spendingColor = if (isOverPace) themeColors.error else themeColors.primary

    Column(modifier = modifier.fillMaxWidth()) {
            LineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                data = listOf(
                    Line(
                        label = "Actual",
                        values = cumulativeSpending,
                        color = SolidColor(spendingColor),
                        firstGradientFillColor = spendingColor.copy(alpha = 0.2f),
                        secondGradientFillColor = Color.Transparent,
                        strokeAnimationSpec = tween(1200),
                        gradientAnimationDelay = 600,
                        drawStyle = DrawStyle.Stroke(width = 2.5.dp),
                        curvedEdges = true,
                        dotProperties = DotProperties(
                            enabled = false
                        )
                    ),
                    Line(
                        label = "Budget Pace",
                        values = budgetPace,
                        color = SolidColor(themeColors.onSurfaceVariant.copy(alpha = 0.4f)),
                        drawStyle = DrawStyle.Stroke(width = 1.5.dp),
                        strokeAnimationSpec = tween(1200),
                        curvedEdges = false,
                        dotProperties = DotProperties(enabled = false)
                    )
                ),
                dividerProperties = DividerProperties(enabled = false),
                indicatorProperties = HorizontalIndicatorProperties(
                    enabled = true,
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        color = themeColors.onSurfaceVariant.copy(0.7f)
                    ),
                    contentBuilder = { value ->
                        CurrencyFormatter.formatAbbreviated(value, currency)
                    }
                ),
                labelHelperProperties = LabelHelperProperties(enabled = false),
                labelProperties = LabelProperties(enabled = false),
                gridProperties = GridProperties(
                    enabled = true,
                    xAxisProperties = GridProperties.AxisProperties(
                        enabled = false
                    ),
                    yAxisProperties = GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.08f))
                    )
                ),
                animationMode = AnimationMode.Together(delayBuilder = { it * 100L }),
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(spendingColor)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "Actual",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(themeColors.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "Budget Pace",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.onSurfaceVariant
                )
            }
        }
}
