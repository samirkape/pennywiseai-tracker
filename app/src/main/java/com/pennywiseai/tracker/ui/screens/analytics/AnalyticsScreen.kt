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
        bankName: String?,
        accountLast4: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onNavigateToTransactionsMultiCategory: (categories: String, period: String?, currency: String?, startDateEpochDay: Long?, endDateEpochDay: Long?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToTransaction: (Long) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToBehavioralStats: () -> Unit = {},
    onNavigateToBreakdown: (tileKey: String) -> Unit = {},
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
    val compactAnalyticsCards by viewModel.compactAnalyticsCards.collectAsStateWithLifecycle()
    // Use rememberSaveable to preserve UI state across navigation
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var categoryViewType by rememberSaveable { mutableStateOf(CategoryViewType.LIST) }
    var showChartTypeSelector by remember { mutableStateOf(false) }
    var activeTileKey by remember { mutableStateOf("outflow") }

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
        bankName: String? = null,
        accountLast4: String? = null,
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
            bankName,
            accountLast4,
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
            uiState.periodOutflow != null ||
            uiState.investmentInsights != null ||
            uiState.paymentModeBreakdown != null
        ) {
            item {
                AnalyticsSummaryTilesRow(
                    currency = uiState.currency,
                    periodOutflow = uiState.periodOutflow,
                    investmentInsights = uiState.investmentInsights,
                    paymentModeBreakdown = uiState.paymentModeBreakdown,
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
                    onSpendingBreakdownClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.EXPENSE.name,
                        )
                    },
                    onTileDetailClick = if (compactAnalyticsCards) {
                        { tileKey -> onNavigateToBreakdown(tileKey) }
                    } else {
                        null
                    },
                    onTileChanged = { activeTileKey = it },
                    showInlineBreakdown = !compactAnalyticsCards,
                )
            }

            val spendByAccountList = uiState.accountBreakdowns[activeTileKey]
            if (!compactAnalyticsCards && !spendByAccountList.isNullOrEmpty()) {
                item {
                    val accountTransactionType = when (activeTileKey) {
                        "investments" -> TransactionTypeFilter.INVESTMENT.name
                        "spending" -> TransactionTypeFilter.EXPENSE.name
                        // outflow and card_and_bank tiles span multiple types — use ALL
                        else -> TransactionTypeFilter.ALL.name
                    }
                    AccountSpendTile(
                        accounts = spendByAccountList,
                        currency = uiState.currency,
                        onAccountClick = { bankName, accountLast4, _ ->
                            drillDownToTransactions(
                                transactionType = accountTransactionType,
                                bankName = bankName,
                                accountLast4 = accountLast4,
                            )
                        },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
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
