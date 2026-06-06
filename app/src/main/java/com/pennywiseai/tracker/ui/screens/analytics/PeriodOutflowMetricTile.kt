package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.util.LinkedHashSet

@Composable
fun PeriodOutflowMetricTile(
    outflow: PeriodOutflowSummary,
    currency: String,
    onClick: ((selectedTypes: Set<TransactionTypeFilter>) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isBreakdownExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedMetrics by rememberSaveable {
        mutableStateOf(linkedSetOf(OutflowMetricOption.SPENDING, OutflowMetricOption.INVESTED))
    }

    val metricRows = buildList {
        add(OutflowMetricRow(OutflowMetricOption.SPENDING, outflow.spending, outflow.spendingTransactionCount))
        add(OutflowMetricRow(OutflowMetricOption.INVESTED, outflow.invested, outflow.investmentTransactionCount))
        if (outflow.ccBillPayment > BigDecimal.ZERO) {
            add(OutflowMetricRow(OutflowMetricOption.CC_PAYMENT, outflow.ccBillPayment, outflow.ccBillPaymentTransactionCount))
        }
    }

    val selectedRows = metricRows.filter { selectedMetrics.contains(it.option) }
    val total = selectedRows.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
    val totalTransactionCount = selectedRows.sumOf { it.transactionCount }
    val totalLabel = if (selectedRows.size == 1) selectedRows.first().option.label.uppercase() else "TOTAL OUTFLOW"
    val formattedTotal = CurrencyFormatter.formatCurrency(total, currency)
    val isLongTotal = formattedTotal.length > 14

    PennyWiseCard(
        modifier = modifier,
        onClick = {
            val selectedTypes = selectedMetrics.map {
                when (it) {
                    OutflowMetricOption.SPENDING -> TransactionTypeFilter.EXPENSE
                    OutflowMetricOption.INVESTED -> TransactionTypeFilter.INVESTMENT
                    OutflowMetricOption.CC_PAYMENT -> TransactionTypeFilter.CC_BILL_PAYMENT
                }
            }.toSet()
            onClick?.invoke(selectedTypes)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$totalTransactionCount TXNS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formattedTotal,
                style = if (isLongTotal) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { isBreakdownExpanded = !isBreakdownExpanded },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChecklistRtl,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = if (isBreakdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Box {
                    TextButton(
                        onClick = { isMenuExpanded = !isMenuExpanded },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                    ) {
                        metricRows.forEach { row ->
                            DropdownMenuItem(
                                text = { Text(row.option.label) },
                                leadingIcon = {
                                    Checkbox(
                                        checked = selectedMetrics.contains(row.option),
                                        onCheckedChange = null,
                                    )
                                },
                                trailingIcon = {
                                    Icon(imageVector = row.option.icon, contentDescription = null)
                                },
                                onClick = {
                                    val next = LinkedHashSet(selectedMetrics)
                                    if (next.contains(row.option)) {
                                        if (next.size > 1) next.remove(row.option)
                                    } else {
                                        next.add(row.option)
                                    }
                                    selectedMetrics = next
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isBreakdownExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    selectedRows.forEachIndexed { index, row ->
                        OutflowBreakdownRow(row = row, currency = currency)
                        if (index != selectedRows.lastIndex) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutflowBreakdownRow(
    row: OutflowMetricRow,
    currency: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = row.option.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.transactionCount} txn${if (row.transactionCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                text = CurrencyFormatter.formatCurrency(row.amount, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class OutflowMetricOption(
    val label: String,
    val icon: ImageVector,
) {
    SPENDING("Spending", Icons.Default.Receipt),
    INVESTED("Invested", Icons.AutoMirrored.Filled.ShowChart),
    CC_PAYMENT("CC Payment", Icons.Default.CreditCard),
}

private data class OutflowMetricRow(
    val option: OutflowMetricOption,
    val amount: BigDecimal,
    val transactionCount: Int,
)
