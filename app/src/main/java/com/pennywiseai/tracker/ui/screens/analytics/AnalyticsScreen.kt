package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import com.pennywiseai.tracker.domain.model.InsightType
import com.pennywiseai.tracker.domain.model.SmartInsight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.utils.LocalWindowSizeInfo
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
    onNavigateToInsights: () -> Unit = {},
    onNavigateToCreditCardAnalytics: (startEpoch: Long, endEpoch: Long, currency: String) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsStateWithLifecycle()
    val useFinancialMonth by viewModel.useFinancialMonth.collectAsStateWithLifecycle()
    val periodAnchorMonth by viewModel.periodAnchorMonth.collectAsStateWithLifecycle()
    val chartType by viewModel.selectedChartType.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val compactAnalyticsCards by viewModel.compactAnalyticsCards.collectAsStateWithLifecycle()
    // Use rememberSaveable to preserve UI state across navigation
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var showChartTypeSelector by remember { mutableStateOf(false) }
    var selectedOverviewTab by rememberSaveable { mutableStateOf(AnalyticsOverviewTab.OUTFLOW) }

    val tabs = AnalyticsOverviewTab.entries
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(selectedOverviewTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )

    // Keep pager and tab in sync — use settledPage so intermediate animation pages
    // don't trigger a feedback loop when jumping non-adjacent tabs (e.g. Outflow → Invested).
    LaunchedEffect(pagerState.settledPage) {
        val newTab = tabs.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (newTab != selectedOverviewTab) selectedOverviewTab = newTab
    }
    LaunchedEffect(selectedOverviewTab) {
        val targetPage = tabs.indexOf(selectedOverviewTab).coerceAtLeast(0)
        if (pagerState.settledPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
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
    val canNavigateToNextPeriod = remember(periodAnchorMonth) {
        periodAnchorMonth?.isBefore(YearMonth.now()) == true
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
                    IconButton(onClick = onNavigateToInsights) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Smart Insights"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
    val windowSizeInfo = LocalWindowSizeInfo.current
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
            top = paddingValues.calculateTopPadding() + Spacing.sm,
            bottom = windowSizeInfo.bottomNavBarPadding + Spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberOverscrollFlingBehavior { listState }
    ) {
        item {
            AnalyticsReferencePeriodChipRow(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) },
                onCustomSelected = { showDateRangePicker = true },
            )
        }

        periodAnchorMonth?.let { anchorMonth ->
            if (showPeriodNavigator && periodRangeLabel != null) {
                item {
                    AnalyticsReferenceDateNavigator(
                        label = periodRangeLabel,
                        onPrevious = { viewModel.navigateToMonth(anchorMonth.minusMonths(1)) },
                        onNext = { viewModel.navigateToMonth(anchorMonth.plusMonths(1)) },
                        canGoNext = canNavigateToNextPeriod,
                    )
                }
            }
        }

        item {
            AnalyticsReferenceOverviewTabs(
                selectedTab = selectedOverviewTab,
                onTabSelected = { selectedOverviewTab = it },
            )
        }

        if (uiState.periodOutflow != null || uiState.investmentInsights != null || uiState.paymentModeBreakdown != null) {
            item {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                    AnalyticsReferenceHeroCard(
                        selectedTab = tab,
                        periodOutflow = uiState.periodOutflow,
                        investmentInsights = uiState.investmentInsights,
                        paymentModeBreakdown = uiState.paymentModeBreakdown,
                        currency = selectedCurrency,
                        onTotalClick = {
                            when (tab) {
                                AnalyticsOverviewTab.OUTFLOW -> drillDownToTransactions(transactionType = null)
                                AnalyticsOverviewTab.SPENDING -> drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name)
                                AnalyticsOverviewTab.INVESTED -> drillDownToTransactions(transactionType = TransactionTypeFilter.INVESTMENT.name)
                            }
                        },
                        onMetricClick = { metricIndex ->
                            when (tab) {
                                AnalyticsOverviewTab.OUTFLOW -> when (metricIndex) {
                                    0 -> drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name)
                                    1 -> drillDownToTransactions(transactionType = TransactionTypeFilter.INVESTMENT.name)
                                    2 -> drillDownToTransactions(transactionType = TransactionTypeFilter.CC_BILL_PAYMENT.name)
                                }
                                AnalyticsOverviewTab.SPENDING -> when (metricIndex) {
                                    0 -> {
                                        val (start, end) = drillDownPeriodEpochs
                                        if (start != null && end != null) {
                                            onNavigateToCreditCardAnalytics(start, end, selectedCurrency)
                                        } else {
                                            drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name, paymentMode = "CREDIT_CARD")
                                        }
                                    }
                                    1 -> drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name, paymentMode = "BANK_ACCOUNT")
                                    2 -> drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name, paymentMode = "CASH")
                                }
                                AnalyticsOverviewTab.INVESTED -> drillDownToTransactions(transactionType = TransactionTypeFilter.INVESTMENT.name)
                            }
                        },
                    )
                }
            }
        }

        if (uiState.spendingTrend.size >= 2) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = "Spend trend",
                        action = {
                            Box {
                                TextButton(
                                    onClick = { showChartTypeSelector = !showChartTypeSelector },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = Spacing.xs)
                                ) {
                                    Text(
                                        text = when (chartType) {
                                            ChartType.LINE -> "Line"
                                            ChartType.BAR -> "Bar"
                                            ChartType.HEATMAP -> "Heatmap"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Icon(
                                        imageVector = if (showChartTypeSelector) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showChartTypeSelector,
                                    onDismissRequest = { showChartTypeSelector = false },
                                ) {
                                    ChartType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = when (type) {
                                                        ChartType.LINE -> "Line Chart"
                                                        ChartType.BAR -> "Bar Chart"
                                                        ChartType.HEATMAP -> "Heatmap"
                                                    },
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = when (type) {
                                                        ChartType.LINE -> Icons.AutoMirrored.Filled.ShowChart
                                                        ChartType.BAR -> Icons.Default.BarChart
                                                        ChartType.HEATMAP -> Icons.Default.GridView
                                                    },
                                                    contentDescription = null,
                                                )
                                            },
                                            trailingIcon = if (chartType == type) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                viewModel.setChartType(type)
                                                showChartTypeSelector = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    )

                    Crossfade(
                        targetState = chartType,
                        label = "chart_transition"
                    ) { type ->
                        when (type) {
                            ChartType.LINE -> BalanceChart(
                                primaryCurrency = selectedCurrency,
                                balanceHistory = uiState.spendingTrend,
                                height = 180,
                                smooth = false
                            )
                            ChartType.BAR -> SpendingBarChart(
                                primaryCurrency = selectedCurrency,
                                data = uiState.spendingTrend,
                                height = 180
                            )
                            ChartType.HEATMAP -> SpendingHeatmap(
                                data = uiState.spendingTrend
                            )
                        }
                    }
                }
            }
        }

        if (uiState.categoryBreakdown.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = "Spending by category",
                        action = {
                            TextButton(
                                onClick = {
                                    val categories = uiState.categoryBreakdown
                                        .map { it.name }
                                        .joinToString("|")
                                    drillDownMultiCategory(categories)
                                },
                            ) {
                                Text(text = "View all", style = MaterialTheme.typography.labelMedium)
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                )
                            }
                        },
                    )
                    AnalyticsCategoryCard(
                        categories = uiState.categoryBreakdown,
                        currency = selectedCurrency,
                        onCategoryClick = { category -> drillDownToTransactions(category = category.name) },
                    )
                }
            }
        }

        if (uiState.topMerchants.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = "Top merchants",
                        action = {
                            TextButton(
                                onClick = { drillDownToTransactions() },
                                contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 0.dp),
                            ) {
                                Text(text = "View all", style = MaterialTheme.typography.labelMedium)
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                )
                            }
                        },
                    )
                    AnalyticsReferenceMerchantCard(
                        merchants = uiState.topMerchants,
                        currency = selectedCurrency,
                        onMerchantClick = { merchant -> drillDownToTransactions(merchant = merchant.name) },
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
private fun AnalyticsCategoryCard(
    categories: List<CategoryData>,
    currency: String,
    onCategoryClick: (CategoryData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(categories) { categories.sortedByDescending { it.amount } }
    val visible = if (expanded) sorted else sorted.take(5)
    val hiddenCount = (sorted.size - visible.size).coerceAtLeast(0)
    val maxAmount = sorted.maxOfOrNull { it.amount } ?: BigDecimal.ZERO

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            visible.forEachIndexed { index, category ->
                val info = CategoryMapping.categories[category.name] ?: CategoryMapping.categories["Others"]!!
                val fraction = if (maxAmount > BigDecimal.ZERO)
                    (category.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
                else 0f

                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(durationMillis = 600, delayMillis = index * 60),
                    label = "categoryBarFraction_$index",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(category) }
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Name row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: dot + name · txn count (single line)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(info.color),
                            )
                            Text(
                                text = category.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "· ${category.transactionCount} txn${if (category.transactionCount != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Right: % (colored) + amount
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${category.percentage.toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = info.color,
                            )
                            Text(
                                text = CurrencyFormatter.formatCurrency(category.amount, currency),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Animated progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(info.color),
                        )
                    }
                }

                if (index != visible.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                    )
                }
            }

            if (hiddenCount > 0 || expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "Show less" else "$hiddenCount more categories",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsTopMerchantsCard(
    merchants: List<MerchantData>,
    currency: String,
    onMerchantClick: (MerchantData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(merchants) { merchants.sortedByDescending { it.amount } }
    val visible = if (expanded) sorted else sorted.take(3)
    val hiddenCount = (sorted.size - visible.size).coerceAtLeast(0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            visible.forEachIndexed { index, merchant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMerchantClick(merchant) }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    BrandIcon(
                        merchantName = merchant.name,
                        size = 38.dp,
                        showBackground = true,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = merchant.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${merchant.transactionCount} txn${if (merchant.transactionCount != 1) "s" else ""}" +
                                if (merchant.isSubscription) " · Subscription" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(merchant.amount, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (index != visible.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                }
            }

            if (hiddenCount > 0 || expanded) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = if (expanded) "Show less" else "$hiddenCount more merchants",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small),
                    )
                }
            }
        }
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

