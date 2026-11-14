package com.spendly.tracker.ui.screens.analytics

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
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.effects.rememberReducedMotion
import com.spendly.tracker.ui.effects.staggeredIntro
import com.spendly.tracker.ui.sharedElementIfAvailable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import com.spendly.tracker.domain.model.InsightType
import com.spendly.tracker.domain.model.SmartInsight
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
import com.spendly.tracker.presentation.common.PaymentMode
import com.spendly.tracker.presentation.common.PaymentModeGroup
import com.spendly.tracker.presentation.common.TimePeriod
import com.spendly.tracker.presentation.common.TransactionTypeFilter
import com.spendly.tracker.ui.components.PeriodRangeNavigator
import com.spendly.tracker.ui.components.*
import com.spendly.tracker.ui.components.charts.CategoryBreakdown
import com.spendly.tracker.ui.components.charts.CategorySlice
import com.spendly.tracker.ui.components.charts.CategoryView
import com.spendly.tracker.ui.components.charts.ChartCard
import com.spendly.tracker.ui.components.charts.ChartSegmentedControl
import com.spendly.tracker.ui.components.charts.ChartTone
import com.spendly.tracker.ui.components.charts.ComparisonMetric
import com.spendly.tracker.ui.components.charts.DistributionBucket
import com.spendly.tracker.ui.components.charts.FinancialComparison
import com.spendly.tracker.ui.components.charts.MetricCard
import com.spendly.tracker.ui.components.charts.MetricDelta
import com.spendly.tracker.ui.components.charts.MetricEmphasis
import com.spendly.tracker.ui.components.charts.MetricStrip
import com.spendly.tracker.ui.components.charts.RankedBarChart
import com.spendly.tracker.ui.components.charts.RankedItem
import com.spendly.tracker.ui.components.charts.TimePoint
import com.spendly.tracker.ui.components.charts.TimeSeriesChart
import com.spendly.tracker.ui.components.charts.TimeSeriesSeries
import com.spendly.tracker.ui.components.charts.TimeSeriesStyle
import com.spendly.tracker.ui.components.charts.TransactionDistribution
import com.spendly.tracker.ui.components.charts.chartPalette
import com.spendly.tracker.ui.components.cards.ListItemCardV2
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.cards.SectionLinkAction
import com.spendly.tracker.ui.components.*
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.screens.savings.SavingsRatePreviewCard
import com.spendly.tracker.ui.screens.savings.SavingsViewModel
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.ui.utils.LocalWindowSizeInfo
import com.spendly.tracker.utils.CurrencyFormatter
import com.spendly.tracker.utils.DateRangeUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.math.RoundingMode
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
    onNavigateToTransactionsByAmount: (
        minAmount: Long?,
        maxAmount: Long?,
        currency: String?,
        transactionType: String?,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToTransactionsMultiCategory: (categories: String, period: String?, currency: String?, startDateEpochDay: Long?, endDateEpochDay: Long?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToTransaction: (Long) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToBehavioralStats: () -> Unit = {},
    onNavigateToBreakdown: (tileKey: String) -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToCreditCardAnalytics: (startEpoch: Long, endEpoch: Long, currency: String) -> Unit = { _, _, _ -> },
    onNavigateToSavings: () -> Unit = {},
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
    // The full savings experience lives on its own page; this only feeds the compact preview.
    val savingsViewModel: SavingsViewModel = hiltViewModel()
    val savingsRate by savingsViewModel.savingsRate.collectAsStateWithLifecycle()
    val savingsProjection by savingsViewModel.savingsProjection.collectAsStateWithLifecycle()
    // Use rememberSaveable to preserve UI state across navigation
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var showChartTypeSelector by remember { mutableStateOf(false) }
    var categoryView by rememberSaveable { mutableStateOf(CategoryView.BARS) }
    var selectedOverviewTab by rememberSaveable { mutableStateOf(AnalyticsOverviewTab.SPENDING) }

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

    // Length of the period the hero card covers — the denominator for the per-day figure.
    val periodDays = remember(uiState.periodStart, uiState.periodEnd) {
        val start = uiState.periodStart
        val end = uiState.periodEnd
        if (start != null && end != null) {
            (java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
        } else {
            0
        }
    }

    // ── Dashboard figures ───────────────────────────────────────────────────
    // Every visual below reads the same UiState values, so the charts, the metrics and the
    // drill-downs can never disagree with one another.
    val palette = chartPalette()
    val spendingTotal = uiState.periodOutflow?.spending ?: uiState.totalExpense
    val spendingCount = uiState.periodOutflow?.spendingTransactionCount ?: 0
    val incomeTotal = uiState.incomeSummary?.total ?: uiState.totalIncome
    val incomeCount = uiState.incomeSummary?.transactionCount ?: 0
    val netCashFlow = uiState.incomeSummary?.net
        ?: incomeTotal.subtract(uiState.periodOutflow?.total ?: BigDecimal.ZERO)
    val averageTransaction = if (spendingCount > 0) {
        spendingTotal.divide(BigDecimal(spendingCount), 2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    val previousSpending = spendingTotal.previousFrom(uiState.periodOutflow?.spendingDeltaPercent)
    val previousIncome = incomeTotal.previousFrom(uiState.incomeSummary?.deltaPercent)

    val categorySlices = remember(uiState.categoryBreakdown, palette) {
        uiState.categoryBreakdown.mapIndexed { index, category ->
            CategorySlice(
                name = category.name,
                amount = category.amount,
                count = category.transactionCount,
                share = (category.percentage / 100f).coerceIn(0f, 1f),
                color = palette.seriesColor(index),
                icon = CategoryMapping.categories[category.name]?.icon,
            )
        }
    }
    val merchantRows = remember(uiState.topMerchants, palette) {
        uiState.topMerchants.mapIndexed { index, merchant ->
            RankedItem(
                name = merchant.name,
                value = merchant.amount,
                count = merchant.transactionCount,
                caption = if (merchant.isSubscription) "Recurring subscription" else null,
                color = palette.seriesColor(index),
            )
        }
    }
    val distributionBuckets = remember(uiState.amountBuckets) {
        uiState.amountBuckets.map { bucket ->
            DistributionBucket(
                label = bucket.rangeLabel.ifBlank { bucket.label },
                amount = bucket.amount,
                count = bucket.transactionCount,
            )
        }
    }
    val trendPoints = remember(uiState.spendingTrend) { uiState.spendingTrend.toTimePoints() }

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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AnalyticsReferencePeriodChipRow(
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { viewModel.selectPeriod(it) },
                    onCustomSelected = { showDateRangePicker = true },
                )

                periodAnchorMonth?.let { anchorMonth ->
                    if (showPeriodNavigator && periodRangeLabel != null) {
                        AnalyticsReferenceDateNavigator(
                            label = periodRangeLabel,
                            onPrevious = { viewModel.navigateToMonth(anchorMonth.minusMonths(1)) },
                            onNext = { viewModel.navigateToMonth(anchorMonth.plusMonths(1)) },
                            canGoNext = canNavigateToNextPeriod,
                        )
                    }
                }

                AnalyticsReferenceOverviewTabs(
                    selectedTab = selectedOverviewTab,
                    onTabSelected = { selectedOverviewTab = it },
                )

                if (uiState.periodOutflow != null || uiState.incomeSummary != null || uiState.investmentInsights != null || uiState.paymentModeBreakdown != null) {
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
                        incomeSummary = uiState.incomeSummary,
                        currency = selectedCurrency,
                        periodDays = periodDays,
                        onTotalClick = {
                            when (tab) {
                                AnalyticsOverviewTab.OUTFLOW -> drillDownToTransactions(transactionType = null)
                                AnalyticsOverviewTab.SPENDING -> drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name)
                                AnalyticsOverviewTab.INVESTED -> drillDownToTransactions(transactionType = TransactionTypeFilter.INVESTMENT.name)
                                AnalyticsOverviewTab.INCOME -> drillDownToTransactions(transactionType = TransactionTypeFilter.INCOME.name)
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
                                AnalyticsOverviewTab.INCOME -> drillDownToTransactions(transactionType = TransactionTypeFilter.INCOME.name)
                            }
                        },
                    )
                }
                }
            }
        }

        item {
            MetricStrip {
                MetricCard(
                    label = "Income",
                    value = CurrencyFormatter.formatCurrencyWhole(incomeTotal, selectedCurrency),
                    modifier = Modifier.weight(1f),
                    emphasis = MetricEmphasis.COMPACT,
                    fillHeight = true,
                    delta = uiState.incomeSummary?.deltaPercent?.let { delta ->
                        MetricDelta(
                            text = signedPercent(delta),
                            tone = if (delta >= 0f) ChartTone.POSITIVE else ChartTone.NEGATIVE,
                            increasing = delta >= 0f,
                        )
                    },
                    caption = "$incomeCount ${if (incomeCount == 1) "credit" else "credits"}",
                )
                MetricCard(
                    label = "Net flow",
                    value = CurrencyFormatter.formatCurrencyWhole(netCashFlow, selectedCurrency),
                    modifier = Modifier.weight(1f),
                    emphasis = MetricEmphasis.COMPACT,
                    fillHeight = true,
                    caption = "income − outflow",
                )
                MetricCard(
                    label = "Average",
                    value = CurrencyFormatter.formatCurrencyWhole(averageTransaction, selectedCurrency),
                    modifier = Modifier.weight(1f),
                    emphasis = MetricEmphasis.COMPACT,
                    fillHeight = true,
                    caption = "per transaction",
                )
            }
        }

        if (previousSpending != null || previousIncome != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(title = "How this period compares")
                    ChartCard {
                        FinancialComparison(
                            metrics = listOf(
                                ComparisonMetric(
                                    label = "Spending",
                                    current = spendingTotal,
                                    previous = previousSpending,
                                    currency = selectedCurrency,
                                    positiveIsGood = false,
                                    caption = "$spendingCount txns",
                                ),
                                ComparisonMetric(
                                    label = "Income",
                                    current = incomeTotal,
                                    previous = previousIncome,
                                    currency = selectedCurrency,
                                    positiveIsGood = true,
                                    caption = "$incomeCount credits",
                                ),
                            ),
                        )
                    }
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

                    @Composable
                    fun LineOrBarChart(type: ChartType) {
                        var trendSeries by rememberSaveable { mutableStateOf(0) } // 0 = Spending, 1 = Income, 2 = Outflow

                        val palette = chartPalette()
                        // Build trend series from ViewModel state
                        val trendSeriesList = remember(uiState.spendingTrend, uiState.incomeTrend, uiState.outflowTrend, selectedCurrency) {
                            listOf(
                                TimeSeriesSeries(
                                    name = "Spending",
                                    points = uiState.spendingTrend.toTimePoints(),
                                    color = palette.accent,
                                    style = TimeSeriesStyle.LINE,
                                    filled = true,
                                ),
                                TimeSeriesSeries(
                                    name = "Income",
                                    points = uiState.incomeTrend.toTimePoints(),
                                    color = palette.positive,
                                    style = TimeSeriesStyle.LINE,
                                    filled = false,
                                ),
                                TimeSeriesSeries(
                                    name = "Outflow",
                                    points = uiState.outflowTrend.toTimePoints(),
                                    color = palette.negative,
                                    style = TimeSeriesStyle.LINE,
                                    filled = false,
                                ),
                            )
                        }

                        SectionHeaderV2(
                            title = "Spend trend",
                            action = {
                                ChartSegmentedControl(
                                    options = listOf("Spending", "Income", "Outflow"),
                                    selectedIndex = trendSeries,
                                    onSelect = { trendSeries = it },
                                    modifier = Modifier.widthIn(max = 240.dp),
                                )
                            },
                        )

                        ChartCard {
                            TimeSeriesChart(
                                series = trendSeriesList.take(trendSeries + 1).reversed(),
                                currency = selectedCurrency,
                                height = 190.dp,
                            )
                        }
                    }

                    @Composable
                    fun HeatmapChart() {
                        SpendingHeatmap(data = uiState.spendingTrend)
                    }

                    Crossfade(
                        targetState = chartType,
                        label = "chart_transition"
                    ) { type ->
                        if (type == ChartType.HEATMAP) HeatmapChart() else LineOrBarChart(type)
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
                            SectionLinkAction(
                                text = if (categoryView == CategoryView.BARS) "Table" else "Bars",
                                onClick = {
                                    categoryView = if (categoryView == CategoryView.BARS) {
                                        CategoryView.TABLE
                                    } else {
                                        CategoryView.BARS
                                    }
                                },
                            )
                        },
                    )
                    ChartCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
                        CategoryBreakdown(
                            slices = categorySlices,
                            currency = selectedCurrency,
                            view = categoryView,
                            onSelect = { slice -> drillDownToTransactions(category = slice.name) },
                        )
                    }
                }
            }
        }

        if (uiState.topMerchants.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = "Top merchants",
                        action = {
                            SectionLinkAction(text = "View all", onClick = { drillDownToTransactions() })
                        },
                    )
                    ChartCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        RankedBarChart(
                            items = merchantRows,
                            currency = selectedCurrency,
                            onSelect = { merchant ->
                                drillDownToTransactions(merchant = merchant.name)
                            },
                        )
                    }
                }
            }
        }

        if (uiState.amountBuckets.any { it.transactionCount > 0 }) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(title = "Spending by size")
                    ChartCard {
                        TransactionDistribution(
                            buckets = distributionBuckets,
                            currency = selectedCurrency,
                            onSelect = { index ->
                                val bucket = uiState.amountBuckets.getOrNull(index) ?: return@TransactionDistribution
                                onNavigateToTransactionsByAmount(
                                    bucket.minAmount,
                                    bucket.maxAmount,
                                    selectedCurrency,
                                    transactionTypeFilter.name,
                                    drillDownPeriodEpochs.first,
                                    drillDownPeriodEpochs.second,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeaderV2(
                    title = "Savings",
                    action = {
                        SectionLinkAction(text = "Open", onClick = onNavigateToSavings)
                    },
                )
                SavingsRatePreviewCard(
                    summary = savingsRate,
                    projection = savingsProjection,
                    onClick = onNavigateToSavings,
                )
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
                // Near-black category colors vanish on dark surfaces; fall back to a visible neutral.
                val accent = if (info.color.luminance() < 0.08f) MaterialTheme.colorScheme.onSurfaceVariant else info.color
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
                                    .background(accent),
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Right: % (neutral) + amount
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${category.percentage.toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .background(accent),
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
    val reducedMotion = rememberReducedMotion()
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
                        .staggeredIntro(index = index, enabled = !reducedMotion)
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
                        modifier = Modifier.sharedElementIfAvailable(
                            key = "merchant_icon_${merchant.name}"
                        ),
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
        SpendlyEmptyState(
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


/** Two-state list/pie switch for the category section. */
@Composable
private fun CategoryViewToggle(
    showPie: Boolean,
    onShowPieChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(2.dp),
    ) {
        listOf(false to Icons.AutoMirrored.Filled.ViewList, true to Icons.Default.PieChart).forEach { (pie, icon) ->
            val selected = showPie == pie
            IconButton(
                onClick = { onShowPieChange(pie) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (pie) "Show pie chart" else "Show list",
                    modifier = Modifier.size(Dimensions.Icon.small),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "+59.9%" / "−12%" / "0%" with a real minus sign so the direction reads at a glance. */
private fun signedPercent(deltaPercent: Float): String {
    val rounded = kotlin.math.round(deltaPercent * 10f) / 10f
    val prefix = when {
        rounded > 0f -> "+"
        rounded < 0f -> "−"
        else -> ""
    }
    return "$prefix${kotlin.math.abs(rounded)}%"
}

/**
 * Reconstructs the reference-period total from a delta percentage, so the comparison
 * chart can render the previous bar without a second query.
 */
private fun BigDecimal.previousFrom(deltaPercent: Float?): BigDecimal? {
    val delta = deltaPercent ?: return null
    val factor = 1.0 + delta / 100.0
    if (factor <= 0.0) return null
    return divide(BigDecimal.valueOf(factor), 2, RoundingMode.HALF_UP)
}

/** Maps the analytics trend buckets onto the chart's label/value pairs. */
private fun List<BalancePoint>.toTimePoints(): List<TimePoint> {
    if (isEmpty()) return emptyList()
    val sorted = sortedBy { it.timestamp }
    val isYearly = sorted.size > 1 && sorted.all { it.timestamp.dayOfYear == 1 }
    val isMonthly = !isYearly && sorted.all { it.timestamp.dayOfMonth == 1 }
    val spansYears = sorted.first().timestamp.year != sorted.last().timestamp.year
    return sorted.map { point ->
        val label = when {
            isYearly -> point.timestamp.format(DateTimeFormatter.ofPattern("yyyy"))
            isMonthly && spansYears -> point.timestamp.format(DateTimeFormatter.ofPattern("MMM yy"))
            isMonthly -> point.timestamp.format(DateTimeFormatter.ofPattern("MMM"))
            else -> point.timestamp.format(DateTimeFormatter.ofPattern("dd MMM"))
        }
        TimePoint(label = label, value = point.balance.toDouble(), tooltipLabel = label)
    }
}
