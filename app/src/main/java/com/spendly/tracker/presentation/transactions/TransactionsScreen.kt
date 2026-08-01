package com.spendly.tracker.presentation.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.spendly.tracker.data.database.entity.AccountBalanceEntity
import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.presentation.common.TimePeriod
import com.spendly.tracker.presentation.common.TransactionTypeFilter
import com.spendly.tracker.presentation.common.defaultTimePeriod
import com.spendly.tracker.data.database.entity.ProfileEntity
import com.spendly.tracker.ui.components.profileIcon
import com.spendly.tracker.ui.components.*
import com.spendly.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    initialCategory: String? = null,
    initialMerchant: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null,
    focusSearch: Boolean = false,
    // Parameters for budget navigation (explicit date range)
    initialStartDateEpochDay: Long? = null,
    initialEndDateEpochDay: Long? = null,
    initialCategories: String? = null,  // Comma-separated category names
    initialTransactionType: String? = null,
    initialPaymentMode: String? = null,
    // Epoch days for a CUSTOM period from analytics (separate from budget date range)
    initialPeriodStartEpoch: Long? = null,
    initialPeriodEndEpoch: Long? = null,
    initialBankName: String? = null,
    initialAccountLast4: String? = null,
    viewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onAddTransactionClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val useFinancialMonth by viewModel.useFinancialMonth.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val categoriesFilter by viewModel.categoriesFilter.collectAsState()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val categoriesMap by viewModel.categories.collectAsState()
    val filteredTotals by viewModel.filteredTotals.collectAsState()
    val currencyGroupedTotals by viewModel.currencyGroupedTotals.collectAsState()
    val availableCurrencies by viewModel.availableCurrencies.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val smsScanMonths by viewModel.smsScanMonths.collectAsState()
    val showSmsDataLimitBanner by viewModel.showSmsDataLimitBanner.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsState()
    val convertedAmounts by viewModel.convertedAmounts.collectAsState()
    val categoryDisplayAmounts by viewModel.categoryDisplayAmounts.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profileAccountKeys by viewModel.profileAccountKeys.collectAsState()
    val selectedAccountKey by viewModel.selectedAccountKey.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    val availableAccountKeys by viewModel.availableAccountKeys.collectAsState()
    val includeExcluded by viewModel.includeExcluded.collectAsState()
    val pendingSelfTransferCount by viewModel.pendingSelfTransferCount.collectAsState()
    val selfTransferReview by viewModel.selfTransferReview.collectAsState()

    val filterVisualizationData by viewModel.filterVisualizationData.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) } // Menu doesn't need saving
    var showVisualization by rememberSaveable { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Focus management for search field
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val defaultPeriod = defaultTimePeriod(useFinancialMonth)

    // Active filter count for all filters (period, type, account, category, include-excluded).
    val activeFilterCount = listOf(
        selectedPeriod != defaultPeriod,
        transactionTypeFilter != TransactionTypeFilter.ALL,
        selectedAccountKey != null,
        categoryFilter != null,
        !categoriesFilter.isNullOrEmpty(),
        includeExcluded
    ).count { it }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // Cache expensive operations
    val timePeriods = remember(useFinancialMonth) {
        listOf(
            TimePeriod.THIS_MONTH,
            TimePeriod.LAST_MONTH,
            TimePeriod.CURRENT_FY,
            TimePeriod.ALL,
            TimePeriod.CUSTOM
        )
    }
    val customRangeLabel = remember(customDateRange) {
        DateRangeUtils.formatDateRange(customDateRange)
    }

    val hasExplicitNavRange = initialPeriodStartEpoch != null && initialPeriodEndEpoch != null

    // Apply initial filters only when navigation does not carry an explicit date range
    LaunchedEffect(Unit) {
        if (!hasExplicitNavRange) {
            viewModel.applyInitialFilters(
                initialCategory,
                initialMerchant,
                initialPeriod,
                initialCurrency
            )
        }
    }

    // Track if we've already processed these specific nav params
    var processedNavParams by rememberSaveable { mutableStateOf(false) }

    // Apply navigation filters only ONCE when actually navigating (not when returning from detail)
    LaunchedEffect(
        initialCategory,
        initialMerchant,
        initialPeriod,
        initialCurrency,
        initialTransactionType,
        initialPaymentMode,
        initialPeriodStartEpoch,
        initialPeriodEndEpoch,
        initialBankName,
        initialAccountLast4,
    ) {
        val hasNavParams = initialCategory != null ||
            initialMerchant != null ||
            initialPeriod != null ||
            initialCurrency != null ||
            initialTransactionType != null ||
            initialPaymentMode != null ||
            initialBankName != null ||
            initialAccountLast4 != null ||
            hasExplicitNavRange
        if (!processedNavParams && hasNavParams) {
            viewModel.applyNavigationFilters(
                initialCategory,
                initialMerchant,
                initialPeriod,
                initialCurrency,
                initialTransactionType,
                initialPeriodStartEpoch,
                initialPeriodEndEpoch,
                initialPaymentMode,
                initialBankName,
                initialAccountLast4,
            )
            processedNavParams = true
        }
    }

    // Apply budget filters when navigating from budget screen
    LaunchedEffect(initialStartDateEpochDay, initialEndDateEpochDay, initialCategories, initialTransactionType, initialPeriodStartEpoch, initialPeriodEndEpoch) {
        if (initialStartDateEpochDay != null && initialEndDateEpochDay != null) {
            viewModel.applyBudgetFilters(
                startDateEpochDay = initialStartDateEpochDay,
                endDateEpochDay = initialEndDateEpochDay,
                currency = initialCurrency,
                categories = initialCategories,
                transactionType = initialTransactionType
            )
        } else if (initialCategories != null) {
            // Multi-category navigation from analytics, with optional CUSTOM period epoch days
            viewModel.applyMultiCategoryFilter(initialCategories, initialPeriod, initialPeriodStartEpoch, initialPeriodEndEpoch)
        }
    }
    
    // Handle delete undo snackbar
    LaunchedEffect(deletedTransaction) {
        deletedTransaction?.let { transaction ->
            // Clear the state immediately to prevent re-triggering
            viewModel.clearDeletedTransaction()
            
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Transaction deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Pass the transaction directly since state is already cleared
                    viewModel.undoDeleteTransaction(transaction)
                }
            }
        }
    }
    
    // Focus search field if requested
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Transactions",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Export FAB (only show if transactions exist)
                if (uiState.transactions.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = { showExportDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export to CSV",
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    }
                }
                
                // Add Transaction FAB (consistent with Home screen)
                SmallFloatingActionButton(
                    onClick = onAddTransactionClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction"
                    )
                }
            }
        }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
        // Search Bar - full width
        TransactionSearchBar(
            query = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            categoryFilter = categoryFilter,
            focusRequester = searchFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Dimensions.Padding.content)
        )

        // Filter + Sort row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val filtersSelected = activeFilterCount > 0
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = filtersSelected,
                onClick = { showFilterSheet = true },
                label = {
                    Text(
                        text = if (filtersSelected) "Filters · $activeFilterCount" else "Filters",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderWidth = 0.dp,
                    selected = filtersSelected,
                    enabled = true,
                ),
            )

            val sortSelected = sortOption != SortOption.DATE_NEWEST
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = sortSelected,
                    onClick = { showSortMenu = true },
                    label = {
                        Text(
                            text = sortOption.label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderWidth = 0.dp,
                        selected = sortSelected,
                        enabled = true,
                    ),
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = sortOption == option,
                                        onClick = null,
                                        modifier = Modifier.size(Dimensions.Icon.medium)
                                    )
                                    Text(option.label)
                                }
                            },
                            onClick = {
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Chart toggle button — only show when there is visualization data.
            if (filterVisualizationData != null) {
                IconButton(
                    onClick = { showVisualization = !showVisualization },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = if (showVisualization) "Hide chart" else "Show chart",
                        tint = if (showVisualization)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                }
            }
        }

        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = filterSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                TransactionsFilterSheetContent(
                    activeFilterCount = activeFilterCount,
                    selectedPeriod = selectedPeriod,
                    timePeriods = timePeriods,
                    customDateRange = customDateRange,
                    customRangeLabel = customRangeLabel,
                    transactionTypeFilter = transactionTypeFilter,
                    selectedAccountKey = selectedAccountKey,
                    availableAccounts = availableAccounts,
                    availableAccountKeys = availableAccountKeys,
                    categoryFilter = categoryFilter,
                    categoriesFilter = categoriesFilter,
                    availableCategories = availableCategories,
                    includeExcluded = includeExcluded,
                    onResetAll = { viewModel.resetFilters() },
                    onPeriodSelected = { period ->
                        if (period == TimePeriod.CUSTOM) {
                            showFilterSheet = false
                            showDateRangePicker = true
                        } else {
                            viewModel.selectPeriod(period)
                        }
                    },
                    onTransactionTypeSelected = viewModel::setTransactionTypeFilter,
                    onClearAccount = viewModel::clearSelectedAccount,
                    onAccountSelected = viewModel::setSelectedAccount,
                    onClearCategories = {
                        viewModel.clearCategoryFilter()
                        viewModel.clearCategoriesFilter()
                    },
                    onCategorySelected = viewModel::setCategoryFilter,
                    onIncludeExcludedToggle = viewModel::setIncludeExcluded,
                    onDismiss = { showFilterSheet = false }
                )
            }
        }

        // Pending self-transfer review chip
        if (pendingSelfTransferCount > 0) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
            ) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.startSelfTransferReview() },
                        label = {
                            Text(
                                if (pendingSelfTransferCount == 1) "Review 1 transfer"
                                else "Review $pendingSelfTransferCount transfers"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }
        }

        // Data scope info banner
        if (showSmsDataLimitBanner) {
            SpendlyCardV2(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Showing last $smsScanMonths months of SMS data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Adjust in Settings to scan more history",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(
                        onClick = onNavigateToSettings,
                        contentPadding = PaddingValues(horizontal = Spacing.sm)
                    ) {
                        Text("Settings", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Totals Card - Only show when there are transactions (hide 0/0/0 state)
        if (uiState.transactions.isNotEmpty() || uiState.isLoading) {
            TransactionTotalsCard(
                income = filteredTotals.income,
                expenses = filteredTotals.expenses,
                netBalance = filteredTotals.netBalance,
                currency = selectedCurrency,
                transactionCount = filteredTotals.transactionCount,
                availableCurrencies = availableCurrencies,
                onCurrencySelected = { viewModel.selectCurrency(it) },
                isUnifiedMode = isUnifiedMode,
                isLoading = uiState.isLoading,
                modifier = Modifier
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(top = Spacing.sm)
            )
        }

        // Filter Visualization Panel - shown when user taps the chart icon
        AnimatedVisibility(
            visible = showVisualization && filterVisualizationData != null && !uiState.isLoading,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            filterVisualizationData?.let { vizData ->
                FilterVisualizationPanel(
                    data = vizData,
                    modifier = Modifier
                        .padding(horizontal = Dimensions.Padding.content)
                        .padding(top = Spacing.sm),
                    onCategoryClick = { categoryName ->
                        viewModel.setCategoryFilter(categoryName)
                        // Scroll to top so the filtered list is immediately visible
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                )
            }
        }

        // Transaction List
        when {
            uiState.isLoading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Spacing.md,
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(8) {
                        TransactionItemSkeleton()
                    }
                }
            }
            uiState.transactions.isEmpty() -> {
                EmptyTransactionsState(
                    searchQuery = searchQuery,
                    selectedPeriod = selectedPeriod,
                    onAddClick = onAddTransactionClick
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().overScrollVertical(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Spacing.md,
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    flingBehavior = rememberOverscrollFlingBehavior { listState }
                ) {
                    val isDateSort = sortOption == SortOption.DATE_NEWEST || sortOption == SortOption.DATE_OLDEST
                    if (isDateSort) {
                        // Iterate through date groups in order
                        listOf(
                            DateGroup.TODAY,
                            DateGroup.YESTERDAY,
                            DateGroup.THIS_WEEK,
                            DateGroup.EARLIER
                        ).forEach { dateGroup ->
                            uiState.groupedTransactions[dateGroup]?.let { transactions ->
                                // Date group header
                                item {
                                    SectionHeaderV2(
                                        title = dateGroup.label,
                                        modifier = Modifier.padding(vertical = Spacing.sm)
                                    )
                                }

                                // Transactions in this group
                                items(
                                    items = transactions,
                                    key = { it.id }
                                ) { transaction ->
                                    com.spendly.tracker.ui.components.cards.TransactionItem(
                                        transaction = transaction,
                                        showDate = dateGroup == DateGroup.EARLIER,
                                        convertedAmount = convertedAmounts[transaction.id],
                                        displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                        categoryDisplayAmount = categoryDisplayAmounts[transaction.id],
                                        profileAccountKeys = profileAccountKeys,
                                        categoryForIconFallback = transaction.category,
                                        categoryIconKey = categoriesMap[transaction.category]?.icon,
                                        onClick = { onTransactionClick(transaction.id) },
                                        onExcludeToggle = { viewModel.toggleExcludedFromTracking(transaction) },
                                        onDelete = { viewModel.deleteTransaction(transaction) }
                                    )
                                    if (transaction != transactions.last()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = Spacing.md),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Flat list without date headers for non-date sorts
                        items(
                            items = uiState.transactions,
                            key = { it.id }
                        ) { transaction ->
                            com.spendly.tracker.ui.components.cards.TransactionItem(
                                transaction = transaction,
                                showDate = true,
                                convertedAmount = convertedAmounts[transaction.id],
                                displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                categoryDisplayAmount = categoryDisplayAmounts[transaction.id],
                                profileAccountKeys = profileAccountKeys,
                                categoryForIconFallback = transaction.category,
                                categoryIconKey = categoriesMap[transaction.category]?.icon,
                                onClick = { onTransactionClick(transaction.id) },
                                onExcludeToggle = { viewModel.toggleExcludedFromTracking(transaction) },
                                onDelete = { viewModel.deleteTransaction(transaction) }
                            )
                            if (transaction != uiState.transactions.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        ExportTransactionsDialog(
            transactions = uiState.transactions,
            onDismiss = { showExportDialog = false }
        )
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

    selfTransferReview?.let { reviewState ->
        SelfTransferReviewSheet(
            state = reviewState,
            onConfirm = { viewModel.confirmSelfTransfer() },
            onDeny = { viewModel.denySelfTransfer() },
            onDismiss = { viewModel.dismissSelfTransferReview() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionsFilterSheetContent(
    activeFilterCount: Int,
    selectedPeriod: TimePeriod,
    timePeriods: List<TimePeriod>,
    customDateRange: Pair<java.time.LocalDate, java.time.LocalDate>?,
    customRangeLabel: String?,
    transactionTypeFilter: TransactionTypeFilter,
    selectedAccountKey: String?,
    availableAccounts: List<AccountBalanceEntity>,
    availableAccountKeys: Set<String>,
    categoryFilter: String?,
    categoriesFilter: List<String>?,
    availableCategories: List<String>,
    includeExcluded: Boolean,
    onResetAll: () -> Unit,
    onPeriodSelected: (TimePeriod) -> Unit,
    onTransactionTypeSelected: (TransactionTypeFilter) -> Unit,
    onClearAccount: () -> Unit,
    onAccountSelected: (String?) -> Unit,
    onClearCategories: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onIncludeExcludedToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Dimensions.Padding.content)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (activeFilterCount > 0) {
                    Text(
                        text = "$activeFilterCount active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = onResetAll) {
                Text("Reset all")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Box(modifier = Modifier.weight(1f, fill = true)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                FilterSheetSection(title = "Period") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        timePeriods.forEach { period ->
                            val isSelected = if (period == TimePeriod.CUSTOM) {
                                selectedPeriod == period && customDateRange != null
                            } else {
                                selectedPeriod == period
                            }
                            FilterSheetChip(
                                label = when {
                                    period == TimePeriod.CUSTOM && customRangeLabel != null -> customRangeLabel
                                    else -> period.label
                                },
                                selected = isSelected,
                                onClick = { onPeriodSelected(period) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                FilterSheetSection(title = "Type") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        TransactionTypeFilter.values().toList().forEach { typeFilter ->
                            FilterSheetChip(
                                label = typeFilter.label,
                                selected = transactionTypeFilter == typeFilter,
                                onClick = { onTransactionTypeSelected(typeFilter) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                FilterSheetSection(title = "Category") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FilterSheetChip(
                            label = "Any category",
                            selected = categoryFilter == null && categoriesFilter.isNullOrEmpty(),
                            onClick = onClearCategories
                        )
                        availableCategories.forEach { category ->
                            FilterSheetChip(
                                label = category,
                                selected = categoryFilter == category,
                                onClick = { onCategorySelected(category) },
                                leadingIcon = {
                                    CategoryIcon(
                                        category = category,
                                        size = Dimensions.Icon.small
                                    )
                                }
                            )
                        }
                    }
                }

                if (availableAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.md))

                    FilterSheetSection(title = "Accounts") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            FilterSheetChip(
                                label = "All accounts",
                                selected = selectedAccountKey == null,
                                onClick = onClearAccount
                            )
                            availableAccounts.forEach { account ->
                                val accountKey = "${account.bankName}_${account.accountLast4}"
                                val hasActivityInPeriod = accountKey in availableAccountKeys
                                FilterSheetChip(
                                    label = if (account.isCreditCard) {
                                        "${account.bankName} ••${account.accountLast4}"
                                    } else {
                                        "${account.bankName} ••${account.accountLast4}"
                                    },
                                    selected = selectedAccountKey == accountKey,
                                    enabled = hasActivityInPeriod,
                                    onClick = { onAccountSelected(accountKey) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (account.isCreditCard) {
                                                Icons.Default.CreditCard
                                            } else {
                                                Icons.Default.AccountBalance
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                FilterSheetSection(title = "Other") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FilterSheetChip(
                            label = "Include excluded",
                            selected = includeExcluded,
                            onClick = { onIncludeExcludedToggle(!includeExcluded) },
                            leadingIcon = if (includeExcluded) {
                                {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            } else null,
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Apply filters")
        }

        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
private fun FilterSheetSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        content()
    }
}

@Composable
private fun FilterSheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (enabled) selectedContainerColor else MaterialTheme.colorScheme.surfaceVariant,
            selectedLabelColor = if (enabled) selectedLabelColor else MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f)
            },
            labelColor = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            },
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderWidth = 0.dp,
            selected = selected,
            enabled = enabled,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryFilter: String? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { 
            Text(
                text = if (categoryFilter != null) "Search in $categoryFilter…"
                else "Search merchant, description, tags…",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.then(
            focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
        )
    )
}


@Composable
private fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod,
    onAddClick: () -> Unit = {}
) {
    val headline = when {
        searchQuery.isNotEmpty() -> "No results for \"$searchQuery\""
        selectedPeriod != TimePeriod.ALL -> "Nothing for ${selectedPeriod.label.lowercase()}"
        else -> "No transactions yet"
    }
    val description = when {
        searchQuery.isNotEmpty() -> "Try a different search term or clear your filters"
        selectedPeriod != TimePeriod.ALL -> "Try selecting a different time period"
        else -> "Add your first transaction manually, or scan SMS from the home screen"
    }
    val actionLabel = if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) "Add Transaction" else null
    val onAction = if (actionLabel != null) onAddClick else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        SpendlyEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            headline = headline,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}
