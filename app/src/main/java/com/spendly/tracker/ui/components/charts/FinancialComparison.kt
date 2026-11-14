package com.spendly.tracker.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * One metric compared against the same metric in the reference period.
 *
 * [positiveIsGood] flips the judgement colour: spending more is bad, income more is good.
 */
@Immutable
data class ComparisonMetric(
    val label: String,
    val current: BigDecimal,
    val previous: BigDecimal?,
    val currency: String,
    val positiveIsGood: Boolean = true,
    val caption: String? = null,
)

/**
 * Period-over-period comparison.
 *
 * The bar shows the current value, the thin tick shows where the previous period ended, so
 * the reader sees direction and magnitude in the same glance without reading the numbers.
 */
@Composable
fun FinancialComparison(
    metrics: List<ComparisonMetric>,
    modifier: Modifier = Modifier,
    referenceLabel: String = "previous period",
) {
    val palette = chartPalette()
    if (metrics.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        metrics.forEachIndexed { index, metric ->
            ComparisonRow(metric = metric, referenceLabel = referenceLabel)
            if (index != metrics.lastIndex) {
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    metric: ComparisonMetric,
    referenceLabel: String,
) {
    val palette = chartPalette()
    val previous = metric.previous?.takeIf { it >= BigDecimal.ZERO }
    val deltaPercent: Float? = if (previous != null && previous > BigDecimal.ZERO) {
        metric.current.subtract(previous)
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toFloat()
    } else null

    val delta = deltaPercent?.let { value ->
        val rising = value > 0f
        MetricDelta(
            text = formatSignedPercent(value),
            tone = when {
                abs(value) < 0.05f -> ChartTone.NEUTRAL
                rising == metric.positiveIsGood -> ChartTone.POSITIVE
                else -> ChartTone.NEGATIVE
            },
            increasing = if (abs(value) < 0.05f) null else rising,
        )
    }

    val scale = listOfNotNull(metric.current, previous)
        .maxOrNull()
        ?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
    val currentFraction = metric.current.divide(scale, 4, RoundingMode.HALF_UP)
        .toFloat().coerceIn(0f, 1f)
    val previousFraction = previous?.divide(scale, 4, RoundingMode.HALF_UP)
        ?.toFloat()?.coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = metric.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = CurrencyFormatter.formatCurrencyWhole(metric.current, metric.currency),
                style = ChartNumberStyle.copy(fontSize = 14.sp, color = palette.textPrimary),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(palette.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentFraction)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (delta?.tone == ChartTone.NEGATIVE && metric.positiveIsGood) {
                                palette.accent.copy(alpha = 0.85f)
                            } else {
                                palette.accent
                            }
                        ),
                )
                if (previousFraction != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(previousFraction)
                            .height(8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .clip(CircleShape)
                                .background(palette.textPrimary.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
            if (delta != null) {
                Spacer(Modifier.width(10.dp))
                DeltaChip(delta)
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildString {
                if (previous != null && previous > BigDecimal.ZERO) {
                    append(CurrencyFormatter.formatCurrencyWhole(previous, metric.currency))
                    append(" ")
                    append(referenceLabel)
                } else {
                    append("No ")
                    append(referenceLabel)
                    append(" to compare")
                }
                metric.caption?.let {
                    append(" · ")
                    append(it)
                }
            },
            fontSize = 10.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatSignedPercent(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    val sign = if (rounded > 0f) "+" else if (rounded < 0f) "−" else ""
    return "$sign${abs(rounded)}%"
}
