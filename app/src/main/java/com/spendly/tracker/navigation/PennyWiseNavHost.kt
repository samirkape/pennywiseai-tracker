package com.spendly.tracker.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.spendly.tracker.ui.LocalNavAnimatedVisibilityScope
import com.spendly.tracker.ui.LocalSharedTransitionScope
import com.spendly.tracker.ui.MainScreen
import com.spendly.tracker.ui.viewmodel.ThemeViewModel

/**
 * Safe version of popBackStack that prevents rapid back presses from causing
 * screen overlap. Only pops if the current entry is fully RESUMED.
 */
fun NavHostController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

@Composable
fun PennyWiseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    startDestination: Any = Home(),
    onEditComplete: () -> Unit = {}
) {
    // Use a stable start destination
    val stableStartDestination = remember { startDestination }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
    NavHost(
        navController = navController,
        startDestination = stableStartDestination,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
    ) {
        composable<AppLock>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.AppLockScreen(
                onUnlocked = {
                    navController.navigate(Home()) {
                        launchSingleTop = true
                        popUpTo(AppLock) { inclusive = true }
                    }
                }
            )
        }
        composable<OnBoarding>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.onboarding.OnBoardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Home()) {
                        launchSingleTop = true
                        popUpTo(OnBoarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Home>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val homeArgs = backStackEntry.toRoute<Home>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                MainScreen(
                    rootNavController = navController,
                    initialCategory = homeArgs.category,
                    initialMerchant = homeArgs.merchant,
                    initialPeriod = homeArgs.period,
                    initialCurrency = homeArgs.currency,
                    initialTransactionType = homeArgs.transactionType,
                    initialStartDateEpochDay = homeArgs.startDateEpochDay,
                    initialEndDateEpochDay = homeArgs.endDateEpochDay,
                    initialPaymentMode = homeArgs.paymentMode,
                    initialBankName = homeArgs.bankName,
                    initialAccountLast4 = homeArgs.accountLast4
                )
            }
        }

        composable<Settings>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.settings.SettingsScreen(
                themeViewModel = themeViewModel,
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToCategories = {
                    navController.navigate(Categories) { launchSingleTop = true }
                },
                onNavigateToUnrecognizedSms = {
                    navController.navigate(UnrecognizedSms) { launchSingleTop = true }
                },
                onNavigateToMerchantAliases = {
                    navController.navigate(MerchantAliases) { launchSingleTop = true }
                },
                onNavigateToBudgets = {
                    navController.navigate(BudgetGroups) { launchSingleTop = true }
                },
                onNavigateToSubscriptions = {
                    navController.navigate(Subscriptions) { launchSingleTop = true }
                },
                onNavigateToExchangeRates = {
                    navController.navigate(ExchangeRates) { launchSingleTop = true }
                },
                onNavigateToImportStatement = {
                    navController.navigate(ImportStatement) { launchSingleTop = true }
                },
                onNavigateToTransactionGroups = {
                    navController.navigate(TransactionGroups) { launchSingleTop = true }
                },
                onNavigateToPayPeriodSettings = {
                    navController.navigate(PayPeriodSettings) { launchSingleTop = true }
                }
            )
        }

        composable<Subscriptions>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.subscriptions.SubscriptionsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onAddSubscriptionClick = {
                    navController.navigate(AddTransaction()) { launchSingleTop = true }
                },
            )
        }

        composable<PayPeriodSettings>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.settings.PayPeriodSettingsScreen(
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable<Categories>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.categories.CategoriesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<TransactionDetail>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val transactionDetail = backStackEntry.toRoute<TransactionDetail>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                com.spendly.tracker.presentation.transactions.TransactionDetailScreen(
                    transactionId = transactionDetail.transactionId,
                    onNavigateBack = {
                        onEditComplete()
                        navController.safePopBackStack()
                    },
                    onNavigateToLoanDetail = { loanId ->
                        navController.navigate(LoanDetail(loanId)) {
                            launchSingleTop = true
                        }
                    },
                    onFindSimilar = { merchant ->
                        navController.navigate(TransactionsByMerchant(merchant)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTransactionDetail = { targetTransactionId ->
                        navController.navigate(TransactionDetail(targetTransactionId))
                    }
                )
            }
        }
        
        composable<AddTransaction>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.add.AddScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<MerchantAliases>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.settings.MerchantAliasesScreen(
                onNavigateBack = { navController.safePopBackStack() },
            )
        }

        composable<UnrecognizedSms>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onAddAsTransaction = { smsId ->
                    navController.navigate(AddTransaction(unrecognizedSmsId = smsId)) {
                        launchSingleTop = true
                    }
                },
                onCreateRule = { smsBody, sender ->
                    navController.navigate(
                        EditQuickKeywordRule(
                            prefilledKeywords = smsBody,
                            prefilledName = sender,
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
            )
        }
        
        composable<Faq>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.settings.FAQScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<Rules>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.rules.RulesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToCreateRule = {
                    navController.navigate(CreateRule()) {
                        launchSingleTop = true
                    }
                },
                onNavigateToEditRule = { ruleId ->
                    navController.navigate(CreateRule(ruleId = ruleId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToQuickKeywordRules = {
                    navController.navigate(QuickKeywordRules) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<QuickKeywordRules>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } },
        ) {
            com.spendly.tracker.ui.screens.rules.QuickKeywordRulesScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToEdit = { ruleId ->
                    navController.navigate(EditQuickKeywordRule(ruleId = ruleId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<EditQuickKeywordRule>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<EditQuickKeywordRule>()
            com.spendly.tracker.ui.screens.rules.EditQuickKeywordRuleScreen(
                ruleId = route.ruleId,
                onNavigateBack = { navController.safePopBackStack() },
                prefilledKeywords = route.prefilledKeywords,
                prefilledName = route.prefilledName,
            )
        }

        composable<CreateRule>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) { backStackEntry ->
            val createRule = backStackEntry.toRoute<CreateRule>()
            val rulesViewModel: com.spendly.tracker.ui.viewmodel.RulesViewModel = hiltViewModel()

            // Collect rules from the flow to find the existing rule
            val rules by rulesViewModel.rules.collectAsStateWithLifecycle()
            val existingRule = createRule.ruleId?.let { ruleId ->
                rules.firstOrNull { it.id == ruleId }
            }

            com.spendly.tracker.ui.screens.rules.CreateRuleScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onSaveRule = { rule ->
                    rulesViewModel.createRule(rule)
                    navController.safePopBackStack()
                },
                existingRule = existingRule
            )
        }
        
        composable<AccountDetail>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.accounts.AccountDetailScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onTransactionClick = { id ->
                    navController.navigate(TransactionDetail(id)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<BudgetGroups>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.budgetgroups.BudgetGroupsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToGroupEdit = { groupId ->
                    navController.navigate(BudgetGroupEdit(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCategory = { category, yearMonth, currency ->
                    navController.navigate(TransactionsWithFilter(category, yearMonth, currency)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<BudgetGroupEdit>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.budgetgroups.BudgetGroupEditScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<Loans>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.loans.LoansScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToLoanDetail = { loanId ->
                    navController.navigate(LoanDetail(loanId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<LoanDetail>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.loans.LoanDetailScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToTransactionDetail = { txId ->
                    navController.navigate(TransactionDetail(txId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<TransactionGroups>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.groups.TransactionGroupsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToGroupDetail = { groupId ->
                    navController.navigate(TransactionGroupDetail(groupId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<TransactionGroupDetail>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.groups.TransactionGroupDetailScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToTransactionDetail = { txId ->
                    navController.navigate(TransactionDetail(txId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ExchangeRates>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.exchangerates.ExchangeRatesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<ImportStatement>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.statement.ImportStatementScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<TransactionsWithFilter>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionsWithFilter>()
            com.spendly.tracker.presentation.transactions.TransactionsScreen(
                initialCategory = args.category,
                initialPeriod = args.period,
                initialCurrency = args.currency,
                onNavigateBack = { navController.safePopBackStack() },
                onTransactionClick = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId)) { launchSingleTop = true }
                },
                onAddTransactionClick = {
                    navController.navigate(AddTransaction()) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(Settings) { launchSingleTop = true }
                }
            )
        }

        composable<TransactionsByMerchant>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionsByMerchant>()
            com.spendly.tracker.presentation.transactions.TransactionsScreen(
                initialMerchant = args.merchant,
                initialPeriod = "ALL",
                onNavigateBack = { navController.safePopBackStack() },
                onTransactionClick = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId)) { launchSingleTop = true }
                },
                onAddTransactionClick = {
                    navController.navigate(AddTransaction()) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(Settings) { launchSingleTop = true }
                }
            )
        }

        composable<Insights>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.insights.InsightsScreen(
                onBack = { navController.safePopBackStack() },
                onNavigateToBehavioralStats = { month ->
                    navController.navigate(BehavioralStats(month.toString())) { launchSingleTop = true }
                },
                onNavigateToQuickCategorize = {
                    navController.navigate(QuickCategorize) { launchSingleTop = true }
                },
                onNavigateToTransactions = { category, merchant, period, currency, transactionType, startDateEpoch, endDateEpoch, paymentMode, bankName, accountLast4 ->
                    navController.navigate(
                        Home(
                            category = category,
                            merchant = merchant,
                            period = period,
                            currency = currency,
                            transactionType = transactionType,
                            startDateEpochDay = startDateEpoch,
                            endDateEpochDay = endDateEpoch,
                            paymentMode = paymentMode,
                            bankName = bankName,
                            accountLast4 = accountLast4
                        )
                    ) {
                        launchSingleTop = true
                        popUpTo(Home()) { inclusive = true }
                    }
                }
            )
        }

        composable<BehavioralStats>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.behavioral.BehavioralStatsScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToTransaction = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId)) { launchSingleTop = true }
                },
                onNavigateToTransactionsMultiCategory = { categories, period, currency, startDateEpoch, endDateEpoch ->
                    navController.navigate(
                        TransactionsByCategories(
                            categories = categories,
                            period = period,
                            currency = currency,
                            startDateEpochDay = startDateEpoch,
                            endDateEpochDay = endDateEpoch
                        )
                    ) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<TransactionsByCategories>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionsByCategories>()
            com.spendly.tracker.presentation.transactions.TransactionsScreen(
                initialCategories = args.categories,
                initialPeriod = args.period,
                initialCurrency = args.currency,
                initialPeriodStartEpoch = args.startDateEpochDay,
                initialPeriodEndEpoch = args.endDateEpochDay,
                onNavigateBack = { navController.safePopBackStack() },
                onTransactionClick = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId)) { launchSingleTop = true }
                },
                onAddTransactionClick = {
                    navController.navigate(AddTransaction()) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(Settings) { launchSingleTop = true }
                }
            )
        }

        composable<QuickCategorize>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.ui.screens.insights.QuickCategorizeScreen(
                onBack = { navController.safePopBackStack() }
            )
        }

        composable<Goals>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.goals.GoalsScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onGoalClick = { goalId ->
                    navController.navigate(GoalDetail(goalId)) { launchSingleTop = true }
                },
                onCreateGoal = {
                    navController.navigate(GoalEdit()) { launchSingleTop = true }
                }
            )
        }

        composable<GoalDetail>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.goals.GoalDetailScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onNavigateToEdit = { goalId ->
                    navController.navigate(GoalEdit(goalId)) { launchSingleTop = true }
                },
                onNavigateToTransaction = { txId ->
                    navController.navigate(TransactionDetail(txId)) { launchSingleTop = true }
                }
            )
        }

        composable<GoalEdit>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.spendly.tracker.presentation.goals.GoalEditScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onSaved = { navController.safePopBackStack() }
            )
        }

    }
    }
    }
}