package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import java.math.BigDecimal

/**
 * Hero spend card with period context, amount, trend sparkline, and optional "More stats" fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeroPager(
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentMonthExpenses: BigDecimal,
    currentMonthIncome: BigDecimal,
    currentMonthTotal: BigDecimal,
    currentMonthInvestment: BigDecimal,
    spendingHistory: List<BigDecimal>,
    lastMonthSpendingHistory: List<BigDecimal>,
    periodDayLabel: String,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    spendingPeriodLabel: String,
    useFinancialMonth: Boolean,
    onToggleSpendingMode: () -> Unit,
    onCurrencySelect: (String) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onSpendSoFarClick: (() -> Unit)? = null,
    onNavigateToInvestmentTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onShowBreakdown: () -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)?,
    moreStatsIncomeText: String? = null,
    moreStatsIncomeSubLabel: String? = null,
    moreStatsTopCategoryName: String? = null,
    moreStatsTopCategorySubLabel: String? = null,
    moreStatsPaceText: String? = null,
    moreStatsPaceSubLabel: String? = null,
    moreStatsLoanLabel: String? = null,
    moreStatsLoanText: String? = null,
    moreStatsSubscriptionsLabel: String? = null,
    moreStatsSubscriptionsValue: String? = null,
    onMoreStatsIncomeClick: (() -> Unit)? = null,
    onMoreStatsTopCategoryClick: (() -> Unit)? = null,
    onMoreStatsPaceClick: (() -> Unit)? = null,
    onMoreStatsLoanClick: (() -> Unit)? = null,
    onMoreStatsSubscriptionsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    var showOptionsSheet by remember { mutableStateOf(false) }

    HeroSpendCard(
        modifier = modifier,
        blurEffects = blurEffects,
        hazeState = hazeState,
        monthlyChange = monthlyChange,
        monthlyChangePercent = monthlyChangePercent,
        currency = currency,
        currentMonthExpenses = currentMonthExpenses,
        currentMonthIncome = currentMonthIncome,
        currentMonthTotal = currentMonthTotal,
        currentMonthInvestment = currentMonthInvestment,
        spendingHistory = spendingHistory,
        lastMonthSpendingHistory = lastMonthSpendingHistory,
        periodDayLabel = periodDayLabel,
        availableCurrencies = availableCurrencies,
        isUnifiedMode = isUnifiedMode,
        spendingPeriodLabel = spendingPeriodLabel,
        useFinancialMonth = useFinancialMonth,
        onToggleSpendingMode = onToggleSpendingMode,
        onCurrencySelect = onCurrencySelect,
        onNavigateToTransactions = onNavigateToTransactions,
        onSpendSoFarClick = onSpendSoFarClick,
        onNavigateToInvestmentTransactions = onNavigateToInvestmentTransactions,
        onNavigateToBudgets = onNavigateToBudgets,
        onShowBreakdown = onShowBreakdown,
        onOpenPayPeriodSettings = onOpenPayPeriodSettings,
        onPeriodChipClick = { showOptionsSheet = true },
        moreStatsIncomeText = moreStatsIncomeText,
        moreStatsIncomeSubLabel = moreStatsIncomeSubLabel,
        moreStatsTopCategoryName = moreStatsTopCategoryName,
        moreStatsTopCategorySubLabel = moreStatsTopCategorySubLabel,
        moreStatsPaceText = moreStatsPaceText,
        moreStatsPaceSubLabel = moreStatsPaceSubLabel,
        moreStatsLoanLabel = moreStatsLoanLabel,
        moreStatsLoanText = moreStatsLoanText,
        moreStatsSubscriptionsLabel = moreStatsSubscriptionsLabel,
        moreStatsSubscriptionsValue = moreStatsSubscriptionsValue,
        onMoreStatsIncomeClick = onMoreStatsIncomeClick,
        onMoreStatsTopCategoryClick = onMoreStatsTopCategoryClick,
        onMoreStatsPaceClick = onMoreStatsPaceClick,
        onMoreStatsLoanClick = onMoreStatsLoanClick,
        onMoreStatsSubscriptionsClick = onMoreStatsSubscriptionsClick,
    )

    if (showOptionsSheet) {
        HeroPeriodOptionsSheet(
            useFinancialMonth = useFinancialMonth,
            availableCurrencies = availableCurrencies,
            isUnifiedMode = isUnifiedMode,
            currency = currency,
            onDismiss = { showOptionsSheet = false },
            onToggleSpendingMode = onToggleSpendingMode,
            onCurrencySelect = onCurrencySelect,
            onOpenPayPeriodSettings = onOpenPayPeriodSettings,
        )
    }
}
