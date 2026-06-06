package com.pennywiseai.tracker.ui.screens.analytics

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
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.presentation.common.PaymentMode
import com.pennywiseai.tracker.presentation.common.PaymentModeGroup
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.ui.components.PennyWiseStandardScaffold
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
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

    PennyWiseStandardScaffold(
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
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md,
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
                    onCardAndBankClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.EXPENSE.name,
                            paymentMode = PaymentModeGroup.CARD_AND_BANK.name,
                        )
                    },
                    onCashClick = {
                        drillDownToTransactions(
                            transactionType = TransactionTypeFilter.EXPENSE.name,
                            paymentMode = PaymentMode.CASH.name,
                        )
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
    onCardAndBankClick: () -> Unit,
    onCashClick: () -> Unit,
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
            AnalyticsMetricTile(
                content = AnalyticsMetricTileContent(
                    topLabel = if (showOutflowTile) "SPENDING" else "TOTAL",
                    primaryValue = CurrencyFormatter.formatCurrency(uiState.totalSpending, uiState.currency),
                    transactionCount = uiState.transactionCount,
                    countBadgeIcon = Icons.Default.Receipt,
                    bottomLeftLabel = "AVERAGE",
                    bottomLeftValue = CurrencyFormatter.formatCurrency(
                        if (uiState.transactionCount > 0) uiState.averageAmount else BigDecimal.ZERO,
                        uiState.currency,
                    ),
                    bottomLeftSuffix = " /day",
                    bottomRightCaption = if (uiState.topCategory != null && uiState.topCategoryPercentage > 0) {
                        "${uiState.topCategoryPercentage.toInt()}% of total"
                    } else {
                        null
                    },
                    bottomRightPill = uiState.topCategory?.takeIf {
                        uiState.topCategoryPercentage > 0
                    }?.let { AnalyticsTilePill.Category(it) },
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
        "card_and_bank" -> uiState.paymentModeBreakdown?.cardAndBank?.let { summary ->
            CardAndBankMetricTile(
                summary = summary,
                currency = uiState.currency,
                onClick = onCardAndBankClick,
            )
        }
        "payment_cash" -> uiState.paymentModeBreakdown?.cash?.let { cash ->
            val average = if (cash.transactionCount > 0) {
                cash.total.divide(
                    BigDecimal(cash.transactionCount),
                    2,
                    java.math.RoundingMode.HALF_UP,
                )
            } else {
                BigDecimal.ZERO
            }
            AnalyticsMetricTile(
                content = AnalyticsMetricTileContent(
                    topLabel = PaymentMode.CASH.label.uppercase(),
                    primaryValue = CurrencyFormatter.formatCurrency(cash.total, uiState.currency),
                    transactionCount = cash.transactionCount,
                    countBadgeIcon = Icons.Default.Payments,
                    bottomLeftLabel = "AVERAGE",
                    bottomLeftValue = CurrencyFormatter.formatCurrency(average, uiState.currency),
                    bottomLeftSuffix = " /txn",
                    bottomRightCaption = "${cash.percentOfTotal.toInt()}% of spend",
                    bottomRightPill = AnalyticsTilePill.Labeled(
                        text = PaymentMode.CASH.label,
                        icon = Icons.Default.Payments,
                    ),
                ),
                onClick = onCashClick,
            )
        }
    }
}

private fun breakdownTitle(tileKey: String): String = when (tileKey) {
    "outflow" -> "Outflow Breakdown"
    "spending" -> "Spending Breakdown"
    "investments" -> "Investment Breakdown"
    "card_and_bank" -> "Card & Bank Breakdown"
    "payment_cash" -> "Cash Breakdown"
    else -> "Breakdown"
}
