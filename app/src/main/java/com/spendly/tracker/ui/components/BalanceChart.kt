package com.spendly.tracker.ui.components

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.DividerProperties
import ir.ehsannarmani.compose_charts.models.DotProperties
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.IndicatorCount
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.LineProperties
import ir.ehsannarmani.compose_charts.models.PopupProperties
import ir.ehsannarmani.compose_charts.models.StrokeStyle
import ir.ehsannarmani.compose_charts.models.ZeroLineProperties
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private const val AXIS_INTERVALS = 4

data class BalancePoint(
    val timestamp: LocalDateTime,
    val balance: BigDecimal,
    val currency: String = "INR"
)

@Composable
fun BalanceChart(
    primaryCurrency: String,
    balanceHistory: List<BalancePoint>,
    modifier: Modifier = Modifier,
    height: Int = 200,
    smooth: Boolean = true
) {
    if (balanceHistory.isEmpty()) return

    val sortedHistory = remember(balanceHistory) {
        balanceHistory.sortedBy { it.timestamp }
    }

    val smoothedHistory = remember(sortedHistory, smooth) {
        if (smooth) smoothBalanceData(sortedHistory) else sortedHistory
    }

    val themeColors = MaterialTheme.colorScheme

    val chartValues = remember(smoothedHistory) {
        smoothedHistory.map { it.balance.toDouble() }
    }

    // Round the top of the axis up to 1/2/5 x 10^n so tick labels land on readable steps (4 even intervals).
    val niceMax = remember(chartValues) {
        val peak = chartValues.maxOrNull() ?: 0.0
        if (chartValues.any { it < 0.0 } || peak <= 0.0) {
            null
        } else {
            val step = peak / AXIS_INTERVALS
            val magnitude = Math.pow(10.0, Math.floor(Math.log10(step)))
            val normalized = step / magnitude
            val niceStep = when {
                normalized <= 1.0 -> 1.0
                normalized <= 2.0 -> 2.0
                normalized <= 2.5 -> 2.5
                normalized <= 5.0 -> 5.0
                else -> 10.0
            } * magnitude
            niceStep * AXIS_INTERVALS
        }
    }

    val labels = remember(smoothedHistory) {
        val isYearly = smoothedHistory.size > 1 && smoothedHistory.all { it.timestamp.dayOfYear == 1 }
        val isMonthly = !isYearly && smoothedHistory.all { it.timestamp.dayOfMonth == 1 }
        val spansMultipleYears = if (smoothedHistory.isNotEmpty()) {
            smoothedHistory.first().timestamp.year != smoothedHistory.last().timestamp.year
        } else false

        val allLabels = smoothedHistory.map {
            val date = it.timestamp
            when {
                isYearly -> date.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> date.format(DateTimeFormatter.ofPattern("MMM yy"))
                isMonthly -> date.format(DateTimeFormatter.ofPattern("MMM"))
                else -> date.format(DateTimeFormatter.ofPattern("dd MMM"))
            }
        }

        // Aim for ~6 horizontal labels; always keep the last point and drop a neighbour that would crowd it.
        val maxLabels = 6
        val skipFactor = if (allLabels.size > maxLabels) {
            (allLabels.size + maxLabels - 1) / maxLabels
        } else 1

        allLabels.mapIndexed { i, label ->
            val isLast = i == allLabels.lastIndex
            val crowdsLast = !isLast && allLabels.lastIndex - i < skipFactor / 2 + 1
            if (isLast || (i % skipFactor == 0 && !crowdsLast)) label else ""
        }
    }

    val popupProperties = remember(smoothedHistory, primaryCurrency, themeColors) {
        PopupProperties(
            enabled = true,
            duration = 2000L,
            textStyle = TextStyle.Default.copy(
                fontSize = 12.sp,
                color = themeColors.onSurface,
            ),
            containerColor = themeColors.surfaceContainerHighest,
            contentBuilder = { popup ->
                val point = smoothedHistory.getOrNull(popup.valueIndex)
                val formatted = CurrencyFormatter.formatAbbreviated(abs(popup.value), primaryCurrency)
                if (point != null) {
                    "${point.timestamp.format(DateTimeFormatter.ofPattern("dd MMM"))}  $formatted"
                } else {
                    formatted
                }
            }
        )
    }

    LineChart(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        popupProperties = popupProperties,
        minValue = if (niceMax != null) 0.0 else chartValues.minOrNull() ?: 0.0,
        maxValue = niceMax ?: chartValues.maxOrNull() ?: 0.0,
        data = listOf(
            Line(
                label = "Spend Trend",
                values = chartValues,
                color = SolidColor(themeColors.primary),
                firstGradientFillColor = themeColors.primary.copy(alpha = 0.3f),
                secondGradientFillColor = Color.Transparent,
                strokeAnimationSpec = tween(1500, easing = EaseInOutCubic),
                gradientAnimationDelay = 750,
                drawStyle = DrawStyle.Stroke(width = 2.dp),
                curvedEdges = true,
                dotProperties = DotProperties(
                    enabled = true,
                    color = SolidColor(themeColors.primary),
                    strokeWidth = 3.dp,
                    radius = 4.dp,
                    strokeColor = SolidColor(themeColors.surface)
                )
            )
        ),
        dividerProperties = DividerProperties(
            enabled = true,
            xAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            ),
            yAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            )
        ),
        indicatorProperties = HorizontalIndicatorProperties(
            enabled = true,
            count = IndicatorCount.CountBased(AXIS_INTERVALS + 1),
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurfaceVariant.copy(0.7f),
                textAlign = TextAlign.Center
            ),
            contentBuilder = { value ->
                CurrencyFormatter.formatAbbreviated(abs(value), primaryCurrency)
            }
        ),
        labelHelperProperties = LabelHelperProperties(
            enabled = false,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurface,
                textAlign = TextAlign.End
            ),
        ),
        labelProperties = LabelProperties(
            enabled = true,
            textStyle = TextStyle.Default.copy(
                fontSize = 10.sp,
                color = themeColors.onSurface.copy(0.7f),
                textAlign = TextAlign.Center
            ),
            labels = labels,
            padding = 16.dp,
            rotation = LabelProperties.Rotation(
                mode = LabelProperties.Rotation.Mode.IfNecessary,
                degree = -45f
            )
        ),
        zeroLineProperties = ZeroLineProperties(
            enabled = true,
            style = StrokeStyle.Dashed(),
            color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
        ),
        gridProperties = GridProperties(
            enabled = true,
            xAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            ),
            yAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            )
        ),
        animationMode = AnimationMode.Together(delayBuilder = { it * 200L }),
    )
}

