package com.pennywiseai.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
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
import com.pennywiseai.tracker.presentation.home.HomeScreen
import com.pennywiseai.tracker.presentation.subscriptions.SubscriptionsScreen
import com.pennywiseai.tracker.presentation.transactions.TransactionsScreen
import com.pennywiseai.tracker.ui.components.PennyWiseBottomNavigation
import com.pennywiseai.tracker.ui.components.PennyWiseNavigationRail
import com.pennywiseai.tracker.ui.components.SpotlightTutorial
import com.pennywiseai.tracker.ui.components.WhatsNewDialog
import com.pennywiseai.tracker.ui.screens.settings.AppearanceScreen
import com.pennywiseai.tracker.ui.screens.chat.ChatScreen
import com.pennywiseai.tracker.ui.screens.settings.SettingsScreen
import com.pennywiseai.tracker.ui.utils.LocalWindowSizeInfo
import com.pennywiseai.tracker.ui.utils.rememberWindowSizeInfo
import com.pennywiseai.tracker.ui.viewmodel.MainViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.ui.viewmodel.SpotlightViewModel
import com.pennywiseai.tracker.navigation.safePopBackStack
import com.pennywiseai.tracker.core.Constants
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
            PennyWiseNavigationRail(
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
                val homeViewModel: com.pennywiseai.tracker.presentation.home.HomeViewModel = hiltViewModel()
                val homeUiState by homeViewModel.uiState.collectAsState()
                val transactionsPeriod = com.pennywiseai.tracker.presentation.common.defaultTimePeriodNavParam(
                    homeUiState.useFinancialMonth
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    navController = rootNavController ?: navController,
                    blurEffects = themeState.blurEffectsEnabled,
                    onNavigateToPayPeriodSettings = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.PayPeriodSettings
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
                            com.pennywiseai.tracker.navigation.Loans
                        ) { launchSingleTop = true }
                    },
                    onLoanClick = { loanId ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.LoanDetail(loanId)
                        ) { launchSingleTop = true }
                    },
                    onNavigateToGoals = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.Goals
                        ) { launchSingleTop = true }
                    },
                    onNavigateToManageAccounts = {
                        navController.navigate("manage_accounts") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAddScreen = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.AddTransaction()
                        ) { launchSingleTop = true }
                    },
                    onTransactionClick = { transactionId ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
                        ) { launchSingleTop = true }
                    },
                    onGroupClick = { groupId ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.TransactionGroupDetail(groupId)
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
                            com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
                        ) { launchSingleTop = true }
                    },
                    onAddTransactionClick = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.AddTransaction()
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
                            com.pennywiseai.tracker.navigation.AddTransaction()
                        ) { launchSingleTop = true }
                    }
                )
            }

            composable(Constants.Routes.ANALYTICS) {
                com.pennywiseai.tracker.ui.screens.analytics.AnalyticsScreen(
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
                            com.pennywiseai.tracker.navigation.Insights
                        ) { launchSingleTop = true }
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
                com.pennywiseai.tracker.ui.screens.analytics.AnalyticsBreakdownScreen(
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
                com.pennywiseai.tracker.ui.screens.payperiod.PayPeriodExplorerScreen(
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
                com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupsScreen(
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
                            com.pennywiseai.tracker.navigation.BudgetGroupEdit(groupId)
                        ) { launchSingleTop = true }
                    },
                    onNavigateToCategory = { category, yearMonth, currency ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.TransactionsWithFilter(
                                category = category,
                                period = yearMonth,
                                currency = currency
                            )
                        ) { launchSingleTop = true }
                    }
                )
            }

            composable("behavioral_stats") {
                com.pennywiseai.tracker.ui.screens.behavioral.BehavioralStatsScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    },
                    onNavigateToTransaction = { transactionId ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
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
                            com.pennywiseai.tracker.navigation.MerchantAliases
                        ) { launchSingleTop = true }
                    },
                    onNavigateToManageAccounts = {
                        navController.navigate("manage_accounts") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRules = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.Rules
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
                    onNavigateToLoans = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.Loans
                        ) { launchSingleTop = true }
                    },
                    onNavigateToExchangeRates = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.ExchangeRates
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
                            com.pennywiseai.tracker.navigation.TransactionGroups
                        ) { launchSingleTop = true }
                    },
                    onNavigateToPayPeriodSettings = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.PayPeriodSettings
                        ) { launchSingleTop = true }
                    }
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
                com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("unrecognized_sms") {
                com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("faq") {
                com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("manage_accounts") {
                com.pennywiseai.tracker.presentation.accounts.ManageAccountsScreen(
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

            composable("import_statement") {
                com.pennywiseai.tracker.presentation.statement.ImportStatementScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }

            composable("add_account") {
                com.pennywiseai.tracker.presentation.accounts.AddAccountScreen(
                    onNavigateBack = {
                        navController.safePopBackStack()
                    }
                )
            }
        }

        // Bottom navigation — only on compact screens (medium/expanded use NavigationRail)
        if (!useNavigationRail && baseRoute in listOf(
                Constants.Routes.HOME,
                Constants.Routes.BUDGETS,
                Constants.Routes.ANALYTICS,
                Constants.Routes.SETTINGS,
            )
        ) {
            PennyWiseBottomNavigation(
                navController = navController,
                currentDestination = navBackStackEntry?.destination,
                navBarStyle = themeState.navBarStyle,
                blurEffects = themeState.blurEffectsEnabled,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Spotlight Tutorial overlay - outside Scaffold to overlay everything
        if (baseRoute == Constants.Routes.HOME && spotlightState.showTutorial && spotlightState.fabPosition != null) {
            val homeViewModel: com.pennywiseai.tracker.presentation.home.HomeViewModel? =
                navController.currentBackStackEntry?.let { hiltViewModel(it) }

            SpotlightTutorial(
                isVisible = true,
                targetPosition = spotlightState.fabPosition,
                message = "Tap here to scan your SMS messages for transactions",
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
