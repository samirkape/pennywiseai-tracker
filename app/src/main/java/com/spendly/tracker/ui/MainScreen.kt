package com.spendly.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import com.spendly.tracker.ui.components.BannerAdView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spendly.tracker.presentation.home.HomeScreen
import com.spendly.tracker.presentation.subscriptions.SubscriptionsScreen
import com.spendly.tracker.presentation.transactions.TransactionsScreen
import com.spendly.tracker.ui.components.SpendlyBottomNavigation
import com.spendly.tracker.ui.components.SpendlyNavigationRail
import com.spendly.tracker.ui.components.SpotlightTutorial
import com.spendly.tracker.ui.components.WhatsNewDialog
import com.spendly.tracker.ui.screens.settings.AppearanceScreen
import com.spendly.tracker.ui.screens.chat.ChatScreen
import com.spendly.tracker.ui.screens.settings.SettingsScreen
import com.spendly.tracker.ui.utils.LocalWindowSizeInfo
import com.spendly.tracker.ui.utils.rememberWindowSizeInfo
import com.spendly.tracker.ui.viewmodel.MainViewModel
import com.spendly.tracker.ui.viewmodel.ThemeViewModel
import com.spendly.tracker.ui.viewmodel.SpotlightViewModel
import com.spendly.tracker.navigation.safePopBackStack
import com.spendly.tracker.core.Constants
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun MainScreen(
    rootNavController: NavHostController? = null,
    navController: NavHostController = rememberNavController(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    spotlightViewModel: SpotlightViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
    initialCategory: String? = null,
    initialMerchant: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null,
    initialTransactionType: String? = null,
    initialStartDateEpochDay: Long? = null,
    initialEndDateEpochDay: Long? = null,
    initialPaymentMode: String? = null,
    initialBankName: String? = null,
    initialAccountLast4: String? = null,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore("?") ?: ""
    val spotlightState by spotlightViewModel.spotlightState.collectAsState()
    val themeState by themeViewModel.themeUiState.collectAsState()

    // What's New dialog state
    val whatsNewVersion by mainViewModel.whatsNewVersion.collectAsState()
    val isPremium by mainViewModel.isPremium.collectAsState()

    // Haze state for blur effects
    val hazeState = remember { HazeState() }

    val isBottomNavTabRoute = baseRoute in setOf(
        Constants.Routes.HOME,
        Constants.Routes.BUDGETS,
        Constants.Routes.ANALYTICS,
        Constants.Routes.SETTINGS,
    )
    val isHomeScreen = baseRoute == Constants.Routes.HOME

    // Back from a bottom tab other than Home switches to Home (avoids stack overlap).
    // Pushed routes (transactions, chat, etc.) use normal pop via system back.
    BackHandler(enabled = !isHomeScreen && isBottomNavTabRoute) {
        navController.navigate(Constants.Routes.HOME) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Navigate to transactions with filter if provided
    LaunchedEffect(
        initialCategory,
        initialMerchant,
        initialPeriod,
        initialCurrency,
        initialTransactionType,
        initialStartDateEpochDay,
        initialEndDateEpochDay,
        initialPaymentMode,
        initialBankName,
        initialAccountLast4
    ) {
        if (initialCategory != null || initialMerchant != null || initialTransactionType != null || initialStartDateEpochDay != null) {
            val route = buildString {
                append("transactions")
                val params = mutableListOf<String>()
                initialCategory?.let {
                    val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                    params.add("category=$encoded")
                }
                initialMerchant?.let {
                    val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                    params.add("merchant=$encoded")
                }
                initialPeriod?.let { params.add("period=$it") }
                initialCurrency?.let { params.add("currency=$it") }
                initialTransactionType?.let { params.add("type=$it") }
                initialStartDateEpochDay?.let { params.add("startDateEpoch=$it") }
                initialEndDateEpochDay?.let { params.add("endDateEpoch=$it") }
                initialPaymentMode?.let { params.add("paymentMode=$it") }
                initialBankName?.let {
                    val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                    params.add("bankName=$encoded")
                }
                initialAccountLast4?.let {
                    val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                    params.add("accountLast4=$encoded")
                }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    val windowSizeInfo = rememberWindowSizeInfo()
    val useNavigationRail = windowSizeInfo.useNavigationRail

    CompositionLocalProvider(LocalWindowSizeInfo provides windowSizeInfo) {
    Row(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
        // NavigationRail for medium / expanded screens (tablets, landscape)
        if (useNavigationRail) {
            SpendlyNavigationRail(
                navController = navController,
                currentDestination = navBackStackEntry?.destination,
            )
        }

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        ) {
        // What's New Dialog
        whatsNewVersion?.let { version ->
            WhatsNewDialog(
                version = version,
                onDismiss = { mainViewModel.dismissWhatsNew() }
            )
        }

        // NavHost — NO padding, fills the full screen
        NavHost(
            navController = navController,
            startDestination = Constants.Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Constants.Routes.HOME) {
                val homeViewModel: com.spendly.tracker.presentation.home.HomeViewModel = hiltViewModel()
                val homeUiState by homeViewModel.uiState.collectAsState()
                val transactionsPeriod = com.spendly.tracker.presentation.common.defaultTimePeriodNavParam(
                    homeUiState.useFinancialMonth
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    navController = rootNavController ?: navController,
                    blurEffects = themeState.blurEffectsEnabled,
                    onNavigateToPayPeriodSettings = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.PayPeriodSettings
                        ) { launchSingleTop = true }
                    },
                    onNavigateToPayPeriodExplorer = { startEpoch, endEpoch ->
                        navController.navigate(
                            "${Constants.Routes.PAY_PERIOD_EXPLORER}/$startEpoch/$endEpoch",
                        ) { launchSingleTop = true }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Constants.Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTransactions = { period ->
                        navController.navigate("transactions?period=$period") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToInvestmentTransactions = { period ->
                        navController.navigate("transactions?period=$period&type=INVESTMENT") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToIncomeTransactions = { period ->
                        navController.navigate("transactions?period=$period&type=INCOME") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAnalytics = {
                        navController.navigate(Constants.Routes.ANALYTICS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTransactionsWithSearch = { period ->
                        navController.navigate("transactions?period=$period&focusSearch=true") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToThisWeekTransactions = { startEpochDay, endEpochDay ->
                        navController.navigate("transactions?startDateEpoch=$startEpochDay&endDateEpoch=$endEpochDay") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSubscriptions = {
                        navController.navigate("subscriptions") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBudgets = {
                        navController.navigate(Constants.Routes.BUDGETS) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToLoans = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.Loans
                        ) { launchSingleTop = true }
                    },
                    onLoanClick = { loanId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.LoanDetail(loanId)
                        ) { launchSingleTop = true }
                    },
                    onNavigateToGoals = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.Goals
                        ) { launchSingleTop = true }
                    },
                    onNavigateToManageAccounts = {
                        navController.navigate("manage_accounts") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAddScreen = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.AddTransaction()
                        ) { launchSingleTop = true }
                    },
                    onTransactionClick = { transactionId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionDetail(transactionId)
                        ) { launchSingleTop = true }
                    },
                    onGroupClick = { groupId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionGroupDetail(groupId)
                        ) { launchSingleTop = true }
                    },
                    onTransactionTypeClick = { type ->
                        val route = buildString {
                            append("transactions?period=$transactionsPeriod")
                            type?.let { append("&type=$it") }
                        }
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    },
                    onFabPositioned = { position ->
                        spotlightViewModel.updateFabPosition(position)
                    }
                )
            }

            composable(
                route = "transactions?category={category}&merchant={merchant}&period={period}&currency={currency}&focusSearch={focusSearch}&type={type}&categories={categories}&startDateEpoch={startDateEpoch}&endDateEpoch={endDateEpoch}&paymentMode={paymentMode}&bankName={bankName}&accountLast4={accountLast4}",
                arguments = listOf(
                    navArgument("category") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("merchant") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("period") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("currency") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("focusSearch") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("categories") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("startDateEpoch") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("endDateEpoch") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("paymentMode") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("bankName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("accountLast4") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category")
                val merchant = backStackEntry.arguments?.getString("merchant")
                val period = backStackEntry.arguments?.getString("period")
                val currency = backStackEntry.arguments?.getString("currency")
                val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: false
                val transactionType = backStackEntry.arguments?.getString("type")
                val categories = backStackEntry.arguments?.getString("categories")
                val periodStartEpoch = backStackEntry.arguments?.getString("startDateEpoch")?.toLongOrNull()
                val periodEndEpoch = backStackEntry.arguments?.getString("endDateEpoch")?.toLongOrNull()
                val paymentMode = backStackEntry.arguments?.getString("paymentMode")
                val bankName = backStackEntry.arguments?.getString("bankName")
                val accountLast4 = backStackEntry.arguments?.getString("accountLast4")

                TransactionsScreen(
                    modifier = Modifier.imePadding(),
                    initialCategory = category,
                    initialMerchant = merchant,
                    initialPeriod = period,
                    initialCurrency = currency,
                    focusSearch = focusSearch,
                    initialTransactionType = transactionType,
                    initialPaymentMode = paymentMode,
                    initialCategories = categories,
                    initialPeriodStartEpoch = periodStartEpoch,
                    initialPeriodEndEpoch = periodEndEpoch,
                    initialBankName = bankName,
                    initialAccountLast4 = accountLast4,
                    onNavigateBack = {
                        navController.safePopBackStack()
                    },
                    onTransactionClick = { transactionId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionDetail(transactionId)
                        ) { launchSingleTop = true }
                    },
                    onAddTransactionClick = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.AddTransaction()
                        ) { launchSingleTop = true }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Constants.Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("subscriptions") {
                SubscriptionsScreen(
                    onNavigateBack = {
                        if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            if (!navController.popBackStack()) {
                                navController.navigate(Constants.Routes.HOME) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                    onAddSubscriptionClick = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.AddTransaction()
                        ) { launchSingleTop = true }
                    }
                )
            }

            composable("prepaid_expenses") {
                com.spendly.tracker.presentation.prepaid.PrepaidExpensesScreen(
                    onNavigateBack = {
                        if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            if (!navController.popBackStack()) {
                                navController.navigate(Constants.Routes.HOME) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )
            }

            composable(Constants.Routes.ANALYTICS) {
                com.spendly.tracker.ui.screens.analytics.AnalyticsScreen(
                    onNavigateToChat = {
                        navController.navigate(Constants.Routes.CHAT) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTransactions = { category, merchant, period, currency, transactionType, startDateEpoch, endDateEpoch, paymentMode, bankName, accountLast4 ->
                        val route = buildString {
                            append("transactions")
                            val params = mutableListOf<String>()
                            category?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("category=$encoded")
                            }
                            merchant?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("merchant=$encoded")
                            }
                            period?.let {
                                params.add("period=$it")
                            }
                            currency?.let {
                                params.add("currency=$it")
                            }
                            transactionType?.let {
                                params.add("type=$it")
                            }
                            startDateEpoch?.let {
                                params.add("startDateEpoch=$it")
                            }
                            endDateEpoch?.let {
                                params.add("endDateEpoch=$it")
                            }
                            paymentMode?.let {
                                params.add("paymentMode=$it")
                            }
                            bankName?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("bankName=$encoded")
                            }
                            accountLast4?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("accountLast4=$encoded")
                            }
                            if (params.isNotEmpty()) {
                                append("?")
                                append(params.joinToString("&"))
                            }
                        }
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(Constants.Routes.HOME) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToBehavioralStats = {
                        navController.navigate("behavioral_stats") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBreakdown = { tileKey ->
                        navController.navigate("analytics_breakdown/$tileKey") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToInsights = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.Insights
                        ) { launchSingleTop = true }
                    },
                    onNavigateToCreditCardAnalytics = { startEpoch, endEpoch, currency ->
                        navController.navigate("${Constants.Routes.CREDIT_CARD_ANALYTICS}/$startEpoch/$endEpoch/$currency") {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Constants.Routes.ANALYTICS_BREAKDOWN,
                arguments = listOf(
                    navArgument("tileKey") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val tileKey = backStackEntry.arguments?.getString("tileKey") ?: "outflow"
                com.spendly.tracker.ui.screens.analytics.AnalyticsBreakdownScreen(
                    tileKey = tileKey,
                    navController = navController,
                    onNavigateBack = { navController.safePopBackStack() },
                    onNavigateToTransactions = { category, merchant, period, currency, transactionType, startDateEpoch, endDateEpoch, paymentMode, bankName, accountLast4 ->
                        val route = buildString {
                            append("transactions")
                            val params = mutableListOf<String>()
                            category?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("category=$encoded")
                            }
                            merchant?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("merchant=$encoded")
                            }
                            period?.let { params.add("period=$it") }
                            currency?.let { params.add("currency=$it") }
                            transactionType?.let { params.add("type=$it") }
                            startDateEpoch?.let { params.add("startDateEpoch=$it") }
                            endDateEpoch?.let { params.add("endDateEpoch=$it") }
                            paymentMode?.let { params.add("paymentMode=$it") }
                            bankName?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("bankName=$encoded")
                            }
                            accountLast4?.let {
                                val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                params.add("accountLast4=$encoded")
                            }
                            if (params.isNotEmpty()) {
                                append("?")
                                append(params.joinToString("&"))
                            }
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onNavigateToCreditCardAnalytics = { startEpoch, endEpoch, currency ->
                        navController.navigate("${Constants.Routes.CREDIT_CARD_ANALYTICS}/$startEpoch/$endEpoch/$currency") {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = "${Constants.Routes.CREDIT_CARD_ANALYTICS}/{startEpoch}/{endEpoch}/{currency}",
                arguments = listOf(
                    navArgument("startEpoch") { type = NavType.LongType },
                    navArgument("endEpoch") { type = NavType.LongType },
                    navArgument("currency") { type = NavType.StringType },
                ),
            ) {
                com.spendly.tracker.ui.screens.analytics.CreditCardAnalyticsScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    onNavigateToTransaction = { txnId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionDetail(txnId)
                        )
                    },
                )
            }

            composable(
                route = "${Constants.Routes.PAY_PERIOD_EXPLORER}/{startEpoch}/{endEpoch}",
                arguments = listOf(
                    navArgument("startEpoch") { type = NavType.LongType },
                    navArgument("endEpoch") { type = NavType.LongType },
                ),
            ) { backStackEntry ->
                val start = backStackEntry.arguments?.getLong("startEpoch") ?: 0L
                val end = backStackEntry.arguments?.getLong("endEpoch") ?: 0L
                com.spendly.tracker.ui.screens.payperiod.PayPeriodExplorerScreen(
                    periodStartEpochDay = start,
                    periodEndEpochDay = end,
                    onNavigateBack = { navController.safePopBackStack() },
                )
            }

            composable(Constants.Routes.CHAT) {
                ChatScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    onNavigateToSettings = {
                        navController.navigate(Constants.Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Constants.Routes.BUDGETS) {
                com.spendly.tracker.presentation.budgetgroups.BudgetGroupsScreen(
                    onNavigateBack = {
                        navController.navigate(Constants.Routes.HOME) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToGroupEdit = { groupId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.BudgetGroupEdit(groupId)
                        ) { launchSingleTop = true }
                    },
                    onNavigateToCategory = { category, yearMonth, currency ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionsWithFilter(
                                category = category,
                                period = yearMonth,
                                currency = currency
                            )
                        ) { launchSingleTop = true }
                    }
                )
            }

            composable("behavioral_stats") {
                com.spendly.tracker.ui.screens.behavioral.BehavioralStatsScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    },
                    onNavigateToTransaction = { transactionId ->
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionDetail(transactionId)
                        ) { launchSingleTop = true }
                    },
                    onNavigateToTransactionsMultiCategory = { categories, period, currency, startDateEpoch, endDateEpoch ->
                        val route = buildString {
                            append("transactions")
                            val params = mutableListOf("categories=$categories")
                            period?.let { params.add("period=$it") }
                            currency?.let { params.add("currency=$it") }
                            startDateEpoch?.let { params.add("startDateEpoch=$it") }
                            endDateEpoch?.let { params.add("endDateEpoch=$it") }
                            append("?")
                            append(params.joinToString("&"))
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }

            composable(Constants.Routes.SETTINGS) {
                SettingsScreen(
                    themeViewModel = themeViewModel,
                    onNavigateBack = {
                        navController.safePopBackStack()
                    },
                    onNavigateToCategories = {
                        navController.navigate("categories") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToUnrecognizedSms = {
                        navController.navigate("unrecognized_sms") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToMerchantAliases = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.MerchantAliases
                        ) { launchSingleTop = true }
                    },
                    onNavigateToManageAccounts = {
                        navController.navigate("manage_accounts") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRules = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.Rules
                        ) { launchSingleTop = true }
                    },
                    onNavigateToBudgets = {
                        navController.navigate(Constants.Routes.BUDGETS) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSubscriptions = {
                        navController.navigate("subscriptions") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPrepaidExpenses = {
                        navController.navigate("prepaid_expenses") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLoans = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.Loans
                        ) { launchSingleTop = true }
                    },
                    onNavigateToExchangeRates = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.ExchangeRates
                        ) { launchSingleTop = true }
                    },
                    onNavigateToAppearance = {
                        navController.navigate("appearance") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToImportStatement = {
                        navController.navigate("import_statement") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTransactionGroups = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.TransactionGroups
                        ) { launchSingleTop = true }
                    },
                    onNavigateToPayPeriodSettings = {
                        rootNavController?.navigate(
                            com.spendly.tracker.navigation.PayPeriodSettings
                        ) { launchSingleTop = true }
                    },
                    onNavigateToSmsParserDebug = {
                        navController.navigate(Constants.Routes.SMS_PARSER_DEBUG) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Constants.Routes.SMS_PARSER_DEBUG) {
                com.spendly.tracker.ui.screens.settings.SmsParserDebugScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            composable("appearance") {
                AppearanceScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    },
                    themeViewModel = themeViewModel
                )
            }

            composable("categories") {
                com.spendly.tracker.presentation.categories.CategoriesScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("unrecognized_sms") {
                com.spendly.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("faq") {
                com.spendly.tracker.ui.screens.settings.FAQScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("manage_accounts") {
                com.spendly.tracker.presentation.accounts.ManageAccountsScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    },
                    onNavigateToAddAccount = {
                        navController.navigate("add_account") {
                            launchSingleTop = true
                        }
                    }
                )
            }


            composable("add_account") {
                com.spendly.tracker.presentation.accounts.AddAccountScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }
        }

        // Bottom navigation + banner ad overlay
        // Column stacks from top to bottom: BannerAdView (top) → SpendlyBottomNavigation (bottom)
        // aligned to BottomCenter so the nav stays at the very bottom edge
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            if (Constants.Ads.ENABLED && !isPremium) {
                BannerAdView(adUnitId = Constants.Ads.HOME_BANNER_UNIT_ID)
            }
            if (!useNavigationRail && baseRoute in listOf(
                    Constants.Routes.HOME,
                    Constants.Routes.BUDGETS,
                    Constants.Routes.ANALYTICS,
                    Constants.Routes.SETTINGS,
                )
            ) {
                SpendlyBottomNavigation(
                    navController = navController,
                    currentDestination = navBackStackEntry?.destination,
                    navBarStyle = themeState.navBarStyle,
                    blurEffects = themeState.blurEffectsEnabled,
                    hazeState = hazeState,
                )
            }
        }

        // Spotlight Tutorial overlay - outside Scaffold to overlay everything
        if (baseRoute == Constants.Routes.HOME && spotlightState.showTutorial && spotlightState.fabPosition != null) {
            val homeViewModel: com.spendly.tracker.presentation.home.HomeViewModel? =
                navController.currentBackStackEntry?.let { hiltViewModel(it) }

            SpotlightTutorial(
                isVisible = true,
                targetPosition = spotlightState.fabPosition,
                message = "Long-press here to scan your SMS messages for transactions",
                onDismiss = {
                    spotlightViewModel.dismissTutorial()
                },
                onTargetClick = {
                    homeViewModel?.scanSmsMessages()
                }
            )
        }
        } // end inner Box
    } // end Row
    } // end CompositionLocalProvider
}