/**
 * Smooth balance data to reduce noise in the chart
 * Applies time-based aggregation and moving average smoothing
 */
private fun smoothBalanceData(
    balanceHistory: List<BalancePoint>,
    maxPoints: Int = 50
): List<BalancePoint> {
    if (balanceHistory.size <= maxPoints) {
        return balanceHistory
    }

    val timeSpan = balanceHistory.last().timestamp.toLocalDate()
        .toEpochDay() - balanceHistory.first().timestamp.toLocalDate().toEpochDay()

    val intervalDays = maxOf(1, (timeSpan / maxPoints).toInt())

    val groupedData = balanceHistory.groupBy { point ->
        val dayIndex = (point.timestamp.toLocalDate().toEpochDay() -
            balanceHistory.first().timestamp.toLocalDate().toEpochDay()) / intervalDays
        dayIndex
    }

    return groupedData.map { (_, group) ->
        val avgBalance = group.map { it.balance }.reduce { acc, balance -> acc + balance } /
            BigDecimal(group.size.toLong())
        val middleIndex = group.size / 2
        val representativePoint = group[middleIndex]

        BalancePoint(
            timestamp = representativePoint.timestamp,
            balance = avgBalance,
            currency = representativePoint.currency
        )
    }.sortedBy { it.timestamp }
}
