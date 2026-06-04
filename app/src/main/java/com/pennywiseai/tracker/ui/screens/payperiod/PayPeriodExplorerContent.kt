package com.pennywiseai.tracker.ui.screens.payperiod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * Shared body for period timeline: cumulative spend by day (matches Transactions / Analytics rules).
 * Used by the home "Spend so far" bottom sheet.
 */
@Composable
fun PayPeriodExplorerContent(
    periodStartEpochDay: Long,
    periodEndEpochDay: Long,
    modifier: Modifier = Modifier,
    showViewTransactionsButton: Boolean = false,
    onViewTransactions: () -> Unit = {},
    viewModel: PayPeriodExplorerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(periodStartEpochDay, periodEndEpochDay) {
        viewModel.start(periodStartEpochDay, periodEndEpochDay)
    }
    val dayFmt = rememberDayFormatter()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = uiState.periodRangeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.pay_period_explorer_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.pay_period_explorer_spent_through),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = CurrencyFormatter.formatCurrency(uiState.spentThroughSelected, uiState.currency),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = dayFmt.format(uiState.selectedDate),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        CumulativeSparkline(
            values = uiState.cumulativeSeries,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        )
        Text(
            text = stringResource(R.string.pay_period_explorer_chart_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            uiState.dayLabels.forEach { day ->
                val selected = day == uiState.selectedDate
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectDate(day) },
                    label = {
                        Text(
                            text = dayFmt.format(day),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        if (showViewTransactionsButton) {
            TextButton(
                onClick = onViewTransactions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pay_period_explorer_view_transactions))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

@Composable
internal fun rememberDayFormatter(): DateTimeFormatter {
    return remember {
        DateTimeFormatter.ofPattern("EEE d MMM")
    }
}

@Composable
internal fun CumulativeSparkline(
    values: List<BigDecimal>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    if (values.isEmpty()) {
        Spacer(modifier = modifier.height(1.dp))
    } else {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            drawLine(
                color = gridColor,
                start = Offset(0f, h * 0.85f),
                end = Offset(w, h * 0.85f),
                strokeWidth = 1.dp.toPx(),
            )
            val maxVal = values.maxOf { it }.coerceAtLeast(BigDecimal.ONE)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) w / 2f else index * w / (values.size - 1).coerceAtLeast(1)
                val y = h * 0.85f - (value.toFloat() / maxVal.toFloat()) * h * 0.75f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
