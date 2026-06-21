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
 * Hero spend card with period context, amount, progress bar, and remaining headroom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeroPager(
    currency: String,
    currentMonthExpenses: BigDecimal,
    currentMonthIncome: BigDecimal,
    currentMonthTotal: BigDecimal,
    currentMonthInvestment: BigDecimal,
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
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    var showOptionsSheet by remember { mutableStateOf(false) }

    HeroSpendCard(
        modifier = modifier,
        blurEffects = blurEffects,
        hazeState = hazeState,
        currency = currency,
        currentMonthExpenses = currentMonthExpenses,
        currentMonthIncome = currentMonthIncome,
        currentMonthTotal = currentMonthTotal,
        currentMonthInvestment = currentMonthInvestment,
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
