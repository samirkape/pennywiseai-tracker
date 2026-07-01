package com.spendly.tracker.presentation.home

import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.work.WorkInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.spendly.tracker.R
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.ui.components.PennyWiseCard
import com.spendly.tracker.ui.components.cards.PennyWiseCardV2
import com.spendly.tracker.ui.components.PennyWiseEmptyState
import com.spendly.tracker.ui.components.PayPeriodSalarySuggestionDialog
import com.spendly.tracker.ui.components.SmsParsingProgressDialog
import com.spendly.tracker.ui.components.cards.GroupCard
import com.spendly.tracker.ui.components.cards.HomeHeroPager
import com.spendly.tracker.ui.screens.payperiod.PayPeriodExplorerContent
import com.spendly.tracker.ui.components.cards.TransactionItem
import com.spendly.tracker.ui.components.cards.formatStatAmount
import com.spendly.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.spendly.tracker.ui.components.spotlightTarget
import com.spendly.tracker.presentation.common.buildProfileAccountKeys
import com.spendly.tracker.presentation.common.defaultTimePeriodNavParam
import com.spendly.tracker.ui.components.ProfileFilterDropdown
import com.spendly.tracker.ui.components.profileFilterIcon
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.ui.utils.LocalWindowSizeInfo
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Primary dashboard: hero balances and charts, date-scoped feed, and recent activity.
 * Layout follows **Pattern A — Hero home** ([docs/scaffold-patterns.md](../../../../../docs/scaffold-patterns.md)):
 * collapsing [CustomTitleTopAppBar], then scrollable content. Secondary actions also live in the quick-actions
 * sheet (menu FAB) to avoid crowding the hero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController,
    blurEffects: Boolean = false,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: (period: String) -> Unit = {},
    onNavigateToInvestmentTransactions: (period: String) -> Unit = {},
    onNavigateToIncomeTransactions: (period: String) -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToTransactionsWithSearch: (period: String) -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onLoanClick: (Long) -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToAddScreen: () -> Unit = {},
    onNavigateToManageAccounts: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
    onTransactionTypeClick: (String?) -> Unit = {},
    onFabPositioned: (Rect) -> Unit = {},
    onNavigateToPayPeriodSettings: () -> Unit = {},
    onNavigateToPayPeriodExplorer: (Long, Long) -> Unit = { _, _ -> },
    onNavigateToThisWeekTransactions: (startEpochDay: Long, endEpochDay: Long) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val transactionsPeriod = defaultTimePeriodNavParam(uiState.useFinancialMonth)
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val smsScanWorkInfo by viewModel.smsScanWorkInfo.collectAsState()
    val activity = LocalActivity.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for full resync confirmation dialog and quick-actions sheet
    var showFullResyncDialog by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showHomeHelpDialog by remember { mutableStateOf(false) }
    var showSpendTimelineSheet by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    // Profile filter dropdown state - kept for potential future use
    val context = LocalContext.current

    // Haptic feedback
    val view = LocalView.current

    // Haptic on successful SMS scan completion
    LaunchedEffect(smsScanWorkInfo?.state) {
        if (smsScanWorkInfo?.state == WorkInfo.State.SUCCEEDED) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    // Enter-always scroll behavior — top bar hides when scrolling down, reappears on scroll up
    val scrollBehaviorSmall = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollBehaviorLarge = scrollBehaviorSmall

    // Haze state for TopAppBar blur
    val hazeState = remember { HazeState() }

    val hazeStateHero = remember { HazeState() }

    // LazyColumn scroll state for overscroll physics
    val lazyListState = rememberLazyListState()

    // Staggered entrance animation state — only animates on first composition
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    // Mark entrance animation as complete after all stagger delays have fired
    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(380)
            hasAnimated = true
        }
    }

    // Check for app updates and reviews when the screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.autoScanIfNeeded()
        // Refresh account balances to ensure proper currency conversion
        viewModel.refreshAccountBalances()

        activity?.let {
            val componentActivity = it as ComponentActivity
            
            // Check for app updates
            viewModel.checkForAppUpdate(
                activity = componentActivity,
                snackbarHostState = snackbarHostState,
                scope = scope
            )
            
            // Check for in-app review eligibility
            viewModel.checkForInAppReview(componentActivity)
        }
    }
    
    // Refresh hidden accounts whenever this screen becomes visible
    // This ensures changes from ManageAccountsScreen are reflected immediately
    DisposableEffect(Unit) {
        viewModel.refreshHiddenAccounts()
        onDispose { }
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
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val spendTimelineSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    uiState.payPeriodSuggestion?.let { suggestion ->
        PayPeriodSalarySuggestionDialog(
            suggestion = suggestion,
            onAccept = { viewModel.acceptPayPeriodSuggestion() },
            onDismiss = { viewModel.dismissPayPeriodSuggestion() },
        )
    }
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.brand_display_name),
                isHomeScreen = true,
                userName = uiState.userName,
                profileImageUri = uiState.profileImageUri,
                profileBackgroundColor = uiState.profileBackgroundColor,
                onAvatarClick = { showAvatarPicker = true },
                hazeState = hazeState,
                blurEffects = blurEffects,
                actionContent = {
                    val containerColor = MaterialTheme.colorScheme.surfaceContainer
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (blurEffects) containerColor.copy(0.5f) else containerColor,
                                shape = CircleShape,
                            )
                            .clickable(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onNavigateToTransactionsWithSearch(transactionsPeriod)
                                },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.home_search),
                            tint = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
    val windowSizeInfo = LocalWindowSizeInfo.current
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState },
            contentPadding = PaddingValues(
                // Must match full top inset: pulling this up clips the in-list brand title under status bar / clip.
                top = paddingValues.calculateTopPadding(),
                bottom = windowSizeInfo.bottomNavBarPadding + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 1. Hero spend (0ms)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (!uiState.isBalanceReady) {
                            com.spendly.tracker.ui.components.skeleton.BalanceCardSkeleton(
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val loan = uiState.loanSummary
                            val loanSubtitle = loan?.let { ls ->
                                when {
                                    ls.totalLentRemaining > java.math.BigDecimal.ZERO &&
                                        ls.totalBorrowedRemaining > java.math.BigDecimal.ZERO ->
                                        "${ls.activeLoans.size} active"
                                    ls.totalLentRemaining > java.math.BigDecimal.ZERO ->
                                        CurrencyFormatter.formatCurrency(ls.totalLentRemaining, uiState.selectedCurrency)
                                    else ->
                                        CurrencyFormatter.formatCurrency(ls.totalBorrowedRemaining, uiState.selectedCurrency)
                                }
                            }
                            HomeHeroPager(
                                modifier = Modifier.fillMaxWidth(),
                            blurEffects = blurEffects,
                            hazeState = hazeStateHero,
                            currency = uiState.selectedCurrency,
                            currentMonthExpenses = uiState.currentMonthExpenses,
                            currentMonthIncome = uiState.currentMonthIncome,
                            currentMonthTotal = uiState.currentMonthTotal,
                            currentMonthInvestment = uiState.currentMonthInvestment,
                            periodDayLabel = uiState.periodDayLabel,
                            availableCurrencies = uiState.availableCurrencies,
                            isUnifiedMode = uiState.isUnifiedMode,
                            spendingPeriodLabel = uiState.spendingPeriodLabel,
                            useFinancialMonth = uiState.useFinancialMonth,
                            onToggleSpendingMode = { viewModel.toggleSpendingMonthMode() },
                            onCurrencySelect = { viewModel.selectCurrency(it) },
                            onNavigateToTransactions = { onNavigateToTransactions(transactionsPeriod) },
                            onNavigateToInvestmentTransactions = {
                                onNavigateToInvestmentTransactions(transactionsPeriod)
                            },
                            onNavigateToBudgets = onNavigateToBudgets,
                            onShowBreakdown = { viewModel.showBreakdownDialog() },
                            onOpenPayPeriodSettings = onNavigateToPayPeriodSettings,
                            onSpendSoFarClick = if (
                                uiState.payPeriodStartEpochDay >= 0L &&
                                uiState.payPeriodEndEpochDay >= 0L
                            ) {
                                { showSpendTimelineSheet = true }
                            } else {
                                null
                            },
                            )
                        }
                    }
                }
            }

            // ── Section 2: This week snapshot ────────────────────────────────
            item {
                HomeThisWeekCard(
                    thisWeekSpend = uiState.thisWeekSpend,
                    lastWeekSpend = uiState.lastWeekSpend,
                    currency = uiState.selectedCurrency,
                    onClick = {
                        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
                        val weekEnd = weekStart.plusDays(6)
                        onNavigateToThisWeekTransactions(weekStart.toEpochDay(), weekEnd.toEpochDay())
                    },
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                )
            }

            // ── Section 3: Last 7 days bar chart ─────────────────────────────
            if (uiState.last7DaysSpend.isNotEmpty()) {
                item {
                    HomeLast7DaysCard(
                        last7DaysSpend = uiState.last7DaysSpend,
                        currency = uiState.selectedCurrency,
                        onClick = onNavigateToAnalytics,
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            }

            // ── Section 5: Smart Insights (after hero/chart, before goals) ──────
            if (uiState.insights.isNotEmpty()) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(10); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        SmartInsightsCard(
                            insights = uiState.insights,
                            onInsightAction = { insight ->
                                when (insight.type) {
                                    InsightType.SUBSCRIPTION_UPCOMING -> onNavigateToSubscriptions()
                                    InsightType.GOAL_MILESTONE -> onNavigateToGoals()
                                    else -> onNavigateToAnalytics()
                                }
                            },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                        )
                    }
                }
            }

            // ── Section 6: Subscriptions + Goal row ──────────────────────────
            item {
                HomeSubscriptionsGoalRow(
                    activeSubscriptionCount = uiState.activeSubscriptionCount,
                    totalSubscriptionAmount = uiState.totalSubscriptionAmount,
                    upcomingSubscriptions = uiState.upcomingSubscriptions,
                    currency = uiState.selectedCurrency,
                    activeGoals = uiState.activeGoals,
                    onNavigateToSubscriptions = onNavigateToSubscriptions,
                    onNavigateToGoals = onNavigateToGoals,
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                )
            }

            // 2. Feed header — day zone (20ms)
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(20); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    HomeFeedDateNavigator(
                        uiState = uiState,
                        transactionsPeriod = transactionsPeriod,
                        onNavigateToTransactions = onNavigateToTransactions,
                        onNavigateDateBy = viewModel::navigateDateBy,
                        onNavigateToDate = viewModel::navigateToDate,
                        getDailyExpensesForMonth = viewModel::getDailyExpensesForMonth,
                        getDailyExpensesBetween = viewModel::getDailyExpensesBetween,
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(100); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            repeat(5) {
                                TransactionItemSkeleton()
                            }
                        }
                    }
                }
            } else if (uiState.recentItems.isEmpty()) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(100); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        PennyWiseEmptyState(
                            icon = Icons.Default.Sync,
                            headline = stringResource(R.string.home_feed_empty_today_headline),
                            description = stringResource(R.string.home_feed_empty_today_description),
                            actionLabel = "Scan Now",
                            onAction = { viewModel.scanSmsMessages() },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            ghostContent = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    repeat(3) {
                                        TransactionItemSkeleton()
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(100); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        val profileAccountKeys = remember(uiState.accountBalances) {
                            buildProfileAccountKeys(uiState.accountBalances)
                        }
                        // Grouped feed card — all transactions in a single card with dividers
                        com.spendly.tracker.ui.components.cards.PennyWiseCardV2(
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            contentPadding = 0.dp
                        ) {
                            uiState.recentItems.forEachIndexed { index, item ->
                                when (item) {
                                    is HomeRecentItem.SingleTransaction -> TransactionItem(
                                        transaction = item.transaction,
                                        convertedAmount = item.convertedAmount,
                                        displayCurrency = if (uiState.isUnifiedMode) {
                                            uiState.selectedCurrency
                                        } else {
                                            null
                                        },
                                        profileAccountKeys = profileAccountKeys,
                                        flat = true,
                                        categoryForIconFallback = item.transaction.category,
                                        categoryIconKey = item.categoryIconKey,
                                        onClick = { onTransactionClick(item.transaction.id) },
                                        onExcludeToggle = {
                                            viewModel.toggleExcludedFromTracking(item.transaction)
                                        },
                                        onDelete = { viewModel.deleteTransaction(item.transaction) },
                                    )
                                    is HomeRecentItem.GroupItem -> GroupCard(
                                        group = item.group,
                                        transactions = item.transactions,
                                        convertedAmounts = item.convertedAmounts,
                                        displayCurrency = if (uiState.isUnifiedMode) {
                                            uiState.selectedCurrency
                                        } else {
                                            null
                                        },
                                        flat = true,
                                        useCategoryIconFallback = true,
                                        onClick = { onGroupClick(item.group.id) },
                                    )
                                }
                                if (index < uiState.recentItems.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }


        }
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = Dimensions.Padding.content,
                    bottom = if (windowSizeInfo.useNavigationRail) Dimensions.Padding.content else 96.dp,
                )
                .combinedClickable(
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onNavigateToAddScreen()
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showActionsSheet = true
                    },
                )
                .spotlightTarget(onFabPositioned),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_action_add),
                )
            }
        }

        AvatarPickerSheet(
            visible = showAvatarPicker,
            currentUri = uiState.profileImageUri,
            userName = uiState.userName,
            onDismiss = { showAvatarPicker = false },
            onSelect = { uri ->
                viewModel.updateProfileImage(uri)
                showAvatarPicker = false
            },
        )

        HomeActionsSheet(
            visible = showActionsSheet,
            isScanning = uiState.isScanning,
            onDismiss = { showActionsSheet = false },
            onRefresh = { viewModel.scanSmsMessages() },
            onAdd = onNavigateToAddScreen,
            onSearch = { onNavigateToTransactionsWithSearch(transactionsPeriod) },
            onBudgets = onNavigateToBudgets,
            onAnalytics = onNavigateToAnalytics,
            onFullResync = { showFullResyncDialog = true },
        )

        if (showHomeHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHomeHelpDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.home_help_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.home_help_categories_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.home_help_categories_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.home_help_tags_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.home_help_tags_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHomeHelpDialog = false }) {
                        Text(stringResource(R.string.home_help_got_it))
                    }
                },
            )
        }

        // Full Resync Confirmation Dialog
        if (showFullResyncDialog) {
            AlertDialog(
                onDismissRequest = { showFullResyncDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Full Resync")
                },
                text = {
                    Text(
                        "This will reprocess all SMS messages from scratch. " +
                        "Use this to fix issues caused by updated bank parsers.\n\n" +
                        "This may take a few seconds depending on your message history."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFullResyncDialog = false
                            viewModel.scanSmsMessages(forceResync = true)
                        }
                    ) {
                        Text("Resync All")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFullResyncDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // SMS Parsing Progress Dialog
        SmsParsingProgressDialog(
            isVisible = uiState.isScanning,
            workInfo = smsScanWorkInfo,
            onDismiss = { viewModel.cancelSmsScan() },
            onCancel = { viewModel.cancelSmsScan() }
        )

        if (uiState.showBreakdownDialog) {
            BreakdownDialog(
                currentMonthIncome = uiState.currentMonthIncome,
                currentMonthExpenses = uiState.currentMonthExpenses,
                currency = uiState.selectedCurrency,
                onNavigateToIncomeTransactions = {
                    viewModel.hideBreakdownDialog()
                    onNavigateToIncomeTransactions(transactionsPeriod)
                },
                onDismiss = { viewModel.hideBreakdownDialog() }
            )
        }

        if (showSpendTimelineSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSpendTimelineSheet = false },
                sheetState = spendTimelineSheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xl),
                ) {
                    Text(
                        text = stringResource(R.string.pay_period_explorer_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = Dimensions.Padding.content,
                            vertical = Spacing.sm,
                        ),
                    )
                    PayPeriodExplorerContent(
                        periodStartEpochDay = uiState.payPeriodStartEpochDay,
                        periodEndEpochDay = uiState.payPeriodEndEpochDay,
                        modifier = Modifier.fillMaxWidth(),
                        showViewTransactionsButton = true,
                        onViewTransactions = {
                            showSpendTimelineSheet = false
                            onNavigateToTransactions(transactionsPeriod)
                        },
                    )
                }
            }
        }

    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownDialog(
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currency: String = "INR",
    onNavigateToIncomeTransactions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = LocalDate.now()
    val currentPeriod = "${now.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} 1–${now.dayOfMonth}"
    val incomeFormatted = CurrencyFormatter.formatCurrency(
        currentMonthIncome.setScale(0, java.math.RoundingMode.HALF_UP), currency
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Where does $incomeFormatted come from?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            PennyWiseCardV2(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = Spacing.sm,
            ) {
                Text(
                    text = "Total of all credit / salary transactions automatically detected from your bank SMS messages during this pay period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            HorizontalDivider()

            Text(
                text = "This period  ·  $currentPeriod",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            // Income row — tappable to drill down into income transactions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToIncomeTransactions() }
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = "Income (from SMS)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View income transactions",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "+${CurrencyFormatter.formatCurrency(currentMonthIncome.abs(), currency)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (!isSystemInDarkTheme()) income_light else income_dark,
                )
            }

            BreakdownRow(label = "Expenses", amount = currentMonthExpenses, isIncome = false, currency = currency)

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: BigDecimal,
    isIncome: Boolean,
    isBold: Boolean = false,
    currency: String = "INR"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = "${if (isIncome) "+" else "-"}${CurrencyFormatter.formatCurrency(amount.abs(), currency)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isIncome) {
                if (!isSystemInDarkTheme()) income_light else income_dark
            } else {
                if (!isSystemInDarkTheme()) expense_light else expense_dark
            }
        )
    }
}

