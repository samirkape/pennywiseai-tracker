package com.spendly.tracker.presentation.merchant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.SpendingBarChart
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.spendGreen
import com.spendly.tracker.ui.theme.spendGreenBg
import com.spendly.tracker.ui.theme.spendRed
import com.spendly.tracker.ui.theme.spendRedBg
import com.spendly.tracker.ui.theme.textMuted
import com.spendly.tracker.utils.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantDetailScreen(
    merchant: String,
    onNavigateBack: () -> Unit,
    onViewAllTransactions: (String) -> Unit,
    viewModel: MerchantDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(merchant) {
        viewModel.load(merchant)
    }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = merchant,
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val state = uiState
        if (state !is MerchantDetailUiState.Loaded) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = padding.calculateTopPadding() + Spacing.md,
                bottom = padding.calculateBottomPadding() + Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                MerchantHeaderCard(state = state)
            }
            item {
                PeriodToggleRow(
                    selected = state.period,
                    onSelect = viewModel::selectPeriod
                )
            }
            item {
                MerchantStatsRow(state = state)
            }
            if (state.chartData.isNotEmpty()) {
                item {
                    MerchantTrendChartCard(state = state)
                }
            }
            state.anomalyMultiplier?.let { multiplier ->
                item {
                    AnomalyCallout(multiplier = multiplier)
                }
            }
            item {
                Button(
                    onClick = { onViewAllTransactions(state.merchantName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View all transactions")
                }
            }
        }
    }
}

@Composable
private fun MerchantHeaderCard(state: MerchantDetailUiState.Loaded) {
    SpendlyCardV2 {
        Text(
            text = state.merchantName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "All-time spend: ${CurrencyFormatter.formatCurrency(state.allTimeTotal, state.currency)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.textMuted
        )
        state.memberSinceLabel?.let {
            Text(
                text = "Member since $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }
    }
}

@Composable
private fun PeriodToggleRow(
    selected: MerchantDetailPeriod,
    onSelect: (MerchantDetailPeriod) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val options = remember {
            listOf(
                MerchantDetailPeriod.MONTH to "Month",
                MerchantDetailPeriod.YEAR to "Year",
                MerchantDetailPeriod.ALL to "All time",
            )
        }
        options.forEach { (period, label) ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun MerchantStatsRow(state: MerchantDetailUiState.Loaded) {
    SpendlyCardV2 {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatColumn(
                label = "Total spent",
                value = CurrencyFormatter.formatCurrency(state.periodTotal, state.currency)
            )
            StatColumn(
                label = "Transactions",
                value = state.periodCount.toString()
            )
            StatColumn(
                label = "Avg / txn",
                value = CurrencyFormatter.formatCurrency(state.periodAvg, state.currency)
            )
        }
        state.momTrendPct?.let { pct ->
            Spacer(modifier = Modifier.height(Spacing.sm))
            val isUp = pct > 0
            Text(
                text = "${if (isUp) "▲" else "▼"} ${kotlin.math.abs(pct)}% vs last month",
                style = MaterialTheme.typography.labelMedium,
                color = if (isUp) MaterialTheme.colorScheme.spendRed else MaterialTheme.colorScheme.spendGreen
            )
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MerchantTrendChartCard(state: MerchantDetailUiState.Loaded) {
    SpendlyCardV2 {
        Text(
            text = "Monthly trend",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.textMuted
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        val amounts = state.chartData.map { it.second.toFloat() }
        val avgAmount = if (amounts.isNotEmpty()) amounts.average().toFloat() else 0f

        SpendingBarChart(
            data = state.chartData,
            currency = state.currency,
            avgAmount = avgAmount,
            greenColor = MaterialTheme.colorScheme.spendGreen,
            redColor = MaterialTheme.colorScheme.spendRed,
            greenBgColor = MaterialTheme.colorScheme.spendGreenBg,
            redBgColor = MaterialTheme.colorScheme.spendRedBg,
            avgLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            mutedColor = MaterialTheme.colorScheme.textMuted,
            tooltipBg = MaterialTheme.colorScheme.inverseSurface,
            tooltipFg = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AnomalyCallout(multiplier: java.math.BigDecimal) {
    SpendlyCardV2(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.spendRedBg
        )
    ) {
        Text(
            text = "This month is ${multiplier}x your recent average",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.spendRed
        )
    }
}
