package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.defaultTimePeriod
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.components.profileIcon
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.CollapsibleFilterRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.DateRangeUtils
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
    val includeExcluded by viewModel.includeExcluded.collectAsState()
    val pendingSelfTransferCount by viewModel.pendingSelfTransferCount.collectAsState()
    val selfTransferReview by viewModel.selfTransferReview.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    // Use rememberSaveable to preserve UI state across navigation
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) } // Menu doesn't need saving
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    
    // Focus management for search field
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    
    // Active filter count for the "More Filters" row (profile, category, include-excluded).
    // Period, type, search, and currency are outside this section and are not counted here.
    val activeFilterCount = listOf(
        selectedProfileId != null,
        categoryFilter != null,
        !categoriesFilter.isNullOrEmpty(),
        includeExcluded
    ).count { it }

    LaunchedEffect(categoryFilter, categoriesFilter) {
        if (categoryFilter != null || !categoriesFilter.isNullOrEmpty()) {
            showAdvancedFilters = true
        }
    }

    val defaultPeriod = defaultTimePeriod(useFinancialMonth)

    // Check if any filter is active (for showing "Clear all" button)
    val hasAnyActiveFilter = searchQuery.isNotEmpty() ||
        selectedPeriod != defaultPeriod ||
        categoryFilter != null ||
        categoriesFilter != null ||
        transactionTypeFilter != TransactionTypeFilter.ALL ||
        selectedProfileId != null ||
        selectedCurrency != null ||
        customDateRange != null ||
        includeExcluded

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // Cache expensive operations — include CALENDAR_MONTH only when pay-month mode is enabled
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
        // Search Bar + Sort Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TransactionSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                categoryFilter = categoryFilter,
                focusRequester = searchFocusRequester,
                modifier = Modifier.weight(1f)
            )
            
            // Sort button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
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
        }
        
        // Period Filter Chips - Always visible
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Clear all filters chip - only show when any filter is active
            if (hasAnyActiveFilter) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.resetFilters() },
                        label = { Text("Clear all") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            labelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }

            // Period filter chips
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
                                period == TimePeriod.THIS_MONTH && !useFinancialMonth -> "This Month"
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

        // Transaction Type Filter Chips - Always visible second row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(TransactionTypeFilter.values().toList()) { typeFilter ->
                FilterChip(
                    selected = transactionTypeFilter == typeFilter,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        viewModel.setTransactionTypeFilter(typeFilter)
                    },
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
                                Icons.Default.CompareArrows,
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
            PennyWiseCardV2(
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
        
        // Collapsible Advanced Filters
        CollapsibleFilterRow(
            isExpanded = showAdvancedFilters,
            activeFilterCount = activeFilterCount,
            onToggle = { showAdvancedFilters = !showAdvancedFilters },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
            // Profile Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // "All" chip
                item {
                    FilterChip(
                        selected = selectedProfileId == null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.setSelectedProfile(null)
                        },
                        label = { Text("All") },
                        leadingIcon = if (selectedProfileId == null) {
                            {
                                Icon(
                                    Icons.Outlined.AccountBalance,
                                    contentDescription = null,
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
                items(profiles) { profile ->
                    FilterChip(
                        selected = selectedProfileId == profile.id,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.setSelectedProfile(profile.id)
                        },
                        label = { Text(profile.name) },
                        leadingIcon = if (selectedProfileId == profile.id) {
                            {
                                Icon(
                                    profileIcon(profile),
                                    contentDescription = null,
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

            if (availableCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        FilterChip(
                            selected = categoryFilter == null && categoriesFilter.isNullOrEmpty(),
                            onClick = {
                                viewModel.clearCategoryFilter()
                                viewModel.clearCategoriesFilter()
                            },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }

                    items(availableCategories) { category ->
                        FilterChip(
                            selected = categoryFilter == category,
                            onClick = {
                                if (categoryFilter == category) {
                                    viewModel.clearCategoryFilter()
                                } else {
                                    viewModel.setCategoryFilter(category)
                                }
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

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Include excluded transactions toggle
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    FilterChip(
                        selected = includeExcluded,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.setIncludeExcluded(!includeExcluded)
                        },
                        label = { Text("Include excluded") },
                        leadingIcon = if (includeExcluded) {
                            {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
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
                                com.pennywiseai.tracker.ui.components.cards.TransactionItem(
                                    transaction = transaction,
                                    showDate = dateGroup == DateGroup.EARLIER,
                                    convertedAmount = convertedAmounts[transaction.id],
                                    displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                    categoryDisplayAmount = categoryDisplayAmounts[transaction.id],
                                    profileAccountKeys = profileAccountKeys,
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
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            headline = headline,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}