private enum class ListItemPosition { Top, Middle, Bottom, Single }

@Composable
private fun ListItemPosition.toShape(): RoundedCornerShape = when (this) {
    ListItemPosition.Top -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    ListItemPosition.Middle -> RoundedCornerShape(4.dp)
    ListItemPosition.Bottom -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    ListItemPosition.Single -> RoundedCornerShape(16.dp)
}

@Composable
private fun MenuListItem(
    headline: String,
    icon: @Composable () -> Unit,
    position: ListItemPosition,
    onClick: () -> Unit,
) {
    val shape = position.toShape()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content, vertical = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.secondary
            ) { icon() }
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Change this to switch between the two strip styles ───────────────────────
private enum class DateStripStyle { DotTrack, NumberStrip }
private val ACTIVE_DATE_STRIP = DateStripStyle.DotTrack

// ── Variant A: dot-on-a-line strip ───────────────────────────────────────────
@Composable
private fun DayDotTrack(
    days: List<LocalDate>,
    selectedDate: LocalDate,
    dailyTotals: Map<LocalDate, BigDecimal>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Box(modifier = modifier.fillMaxWidth()) {
        // Connecting line behind the dots
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(1.dp),
                ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEach { date ->
                val isSelected = date == selectedDate
                val hasActivity = (dailyTotals[date] ?: BigDecimal.ZERO) > BigDecimal.ZERO
                val isToday = date == today
                val dotSize by animateDpAsState(
                    targetValue = if (isSelected) 10.dp else if (hasActivity) 6.dp else 4.dp,
                    animationSpec = tween(150),
                    label = "dotSize",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSelectDate(date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.NARROW,
                            java.util.Locale.getDefault(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    hasActivity -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                },
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = if (isSelected) 11.sp else 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ── Variant D: number strip with accent underline ────────────────────────────
@Composable
private fun DayNumberStrip(
    days: List<LocalDate>,
    selectedDate: LocalDate,
    dailyTotals: Map<LocalDate, BigDecimal>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
    val underlineColor = MaterialTheme.colorScheme.primary
        days.forEach { date ->
            val isSelected = date == selectedDate
            val hasActivity = (dailyTotals[date] ?: BigDecimal.ZERO) > BigDecimal.ZERO
            val underlineAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(150),
                label = "underline",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSelectDate(date) }
                    .padding(bottom = 3.dp)
                    .drawBehind {
                        // Accent underline — color captured outside drawBehind scope
                        val lineY = size.height
                        drawLine(
                            color = underlineColor.copy(alpha = underlineAlpha),
                            start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, lineY),
                            end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, lineY),
                            strokeWidth = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.NARROW,
                        java.util.Locale.getDefault(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = if (isSelected) 13.sp else 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
                // Spend activity dot — tiny, only on days with transactions
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(
                            color = if (hasActivity)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSelected) 0.7f else 0.35f)
                            else
                                androidx.compose.ui.graphics.Color.Transparent,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun HomeFeedDateNavigator(
    uiState: HomeUiState,
    transactionsPeriod: String,
    onNavigateToTransactions: (String) -> Unit,
    onNavigateDateBy: (Int) -> Unit,
    onNavigateToDate: (LocalDate) -> Unit,
    getDailyExpensesForMonth: (LocalDate) -> kotlinx.coroutines.flow.Flow<Map<LocalDate, BigDecimal>>,
    getDailyExpensesBetween: (LocalDate, LocalDate) -> kotlinx.coroutines.flow.Flow<Map<LocalDate, BigDecimal>>,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val selectedDate = uiState.selectedDate
    var showDatePicker by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val spendColor = if (isDark) expense_dark else expense_light
    val incomeColor = if (isDark) income_dark else income_light

    val netSpend = remember(uiState.recentItems, uiState.isUnifiedMode) {
        var net = BigDecimal.ZERO
        fun apply(tx: com.spendly.tracker.data.database.entity.TransactionEntity, amount: BigDecimal) {
            if (tx.isExcludedFromTracking) return
            when (tx.transactionType) {
                TransactionType.EXPENSE,
                TransactionType.CREDIT -> net += amount
                TransactionType.INCOME -> net -= amount
                else -> Unit
            }
        }
        uiState.recentItems.forEach { item ->
            when (item) {
                is HomeRecentItem.SingleTransaction -> {
                    val tx = item.transaction
                    apply(tx, if (uiState.isUnifiedMode) item.convertedAmount ?: tx.amount else tx.amount)
                }
                is HomeRecentItem.GroupItem -> {
                    item.transactions.forEach { tx ->
                        apply(tx, if (uiState.isUnifiedMode) item.convertedAmounts[tx.id] ?: tx.amount else tx.amount)
                    }
                }
            }
        }
        net
    }

    val isNetIncome = netSpend < BigDecimal.ZERO
    val hasSpend = netSpend != BigDecimal.ZERO

    val stripStart = today.minusDays(6)
    val dailyTotals by produceState(
        initialValue = emptyMap<LocalDate, BigDecimal>(),
        key1 = stripStart,
        key2 = today,
    ) {
        getDailyExpensesBetween(stripStart, today).collect { value = it }
    }

    // Must track [today]: a bare remember { } freezes the 7-day window from first composition
    // (strip would never roll forward after midnight or when returning to Home days later).
    val stripDays = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }

    val dayLabel = when (selectedDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
    }.uppercase()

    // Column wrapper is required — this composable is called inside AnimatedVisibility
    // which uses Box internally; without it siblings would overlap instead of stack.
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // ── Date strip (A: dot track  |  D: number strip) ────────────────────
        when (ACTIVE_DATE_STRIP) {
            DateStripStyle.DotTrack -> DayDotTrack(
                days = stripDays,
                selectedDate = selectedDate,
                dailyTotals = dailyTotals,
                onSelectDate = onNavigateToDate,
            )
            DateStripStyle.NumberStrip -> DayNumberStrip(
                days = stripDays,
                selectedDate = selectedDate,
                dailyTotals = dailyTotals,
                onSelectDate = onNavigateToDate,
            )
        }

        // ── Copilot-style section header ──────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showDatePicker = true }
                        .padding(top = 2.dp, bottom = 2.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Change day",
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = -90f },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                if (hasSpend) {
                    Text(
                        text = "${if (isNetIncome) "+" else ""}${
                            CurrencyFormatter.formatCurrency(netSpend.abs(), uiState.selectedCurrency)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isNetIncome) incomeColor else spendColor,
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }

    if (showDatePicker) {
        CalendarBottomSheet(
            selectedDate = selectedDate,
            today = today,
            selectedCurrency = uiState.selectedCurrency,
            getDailyExpenses = getDailyExpensesForMonth,
            onDismiss = { showDatePicker = false },
            onDateSelected = { picked ->
                onNavigateToDate(picked)
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarBottomSheet(
    selectedDate: LocalDate,
    today: LocalDate,
    selectedCurrency: String,
    getDailyExpenses: (LocalDate) -> kotlinx.coroutines.flow.Flow<Map<LocalDate, java.math.BigDecimal>>,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    var displayMonth by remember(selectedDate) { mutableStateOf(selectedDate.withDayOfMonth(1)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dailyExpenses by produceState(
        initialValue = emptyMap<LocalDate, java.math.BigDecimal>(),
        key1 = displayMonth,
    ) {
        getDailyExpenses(displayMonth).collect { value = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Month navigation header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = displayMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { displayMonth = displayMonth.plusMonths(1) },
                    enabled = displayMonth.isBefore(today.withDayOfMonth(1))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = if (displayMonth.isBefore(today.withDayOfMonth(1)))
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // Day-of-week headers
            val dowLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
            Row(modifier = Modifier.fillMaxWidth()) {
                dowLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Day grid
            val firstDayOfMonth = displayMonth
            val startOffset = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0
            val daysInMonth = firstDayOfMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val expenseColor = if (isDark) expense_dark else expense_light

            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - startOffset + 1
                        val cellDate = if (dayNumber in 1..daysInMonth)
                            firstDayOfMonth.withDayOfMonth(dayNumber) else null
                        val isFuture = cellDate != null && cellDate.isAfter(today)
                        val isSelected = cellDate == selectedDate
                        val isToday = cellDate == today
                        val expense = cellDate?.let { dailyExpenses[it] }
                        val hasExpense = expense != null && expense > java.math.BigDecimal.ZERO

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .then(
                                    if (cellDate != null && !isFuture)
                                        Modifier.clickable { onDateSelected(cellDate) }
                                    else Modifier
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (cellDate != null) {
                                // Day number circle
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                // Expense amount below the day number
                                if (hasExpense && !isFuture) {
                                    Text(
                                        text = CurrencyFormatter.formatAbbreviated(
                                            expense!!.toDouble(), selectedCurrency
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = expenseColor.copy(alpha = if (isSelected) 0.85f else 1f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
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

@Composable
private fun HomeGoalsSummaryCard(
    summary: GoalsSummaryForHome,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (summary.totalTarget > BigDecimal.ZERO)
        (summary.totalCurrent.toFloat() / summary.totalTarget.toFloat()).coerceIn(0f, 1f)
    else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (summary.activeCount == 0) {
                    Text(
                        text = "Financial Goals",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Set a savings goal to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${summary.activeCount} ${if (summary.activeCount == 1) "goal" else "goals"} active",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${summary.avgProgressPercent}% saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View goals",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

