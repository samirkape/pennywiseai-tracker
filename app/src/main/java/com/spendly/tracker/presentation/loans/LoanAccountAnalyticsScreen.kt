package com.spendly.tracker.presentation.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

private val sliceColors = listOf(
    Color(0xFF4F8EF7), Color(0xFFF2A93B), Color(0xFF3DBE8B), Color(0xFFE5646E),
    Color(0xFF9B6FE0), Color(0xFF3CBFD6), Color(0xFFE08A5A), Color(0xFF8A94A6)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanAccountAnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    viewModel: LoanAccountAnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "By account",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        val accounts = uiState.accounts
        val total = accounts.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = uiState.direction == LoanDirection.LENT,
                        onClick = { viewModel.setDirection(LoanDirection.LENT) },
                        label = { Text("Lent from") }
                    )
                    FilterChip(
                        selected = uiState.direction == LoanDirection.BORROWED,
                        onClick = { viewModel.setDirection(LoanDirection.BORROWED) },
                        label = { Text("Borrowed into") }
                    )
                    FilterChip(
                        selected = uiState.includeSettled,
                        onClick = viewModel::toggleIncludeSettled,
                        label = { Text("Settled") }
                    )
                }
            }
            if (accounts.isEmpty() || total.signum() <= 0) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isLoading) "Loading…" else "No transactions linked to accounts yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    AccountDonut(
                        accounts = accounts,
                        total = total,
                        currency = uiState.currency,
                        selectedLabel = uiState.selectedLabel,
                        onSelect = viewModel::toggleSelected
                    )
                }
                item {
                    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            accounts.forEachIndexed { index, account ->
                                AccountRow(
                                    account = account,
                                    total = total,
                                    currency = uiState.currency,
                                    color = sliceColors[index % sliceColors.size],
                                    expanded = uiState.selectedLabel == account.label,
                                    onClick = { viewModel.toggleSelected(account.label) },
                                    onTransactionClick = onNavigateToTransactionDetail
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDonut(
    accounts: List<AccountLoanAmount>,
    total: BigDecimal,
    currency: String,
    selectedLabel: String?,
    onSelect: (String) -> Unit
) {
    val pies = remember(accounts, selectedLabel) {
        accounts.mapIndexed { i, a ->
            val color = sliceColors[i % sliceColors.size]
            Pie(
                label = a.label,
                data = a.amount.toDouble(),
                color = if (selectedLabel == null || selectedLabel == a.label) color else color.copy(alpha = 0.35f),
                selectedColor = color,
                selected = selectedLabel == a.label,
                scaleAnimEnterSpec = tween(300),
                colorAnimEnterSpec = tween(300)
            )
        }
    }
    val selected = accounts.firstOrNull { it.label == selectedLabel }
    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            PieChart(
                modifier = Modifier.size(200.dp),
                data = pies,
                onPieClick = { pie -> pie.label?.let(onSelect) },
                selectedScale = 1.08f,
                scaleAnimEnterSpec = tween(300),
                colorAnimEnterSpec = tween(300),
                style = Pie.Style.Stroke(width = 36.dp)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 110.dp)
            ) {
                Text(
                    selected?.label ?: "Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
                Text(
                    CurrencyFormatter.formatCurrency(selected?.amount ?: total, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountLoanAmount,
    total: BigDecimal,
    currency: String,
    color: Color,
    expanded: Boolean,
    onClick: () -> Unit,
    onTransactionClick: (Long) -> Unit
) {
    val percent = account.amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP)
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(account.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${account.transactionCount} transaction${if (account.transactionCount == 1) "" else "s"} · $percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                CurrencyFormatter.formatCurrency(account.amount, currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 20.dp, top = Spacing.xs)) {
                account.transactions.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransactionClick(tx.id) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.merchantName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tx.dateTime.format(dateFmt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            CurrencyFormatter.formatCurrency(tx.amount, tx.currency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
