package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.presentation.common.PaymentMode
import com.pennywiseai.tracker.presentation.common.PaymentModeGroup
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.ui.components.PeriodRangeNavigator
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.ListItemCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class CategoryViewType { CHART, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onNavigateToTransactions: (
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        transactionType: String?,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?,
        paymentMode: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onNavigateToTransactionsMultiCategory: (categories: String, period: String?, currency: String?, startDateEpochDay: Long?, endDateEpochDay: Long?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToTransaction: (Long) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToBehavioralStats: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val availableCurrencies by viewModel.availableCurrencies.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsStateWithLifecycle()
    val useFinancialMonth by viewModel.useFinancialMonth.collectAsStateWithLifecycle()
    val periodAnchorMonth by viewModel.periodAnchorMonth.collectAsStateWithLifecycle()
    val chartType by viewModel.selectedChartType.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    // Use rememberSaveable to preserve UI state across navigation
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var categoryViewType by rememberSaveable { mutableStateOf(CategoryViewType.LIST) }
    var showChartTypeSelector by remember { mutableStateOf(false) }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // Calculate active filter count (transaction type + category filter)
    val activeFilterCount = (if (transactionTypeFilter != TransactionTypeFilter.EXPENSE) 1 else 0) +
        (if (categoryFilter != null) 1 else 0)

    // Cache expensive operations — include CALENDAR_MONTH only when financial month is enabled
    val timePeriods = remember(useFinancialMonth) {
        if (useFinancialMonth) {
            listOf(
                TimePeriod.THIS_MONTH,
                TimePeriod.CALENDAR_MONTH,
                TimePeriod.LAST_MONTH,
                TimePeriod.CURRENT_FY,
                TimePeriod.ALL,
                TimePeriod.CUSTOM
            )
        } else {
            listOf(
                TimePeriod.THIS_MONTH,
                TimePeriod.LAST_MONTH,
                TimePeriod.CURRENT_FY,
                TimePeriod.ALL,
                TimePeriod.CUSTOM
            )
        }
    }
    val customRangeLabel = remember(customDateRange) {
        DateRangeUtils.formatDateRange(customDateRange)
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

    val drillDownPeriodEpochs: Pair<Long?, Long?> = remember(activePeriodRange) {
        val range = activePeriodRange
        if (range == null) null to null else range.first.toEpochDay() to range.second.toEpochDay()
    }

    fun drillDownToTransactions(
        category: String? = null,
        merchant: String? = null,
        transactionType: String? = transactionTypeFilter.name,
        paymentMode: String? = null,
    ) {
        onNavigateToTransactions(
            category,
            merchant,
            TimePeriod.CUSTOM.name,
            selectedCurrency,
            transactionType,
            drillDownPeriodEpochs.first,
            drillDownPeriodEpochs.second,
            paymentMode,
        )
    }

    fun drillDownMultiCategory(encodedCategories: String) {
        onNavigateToTransactionsMultiCategory(
            encodedCategories,
            TimePeriod.CUSTOM.name,
            selectedCurrency,
            drillDownPeriodEpochs.first,
            drillDownPeriodEpochs.second,
        )
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = scrollBehaviorSmall
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Analytics",
                hazeState = hazeState,
                hasActionButton = true,
                actionContent = {
                    IconButton(onClick = onNavigateToBehavioralStats) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = "Behavioral Stats"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .hazeSource(hazeState)
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
        // Period Selector - Always visible
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(timePeriods) { period ->
                    FilterChip(
                        // Only show CUSTOM as selected if both period is CUSTOM AND dates are set
                        selected = if (period == TimePeriod.CUSTOM) {
                            selectedPeriod == period && customDateRange != null
                        } else {
                            selectedPeriod == period
                        },
                        onClick = {
                            if (period == TimePeriod.CUSTOM) {
                                showDateRangePicker = true
                                // Don't change selectedPeriod until user confirms dates
                            } else {
                                viewModel.selectPeriod(period)
                            }
                        },
                        label = {
                            Text(
                                when {
                                    period == TimePeriod.CUSTOM && customRangeLabel != null -> customRangeLabel
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

        // Currency Selector (if multiple currencies available and not in unified mode)
        if (availableCurrencies.size > 1 && !isUnifiedMode) {
            item {
                CurrencyFilterRow(
                    selectedCurrency = selectedCurrency,
                    availableCurrencies = availableCurrencies,
                    onCurrencySelected = { viewModel.selectCurrency(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Collapsible Transaction Type + Category Filter
        item {
            CollapsibleFilterRow(
                isExpanded = showAdvancedFilters,
                activeFilterCount = activeFilterCount,
                onToggle = { showAdvancedFilters = !showAdvancedFilters },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Transaction Type chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(TransactionTypeFilter.values().toList()) { typeFilter ->
                            FilterChip(
                                selected = transactionTypeFilter == typeFilter,
                                onClick = { viewModel.setTransactionTypeFilter(typeFilter) },
                                label = { Text(typeFilter.label) },
                                leadingIcon = if (transactionTypeFilter == typeFilter) {
                                    {
                                        when (typeFilter) {
                                            TransactionTypeFilter.INCOME -> Icon(
                                                Icons.AutoMirrored.Filled.TrendingUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            TransactionTypeFilter.EXPENSE -> Icon(
                                                Icons.AutoMirrored.Filled.TrendingDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            TransactionTypeFilter.CREDIT -> Icon(
                                                Icons.Default.CreditCard,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            TransactionTypeFilter.TRANSFER -> Icon(
                                                Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            TransactionTypeFilter.CC_BILL_PAYMENT -> Icon(
                                                Icons.Default.Payment,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            TransactionTypeFilter.INVESTMENT -> Icon(
                                                Icons.AutoMirrored.Filled.ShowChart,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            TransactionTypeFilter.EXCLUDED -> Icon(
                                                Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                            else -> null
                                        }
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }

                    // Category filter chips (shown only when categories are available)
                    if (uiState.availableCategories.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            item {
                                FilterChip(
                                    selected = categoryFilter == null,
                                    onClick = { viewModel.clearCategoryFilter() },
                                    label = { Text("All") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                            items(uiState.availableCategories) { category ->
                                FilterChip(
                                    selected = categoryFilter == category,
                                    onClick = {
                                        if (categoryFilter == category) viewModel.clearCategoryFilter()
                                        else viewModel.setCategoryFilter(category)
                                    },
                                    label = { Text(category) },
                                    leadingIcon = {
                                        CategoryIcon(
                                            category = category,
                                            size = Dimensions.Icon.small
                                        )
                                    },
                                    trailingIcon = if (categoryFilter == category) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear category filter",
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (
            uiState.totalSpending > BigDecimal.ZERO ||
            uiState.transactionCount > 0 ||
            uiState.periodOutflow != null ||
            uiState.investmentInsights != null ||
            uiState.paymentModeBreakdown != null
        ) {
            item {
                AnalyticsSummaryTilesRow(
                    spendingTotal = uiState.totalSpending,
                    spendingTransactionCount = uiState.transactionCount,
                    spendingAverage = uiState.averageAmount,
                    spendingTopCategory = uiState.topCategory,
                    spendingTopCategoryPercentage = uiState.topCategoryPercentage,
                    currency = uiState.currency,
                    periodOutflow = uiState.periodOutflow,
                    investmentInsights = uiState.investmentInsights,
                    paymentModeBreakdown = uiState.paymentModeBreakdown,
                    onSpendingClick = { drillDownToTransactions(category = categoryFilter) },
                    onOutflowClick = { selectedTypes ->
                        val typeName = if (selectedTypes.size == 1) {
                            selectedTypes.first().name
                        } else {
                            TransactionTypeFilter.ALL.name
                        }
                        drillDownToTransactions(
                            category = categoryFilter,
                            transactionType = typeName,
                        )
                    },
                    onInvestmentClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.INVESTMENT.name,
                        )
                    },
                    onCardAndBankClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.EXPENSE.name,
                            paymentMode = PaymentModeGroup.CARD_AND_BANK.name,
                        )
                    },
                    onCashClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.EXPENSE.name,
                            paymentMode = PaymentMode.CASH.name,
                        )
                    },
                )
            }
        }

        uiState.investmentInsights?.takeIf { it.topMerchants.isNotEmpty() }?.let { insights ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(title = "Top Investment Merchants")
                    ExpandableList(
                        items = insights.topMerchants,
                        visibleItemCount = 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) { merchant ->
                        AnalyticsMerchantListItem(
                            merchant = merchant,
                            currency = insights.currency,
                            onClick = {
                                drillDownToTransactions(
                                    merchant = merchant.name,
                                    transactionType = TransactionTypeFilter.INVESTMENT.name,
                                )
                            },
                        )
                    }
                }
            }
        }

        // Chart Section with Type Selector
        if (uiState.spendingTrend.size >= 2) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = "Trends",
                        action = {
                            Button(
                                onClick = { showChartTypeSelector = !showChartTypeSelector },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = Spacing.xs)
                            ) {
                                Icon(
                                    imageVector = when (chartType) {
                                        ChartType.LINE -> Icons.AutoMirrored.Filled.ShowChart
                                        ChartType.BAR -> Icons.Default.BarChart
                                        ChartType.HEATMAP -> Icons.Default.GridView
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = when (chartType) {
                                        ChartType.LINE -> "Line"
                                        ChartType.BAR -> "Bar"
                                        ChartType.HEATMAP -> "Heatmap"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )

                    // Expandable chart type selector card
                    AnimatedVisibility(visible = showChartTypeSelector) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.sm),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            ChartType.entries.forEach { type ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.setChartType(type)
                                            showChartTypeSelector = false
                                        }
                                        .padding(horizontal = Spacing.md, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (type) {
                                                ChartType.LINE -> Icons.AutoMirrored.Filled.ShowChart
                                                ChartType.BAR -> Icons.Default.BarChart
                                                ChartType.HEATMAP -> Icons.Default.GridView
                                            },
                                            contentDescription = null,
                                            tint = if (chartType == type)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = when (type) {
                                                ChartType.LINE -> "Line Chart"
                                                ChartType.BAR -> "Bar Chart"
                                                ChartType.HEATMAP -> "Heatmap"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (chartType == type)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (chartType == type) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(Dimensions.Icon.medium)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chart display with crossfade transition
                    Crossfade(
                        targetState = chartType,
                        label = "chart_transition"
                    ) { type ->
                        when (type) {
                            ChartType.LINE -> BalanceChart(
                                primaryCurrency = selectedCurrency,
                                balanceHistory = uiState.spendingTrend,
                                height = 220
                            )
                            ChartType.BAR -> SpendingBarChart(
                                primaryCurrency = selectedCurrency,
                                data = uiState.spendingTrend,
                                height = 220
                            )
                            ChartType.HEATMAP -> SpendingHeatmap(
                                data = uiState.spendingTrend
                            )
                        }
                    }
                }
            }
        }

        // Category Breakdown Section with Pie/List toggle
        if (uiState.categoryBreakdown.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = "Top Categories",
                        action = {
                            IconButton(onClick = {
                                categoryViewType = if (categoryViewType == CategoryViewType.CHART) {
                                    CategoryViewType.LIST
                                } else {
                                    CategoryViewType.CHART
                                }
                            }) {
                                Icon(
                                    imageVector = if (categoryViewType == CategoryViewType.CHART)
                                        Icons.AutoMirrored.Filled.List
                                    else Icons.Default.PieChart,
                                    contentDescription = "Toggle View",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    // Animated content swap
                    AnimatedContent(
                        targetState = categoryViewType,
                        transitionSpec = {
                            if (targetState == CategoryViewType.CHART) {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut()) using
                                    SizeTransform(clip = false)
                            } else {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut()) using
                                    SizeTransform(clip = false)
                            }
                        },
                        label = "category_view_transition"
                    ) { viewType ->
                        when (viewType) {
                            CategoryViewType.CHART -> CategoryPieChart(
                                categories = uiState.categoryBreakdown,
                                currency = selectedCurrency,
                                onCategoryClick = { category ->
                                    drillDownToTransactions(category = category.name)
                                }
                            )
                            CategoryViewType.LIST -> CategoryBreakdownCard(
                                categories = uiState.categoryBreakdown,
                                currency = selectedCurrency,
                                onCategoryClick = { category ->
                                    drillDownToTransactions(category = category.name)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Tag Insights: Top Tags + Co-occurrence + Multi-Tagged in a single pager tile
        if (uiState.topTags.isNotEmpty() || uiState.categoryOverlaps.isNotEmpty() || uiState.multiCategoryTransactions.isNotEmpty()) {
            item {
                TagInsightsCard(
                    topTags = uiState.topTags,
                    overlaps = uiState.categoryOverlaps,
                    multiTaggedTransactions = uiState.multiCategoryTransactions,
                    currency = selectedCurrency,
                    onOverlapClick = { overlap ->
                        val encoded = listOf(overlap.categoryA, overlap.categoryB)
                            .joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
                        drillDownMultiCategory(encoded)
                    },
                    onTransactionClick = onNavigateToTransaction
                )
            }
        }

        // Top Merchants Section
        if (uiState.topMerchants.isNotEmpty()) {
            item {
                SectionHeaderV2(
                    title = "Top Merchants"
                )
            }

            // All Merchants with expandable list
            item {
                ExpandableList(
                    items = uiState.topMerchants,
                    visibleItemCount = 3,
                    modifier = Modifier.fillMaxWidth()
                ) { merchant ->
                    AnalyticsMerchantListItem(
                        merchant = merchant,
                        currency = selectedCurrency,
                        onClick = { drillDownToTransactions(merchant = merchant.name) },
                    )
                }
            }
        }


        // Empty state
        if (uiState.topMerchants.isEmpty() && uiState.categoryBreakdown.isEmpty() && !uiState.isLoading) {
            item {
                EmptyAnalyticsState(onScanSmsClick = onNavigateToHome)
            }
        }
    }
    }

    if (showDateRangePicker) {
        CustomDateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { startDate, endDate ->
                viewModel.setCustomDateRange(startDate, endDate)
                showDateRangePicker = false
            },
            initialStartDate = customDateRange?.first,
            initialEndDate = customDateRange?.second
        )
    }
}

@Composable
private fun CategoryListItem(
    category: CategoryData,
    currency: String
) {
    val categoryInfo = CategoryMapping.categories[category.name]
        ?: CategoryMapping.categories["Others"]!!

    ListItemCardV2(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryInfo.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                CategoryIcon(
                    category = category.name,
                    size = 24.dp,
                    tint = categoryInfo.color
                )
            }
        },
        title = category.name,
        subtitle = "${category.transactionCount} transactions",
        amount = CurrencyFormatter.formatCurrency(category.amount, currency),
        trailingContent = {
            Text(
                text = "${category.percentage.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun EmptyAnalyticsState(
    onScanSmsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            headline = "Not enough data yet",
            description = "Your spending insights will appear here after your first week of tracking",
            actionLabel = "Scan SMS",
            onAction = onScanSmsClick
        )
    }
}

@Composable
private fun TagInsightsCard(
    topTags: List<TagData>,
    overlaps: List<CategoryOverlapData>,
    multiTaggedTransactions: List<MultiCategoryTransactionData>,
    currency: String,
    modifier: Modifier = Modifier,
    onOverlapClick: (CategoryOverlapData) -> Unit = {},
    onTransactionClick: (Long) -> Unit = {}
) {
    data class TagPage(val title: String, val subtitle: String)

    val pages = remember(topTags, overlaps, multiTaggedTransactions) {
        buildList {
            if (topTags.isNotEmpty()) add(TagPage("Top Tags", "${topTags.size} tags used"))
            if (overlaps.isNotEmpty()) add(TagPage("Co-occurrence", "${overlaps.size} tag pairs"))
            if (multiTaggedTransactions.isNotEmpty()) add(TagPage("Multi-Tagged", "${multiTaggedTransactions.size} transactions"))
        }
    }
    if (pages.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeaderV2(title = "Tag Insights")

        // Pager sits outside the card so swipe gestures are not intercepted
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = Dimensions.Padding.content),
            pageSpacing = Spacing.sm
        ) { pageIndex ->
            val page = pages[pageIndex]
            PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Per-page header
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    when (page.title) {
                        "Top Tags" -> TopTagsPageContent(tags = topTags, currency = currency)
                        "Co-occurrence" -> CoOccurrencePageContent(overlaps = overlaps, onOverlapClick = onOverlapClick)
                        "Multi-Tagged" -> MultiTaggedPageContent(transactions = multiTaggedTransactions, currency = currency, onTransactionClick = onTransactionClick)
                    }
                }
            }
        }

        // Pill dot indicators — centered below pager
        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    val isActive = pagerState.currentPage == index
                    val width by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isActive) 16.dp else 6.dp,
                        animationSpec = androidx.compose.animation.core.tween(200),
                        label = "tag_dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(width)
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
}

@Composable
private fun TopTagsPageContent(
    tags: List<TagData>,
    currency: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        tags.take(6).forEach { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${tag.transactionCount} txn${if (tag.transactionCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatAbbreviated(tag.totalAmount.toDouble(), currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CoOccurrencePageContent(
    overlaps: List<CategoryOverlapData>,
    onOverlapClick: (CategoryOverlapData) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        overlaps.take(5).forEach { overlap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOverlapClick(overlap) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background((CategoryMapping.categories[overlap.categoryA]?.color ?: Color.Gray).copy(alpha = 0.15f))
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryIcon(category = overlap.categoryA, size = 16.dp)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background((CategoryMapping.categories[overlap.categoryB]?.color ?: Color.Gray).copy(alpha = 0.15f))
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryIcon(category = overlap.categoryB, size = 14.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${overlap.categoryA} + ${overlap.categoryB}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${overlap.coOccurrenceCount} transactions together",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text(
                        text = "${overlap.coOccurrenceCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiTaggedPageContent(
    transactions: List<MultiCategoryTransactionData>,
    currency: String,
    onTransactionClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        transactions.take(4).forEach { tx ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTransactionClick(tx.transactionId) }
                    .padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    BrandIcon(merchantName = tx.merchantName, size = 36.dp, showBackground = true)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.merchantName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = tx.dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(tx.amount, tx.currency.ifEmpty { currency }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(tx.categories) { cat ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            icon = { CategoryIcon(category = cat, size = 12.dp) },
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyFilterRow(
    selectedCurrency: String,
    availableCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            Text(
                text = "Currency:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    vertical = Spacing.sm,
                    horizontal = Spacing.xs
                )
            )
        }
        items(availableCurrencies) { currency ->
            FilterChip(
                selected = selectedCurrency == currency,
                onClick = { onCurrencySelected(currency) },
                label = { Text(currency) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
