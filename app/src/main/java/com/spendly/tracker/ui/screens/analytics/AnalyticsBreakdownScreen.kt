package com.spendly.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.spendly.tracker.core.Constants
import com.spendly.tracker.presentation.common.PaymentMode
import com.spendly.tracker.presentation.common.PaymentModeGroup
import com.spendly.tracker.presentation.common.TimePeriod
import com.spendly.tracker.presentation.common.TransactionTypeFilter
import com.spendly.tracker.ui.components.SpendlyStandardScaffold
import com.spendly.tracker.ui.components.cards.SpendingBreakdownData
import com.spendly.tracker.ui.components.cards.SpendingBreakdownTile
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import com.spendly.tracker.utils.DateRangeUtils
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsBreakdownScreen(
    tileKey: String,
    navController: NavHostController,
    onNavigateBack: () -> Unit,
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
    ) -> Unit,
    onNavigateToCreditCardAnalytics: (startEpoch: Long, endEpoch: Long, currency: String) -> Unit = { _, _, _ -> },
) {
    val parentEntry = remember(navController) {
        navController.getBackStackEntry(Constants.Routes.ANALYTICS)
    }
    val viewModel: AnalyticsViewModel = hiltViewModel(parentEntry)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
    val drillDownPeriodEpochs = remember(activePeriodRange) {
        val range = activePeriodRange
        if (range == null) null to null else range.first.toEpochDay() to range.second.toEpochDay()
    }

    fun drillDownToTransactions(
        category: String? = categoryFilter,
        merchant: String? = null,
        transactionType: String? = null,
        paymentMode: String? = null,
        bankName: String? = null,
        accountLast4: String? = null,
    ) {
        onNavigateToTransactions(
            category,
            merchant,
            TimePeriod.CUSTOM.name,
            uiState.currency,
            transactionType,
            drillDownPeriodEpochs.first,
            drillDownPeriodEpochs.second,
            paymentMode,
            bankName,
            accountLast4,
        )
    }

    val accountTransactionType = remember(tileKey) {
        when (tileKey) {
            "investments" -> TransactionTypeFilter.INVESTMENT.name
            "spending" -> TransactionTypeFilter.EXPENSE.name
            else -> TransactionTypeFilter.ALL.name
        }
    }

    val accounts = uiState.accountBreakdowns[tileKey].orEmpty()

    SpendlyStandardScaffold(
        title = breakdownTitle(tileKey),
        onNavigateBack = onNavigateBack,
        scrollBehavior = scrollBehavior,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
        ) {
            periodRangeLabel?.let { label ->
                item {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                BreakdownMetricSection(
                    tileKey = tileKey,
                    uiState = uiState,
                    onOutflowClick = { selectedTypes ->
                        val typeName = if (selectedTypes.size == 1) {
                            selectedTypes.first().name
                        } else {
                            TransactionTypeFilter.ALL.name
                        }
                        drillDownToTransactions(
                            transactionType = typeName,
                        )
                    },
                    onOutflowBreakdownRowClick = { type ->
                        drillDownToTransactions(transactionType = type.name)
                    },
                    onSpendingClick = {
                        drillDownToTransactions(category = categoryFilter)
                    },
                    onInvestmentClick = {
                        drillDownToTransactions(transactionType = TransactionTypeFilter.INVESTMENT.name)
                    },
                    onSpendingBreakdownClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.EXPENSE.name,
                        )
                    },
                    onSpendingCardClick = {
                        val (start, end) = drillDownPeriodEpochs
                        if (start != null && end != null) {
                            onNavigateToCreditCardAnalytics(start, end, uiState.currency)
                        } else {
                            drillDownToTransactions(transactionType = TransactionTypeFilter.EXPENSE.name, paymentMode = "CREDIT_CARD")
                        }
                    },
                )
            }

            if (accounts.isNotEmpty()) {
                item {
                    AccountSpendTile(
                        accounts = accounts,
                        currency = uiState.currency,
                        compactMode = false,
                        onAccountClick = { bankName, accountLast4, _ ->
                            drillDownToTransactions(
                                transactionType = accountTransactionType,
                                bankName = bankName,
                                accountLast4 = accountLast4,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownMetricSection(
    tileKey: String,
    uiState: AnalyticsUiState,
    onOutflowClick: (Set<TransactionTypeFilter>) -> Unit,
    onOutflowBreakdownRowClick: (TransactionTypeFilter) -> Unit,
    onSpendingClick: () -> Unit,
    onInvestmentClick: () -> Unit,
    onSpendingBreakdownClick: () -> Unit,
    onSpendingCardClick: (() -> Unit)? = null,
) {
    when (tileKey) {
        "outflow" -> uiState.periodOutflow?.let { outflow ->
            PeriodOutflowMetricTile(
                outflow = outflow,
                currency = outflow.currency,
                onClick = onOutflowClick,
                compactMode = false,
                showInlineBreakdown = true,
                onBreakdownRowClick = onOutflowBreakdownRowClick,
            )
        }
        "spending" -> {
            val showOutflowTile = uiState.periodOutflow != null
            val transactionCount = uiState.transactions.size
            val averageAmount = if (transactionCount > 0) {
                uiState.totalExpense.divide(BigDecimal(transactionCount), 2, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
            val topCategory = uiState.categoryBreakdown.firstOrNull()
            AnalyticsMetricTile(
                content = AnalyticsMetricTileContent(
                    topLabel = if (showOutflowTile) "SPENDING" else "TOTAL",
                    primaryValue = CurrencyFormatter.formatCurrency(uiState.totalExpense, uiState.currency),
                    transactionCount = transactionCount,
                    countBadgeIcon = Icons.Default.Receipt,
                    bottomLeftLabel = "AVERAGE",
                    bottomLeftValue = CurrencyFormatter.formatCurrency(
                        averageAmount,
                        uiState.currency,
                    ),
                    bottomLeftSuffix = " /day",
                    bottomRightCaption = if (topCategory != null && topCategory.percentage > 0) {
                        "${topCategory.percentage.toInt()}% of total"
                    } else {
                        null
                    },
                    bottomRightPill = topCategory?.takeIf { it.percentage > 0 }
                        ?.let { AnalyticsTilePill.Category(it.name) },
                ),
                onClick = onSpendingClick,
            )
        }
        "investments" -> uiState.investmentInsights?.let { insights ->
            val delta = insights.deltaPercent
            val hasRecurring = insights.recurringCount > 0
            AnalyticsMetricTile(
                content = AnalyticsMetricTileContent(
                    topLabel = "INVESTED",
                    primaryValue = CurrencyFormatter.formatCurrency(insights.totalInvested, insights.currency),
                    transactionCount = insights.transactionCount,
                    countBadgeIcon = Icons.AutoMirrored.Filled.ShowChart,
                    bottomLeftLabel = if (hasRecurring) "RECURRING" else "LARGEST",
                    bottomLeftValue = if (hasRecurring) {
                        "${insights.recurringCount} SIP${if (insights.recurringCount != 1) "s" else ""}"
                    } else {
                        CurrencyFormatter.formatCurrency(insights.largestInvestment, insights.currency)
                    },
                    bottomRightCaption = when {
                        delta != null -> {
                            val sign = if (delta >= 0f) "+" else ""
                            "$sign${delta.toInt()}% vs last period"
                        }
                        insights.topCategory != null && insights.topCategoryPercentage > 0 ->
                            "${insights.topCategoryPercentage.toInt()}% of invested"
                        else -> null
                    },
                    bottomRightPill = insights.topCategory?.takeIf {
                        insights.topCategoryPercentage > 0
                    }?.let { AnalyticsTilePill.Category(it) },
                ),
                onClick = onInvestmentClick,
            )
        }
        "spending_breakdown" -> uiState.paymentModeBreakdown?.let { breakdown ->
            val cardAndBank = breakdown.cardAndBank
            val cash = breakdown.cash
            if (cardAndBank != null || cash != null) {
                val totalAmount = (cardAndBank?.total ?: BigDecimal.ZERO) + (cash?.total ?: BigDecimal.ZERO)
                val cardTxnCount = cardAndBank?.creditCount ?: 0
                val cashTxnCount = cash?.transactionCount ?: 0
                val creditCardAmount = CurrencyFormatter.formatCurrency(cardAndBank?.creditTotal ?: BigDecimal.ZERO, breakdown.currency)
                val creditCardTxns = cardAndBank?.creditCount ?: 0
                val bankAmount = CurrencyFormatter.formatCurrency(cardAndBank?.bankTotal ?: BigDecimal.ZERO, breakdown.currency)
                val bankTxns = cardAndBank?.bankCount ?: 0
                val cashAmount = CurrencyFormatter.formatCurrency(cash?.total ?: BigDecimal.ZERO, breakdown.currency)
                val cashTxns = cash?.transactionCount ?: 0
                val cashPercent = cash?.percentOfTotal ?: 0f
                val footerNote = if (cashPercent > 0f) {
                    "Cash is ${cashPercent.toInt()}% of spend · excl. investments"
                } else {
                    "Excl. investments"
                }
                
                SpendingBreakdownTile(
                    data = SpendingBreakdownData(
                        totalAmount = CurrencyFormatter.formatCurrency(totalAmount, breakdown.currency),
                        cardTxnCount = cardTxnCount,
                        cashTxnCount = cashTxnCount,
                        creditCardAmount = creditCardAmount,
                        creditCardTxns = creditCardTxns,
                        bankAmount = bankAmount,
                        bankTxns = bankTxns,
                        cashAmount = cashAmount,
                        cashTxns = cashTxns,
                        footerNote = footerNote,
                    ),
                    onClick = onSpendingBreakdownClick,
                    onCardClick = onSpendingCardClick,
                )
            }
        }
    }
}

private fun breakdownTitle(tileKey: String): String = when (tileKey) {
    "outflow" -> "Outflow Breakdown"
    "spending" -> "Spending Breakdown"
    "investments" -> "Investment Breakdown"
    "spending_breakdown" -> "Spending Breakdown"
    else -> "Breakdown"
}
