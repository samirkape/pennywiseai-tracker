package com.spendly.tracker.presentation.groups

import com.spendly.tracker.ui.theme.Dimensions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

private enum class GroupBreakdown(val label: String) { Category("Category"), Merchant("Merchant") }

private enum class GroupChartMode { Bars, Pie }

private data class BreakdownSlice(
    val key: String,
    val amount: BigDecimal,
    val fraction: Float,
    val color: Color,
    val transactions: List<TransactionEntity>
)

internal val merchantPalette = listOf(
    Color(0xFF4F8EF7), Color(0xFFF2994A), Color(0xFF27AE60), Color(0xFF9B51E0),
    Color(0xFFEB5757), Color(0xFF2D9CDB), Color(0xFFF2C94C), Color(0xFF00A99D)
)

@Composable
internal fun GroupAnalyticsCard(
    transactions: List<TransactionEntity>,
    currency: String,
    barColor: Color,
    onTransactionClick: (Long) -> Unit
) {
    val expenses = remember(transactions) { transactions.filter { it.isGroupExpense() } }
    val total = remember(expenses) { expenses.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount } }
    val average = remember(expenses, total) {
        if (expenses.isEmpty()) BigDecimal.ZERO
        else total.divide(BigDecimal(expenses.size), 2, RoundingMode.HALF_UP)
    }
    val largest = remember(expenses) { expenses.maxByOrNull { it.amount } }

    var breakdown by rememberSaveable { mutableStateOf(GroupBreakdown.Category) }
    var mode by rememberSaveable { mutableStateOf(GroupChartMode.Pie) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val slices = remember(expenses, total, breakdown) {
        buildSlices(expenses, total, breakdown, barColor)
    }
    // Drop a stale selection when the breakdown or data no longer contains it.
    val selected = slices.firstOrNull { it.key == selectedKey }

    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                AnalyticsStat(
                    label = "Average spend",
                    value = CurrencyFormatter.formatCurrency(average, currency),
                    modifier = Modifier.weight(1f)
                )
                AnalyticsStat(
                    label = "Largest",
                    value = largest?.let { CurrencyFormatter.formatCurrency(it.amount, currency) } ?: "-",
                    supporting = largest?.merchantName,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    GroupBreakdown.values().forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = breakdown == option,
                            onClick = { breakdown = option; selectedKey = null },
                            shape = SegmentedButtonDefaults.itemShape(index, GroupBreakdown.values().size)
                        ) { Text(option.label) }
                    }
                }
                FilledTonalIconButton(
                    onClick = {
                        mode = if (mode == GroupChartMode.Pie) GroupChartMode.Bars else GroupChartMode.Pie
                    }
                ) {
                    Icon(
                        imageVector = if (mode == GroupChartMode.Pie) Icons.Default.BarChart else Icons.Default.DonutLarge,
                        contentDescription = if (mode == GroupChartMode.Pie) "Show bars" else "Show pie chart"
                    )
                }
            }

            if (mode == GroupChartMode.Pie && slices.isNotEmpty()) {
                GroupPie(
                    slices = slices,
                    total = total,
                    currency = currency,
                    selected = selected,
                    onSelect = { key -> selectedKey = if (selectedKey == key) null else key }
                )
            }

            slices.forEach { slice ->
                BreakdownRow(
                    slice = slice,
                    currency = currency,
                    showBar = mode == GroupChartMode.Bars,
                    expanded = selected?.key == slice.key,
                    onToggle = { selectedKey = if (selectedKey == slice.key) null else slice.key },
                    onTransactionClick = onTransactionClick
                )
            }
        }
    }
}

private fun buildSlices(
    expenses: List<TransactionEntity>,
    total: BigDecimal,
    breakdown: GroupBreakdown,
    fallback: Color
): List<BreakdownSlice> {
    if (total.signum() <= 0) return emptyList()
    return expenses
        .groupBy { if (breakdown == GroupBreakdown.Category) it.category else it.merchantName }
        .map { (key, list) -> key to list }
        .sortedByDescending { (_, list) -> list.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount } }
        .mapIndexed { index, (key, list) ->
            val amount = list.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
            val color = when (breakdown) {
                GroupBreakdown.Category -> CategoryMapping.categories[key]?.color ?: fallback
                GroupBreakdown.Merchant -> merchantPalette[index % merchantPalette.size]
            }
            BreakdownSlice(
                key = key,
                amount = amount,
                fraction = amount.divide(total, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f),
                color = color,
                transactions = list.sortedByDescending { it.amount }
            )
        }
}

@Composable
private fun GroupPie(
    slices: List<BreakdownSlice>,
    total: BigDecimal,
    currency: String,
    selected: BreakdownSlice?,
    onSelect: (String) -> Unit
) {
    val pies = remember(slices, selected?.key) {
        slices.map { slice ->
            Pie(
                label = slice.key,
                data = slice.amount.toDouble(),
                color = slice.color,
                selectedColor = slice.color,
                selected = slice.key == selected?.key,
                scaleAnimEnterSpec = tween(Dimensions.Animation.standard),
                colorAnimEnterSpec = tween(Dimensions.Animation.standard)
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        PieChart(
            modifier = Modifier.size(180.dp),
            data = pies,
            onPieClick = { pie -> pie.label?.let(onSelect) },
            selectedScale = 1.08f,
            scaleAnimEnterSpec = tween(Dimensions.Animation.standard),
            colorAnimEnterSpec = tween(Dimensions.Animation.standard),
            style = Pie.Style.Stroke(width = 28.dp)
        )
        Column(
            modifier = Modifier.width(100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selected?.key ?: "Total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = CurrencyFormatter.formatCurrency(selected?.amount ?: total, currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            if (selected != null) {
                Text(
                    text = "${(selected.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = selected.color
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    slice: BreakdownSlice,
    currency: String,
    showBar: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTransactionClick: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = slice.key,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "${CurrencyFormatter.formatCurrency(slice.amount, currency)} · ${(slice.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Show transactions",
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showBar) {
            LinearProgressIndicator(
                progress = { slice.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                color = slice.color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                slice.transactions.forEachIndexed { index, txn ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransactionClick(txn.id) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = txn.merchantName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = txn.dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = CurrencyFormatter.formatCurrency(txn.amount, txn.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (index < slice.transactions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
