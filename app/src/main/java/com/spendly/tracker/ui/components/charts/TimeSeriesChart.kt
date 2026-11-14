package com.spendly.tracker.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import kotlin.math.pow
import kotlin.math.roundToInt

enum class TimeSeriesStyle { LINE, BAR }

/** One plotted bucket. [label] is the axis/tooltip caption, [value] the amount. */
@Immutable
data class TimePoint(
    val label: String,
    val value: Double,
    val tooltipLabel: String = label,
)

/**
 * One line (or column) of a multi-series chart.
 *
 * Every series must cover the same buckets so they can share one x axis; the chart picks
 * the longest series for the axis labels and aligns the others by index.
 */
@Immutable
data class TimeSeriesSeries(
    val name: String,
    val points: List<TimePoint>,
    val color: Color,
    val style: TimeSeriesStyle = TimeSeriesStyle.LINE,
    /** Draws the soft gradient underneath the line. Only the leading series should fill. */
    val filled: Boolean = false,
)

/**
 * Time-series chart shared by the analytics dashboard.
 *
 * Draws one or more series on a single axis, marks statistical outliers (so a large payment
 * is visible without rescaling the rest of the chart) and exposes a tap selection that
 * surfaces the exact amounts for that bucket.
 */
@Composable
fun TimeSeriesChart(
    series: List<TimeSeriesSeries>,
    currency: String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    onPointSelected: ((Int) -> Unit)? = null,
) {
    val palette = chartPalette()
    val textMeasurer = rememberTextMeasurer()
    var selected by remember(series) { mutableStateOf<Int?>(null) }

    val usable = series.filter { it.points.isNotEmpty() }
    if (usable.isEmpty()) {
        ChartEmptyState(message = "No activity in this period", modifier = modifier, height = height)
        return
    }

    // The series with the most buckets defines the axis; shorter ones align by index.
    val axisSeries = usable.maxByOrNull { it.points.size }!!
    val pointCount = axisSeries.points.size
    val maxValue = remember(series) {
        niceCeiling(series.flatMap { s -> s.points.map { it.value } }.maxOrNull() ?: 0.0)
    }
    val spikes = remember(series) {
        series.associate { s -> s.name to detectSpikes(s.points.map { it.value }) }
    }
    val peakIndex = remember(series) {
        axisSeries.points.indices.maxByOrNull { axisSeries.points[it].value }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(520))
    }
    // Reading the animation value keeps the canvas redrawing while it plays.
    val animatedProgress = progress.value

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            usable.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(entry.color)
                    )
                    Text(
                        text = entry.name,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val focus = selected ?: peakIndex
            if (focus != null) {
                Text(
                    text = buildString {
                        if (selected == null) append("Peak · ")
                        append(axisSeries.points.getOrNull(focus)?.tooltipLabel.orEmpty())
                    },
                    fontSize = 11.sp,
                    color = if (selected == null) palette.textSecondary else palette.textPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(series) {
                    detectTapGestures { offset ->
                        val left = yAxisWidth.toPx()
                        val right = size.width - rightPadding.toPx()
                        if (size.width <= left || offset.x < left || offset.x > right) {
                            selected = null
                            return@detectTapGestures
                        }
                        val count = axisSeries.points.size
                        val step = if (count <= 1) 1f else (right - left) / (count - 1)
                        val index = if (count <= 1) 0
                        else ((offset.x - left) / step).roundToInt().coerceIn(0, count - 1)
                        selected = index
                        onPointSelected?.invoke(index)
                    }
                },
        ) {
            drawSeries(
                series = usable,
                axisPoints = axisSeries.points,
                maxValue = maxValue,
                spikes = spikes,
                selected = selected,
                progress = animatedProgress,
                palette = palette,
                textMeasurer = textMeasurer,
                currency = currency,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            axisLabels(axisSeries.points).forEachIndexed { index, label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = palette.textSecondary.copy(alpha = 0.75f),
                    maxLines = 1,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        else -> TextAlign.End
                    },
                )
            }
        }

        // Exact figures for the selected bucket: the chart shows shape, this shows the money.
        val focusIndex = selected
        if (focusIndex != null && usable.any { it.points.getOrNull(focusIndex) != null }) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                usable.forEach { entry ->
                    val point = entry.points.getOrNull(focusIndex) ?: return@forEach
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(entry.color)
                        )
                        Text(
                            text = entry.name,
                            fontSize = 11.sp,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatAxisMoney(point.value, currency),
                            style = ChartNumberStyle.copy(fontSize = 12.sp, color = palette.textPrimary),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Single-series convenience wrapper around [TimeSeriesChart]. */
@Composable
fun TimeSeriesChart(
    points: List<TimePoint>,
    currency: String,
    modifier: Modifier = Modifier,
    seriesName: String = "Spending",
    style: TimeSeriesStyle = TimeSeriesStyle.LINE,
    height: Dp = 190.dp,
    onPointSelected: ((Int) -> Unit)? = null,
) {
    val palette = chartPalette()
    TimeSeriesChart(
        series = listOf(
            TimeSeriesSeries(
                name = seriesName,
                points = points,
                color = palette.accent,
                style = style,
                filled = style == TimeSeriesStyle.LINE,
            )
        ),
        currency = currency,
        modifier = modifier,
        height = height,
        onPointSelected = onPointSelected,
    )
}

private val yAxisWidth = 46.dp
private val rightPadding = 6.dp

private fun axisLabels(points: List<TimePoint>): List<String> = when {
    points.isEmpty() -> emptyList()
    points.size == 1 -> listOf(points.first().label)
    points.size == 2 -> listOf(points.first().label, points.last().label)
    else -> listOf(points.first().label, points[points.size / 2].label, points.last().label)
}

/**
 * Outliers are values far above the typical bucket, not merely the highest two.
 *
 * A mean/σ test fails exactly when it matters: one huge payment inflates the mean and the
 * deviation enough to hide itself. Comparing against the median keeps the test stable, so a
 * single large transaction is still marked while normal variation stays unmarked.
 */
private fun detectSpikes(values: List<Double>): Set<Int> {
    val positive = values.filter { it > 0.0 }
    if (positive.size < 4) return emptySet()

    val median = positive.sorted()[positive.size / 2]
    val threshold = if (median > 0.0) median * 3.0 else positive.average() * 3.0
    if (threshold <= 0.0) return emptySet()

    return values.indices
        .filter { values[it] > threshold }
        .sortedByDescending { values[it] }
        .take(2)
        .toSet()
}

/** Rounds a maximum up to 1, 2, 2.5 or 5 × 10ⁿ so axis labels stay short and legible. */
private fun niceCeiling(rawMax: Double): Double {
    val value = rawMax.coerceAtLeast(1.0)
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(value)))
    val normalized = value / magnitude
    val step = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}

private fun DrawScope.drawSeries(
    series: List<TimeSeriesSeries>,
    axisPoints: List<TimePoint>,
    maxValue: Double,
    spikes: Map<String, Set<Int>>,
    selected: Int?,
    progress: Float,
    palette: ChartPalette,
    textMeasurer: TextMeasurer,
    currency: String,
) {
    val left = yAxisWidth.toPx()
    val top = 10.dp.toPx()
    val bottom = size.height - 6.dp.toPx()
    val right = size.width - rightPadding.toPx()
    val plotWidth = (right - left).coerceAtLeast(1f)
    val plotHeight = (bottom - top).coerceAtLeast(1f)

    fun yFor(value: Double): Float =
        bottom - (value / maxValue).coerceIn(0.0, 1.0).toFloat() * plotHeight

    // ── Grid + y-axis ────────────────────────────────────────────────────────
    val labelStyle = TextStyle(fontSize = 9.sp, color = palette.textSecondary)
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
    val gridFractions = listOf(0f, 0.5f, 1f)
    gridFractions.forEach { fraction ->
        val y = bottom - fraction * plotHeight
        drawLine(
            color = palette.grid,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = 1f,
            pathEffect = dash,
        )
        val text = formatAxisMoney(maxValue * fraction, currency)
        val measured = textMeasurer.measure(text, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = (left - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                y = y - measured.size.height / 2f,
            ),
        )
    }

    val barSeries = series.filter { it.style == TimeSeriesStyle.BAR }
    val barCount = barSeries.size.coerceAtLeast(1)
    val slot = if (axisPoints.isEmpty()) plotWidth else plotWidth / axisPoints.size

    series.forEach { entry ->
        val count = entry.points.size
        if (count == 0) return@forEach

        if (entry.style == TimeSeriesStyle.BAR) {
            val groupWidth = slot * 0.62f
            val barWidth = (groupWidth / barCount).coerceAtMost(18.dp.toPx())
            val barIndex = barSeries.indexOf(entry)
            entry.points.forEachIndexed { index, point ->
                val barHeight = (point.value / maxValue).coerceIn(0.0, 1.0).toFloat() * plotHeight * progress
                if (barHeight <= 0f) return@forEachIndexed
                val groupStart = left + slot * index + (slot - groupWidth) / 2f
                val centerX = groupStart + groupWidth * (barIndex + 0.5f) / barCount
                drawRoundRect(
                    color = if (index == selected) entry.color else entry.color.copy(alpha = 0.72f),
                    topLeft = Offset(centerX - barWidth / 2f, bottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2.4f, barWidth / 2.4f),
                )
            }
        } else {
            val step = if (count <= 1) 0f else plotWidth / (count - 1)
            val offsets = entry.points.mapIndexed { index, point ->
                Offset(left + step * index, yFor(point.value))
            }
            if (offsets.size == 1) {
                drawCircle(color = entry.color, radius = 4.dp.toPx(), center = offsets.first())
            } else {
                val (linePath, fillPath) = buildPaths(offsets, bottom)
                clipRect(right = left + plotWidth * progress) {
                    if (entry.filled) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    entry.color.copy(alpha = 0.30f),
                                    entry.color.copy(alpha = 0.06f),
                                    Color.Transparent,
                                ),
                                startY = top,
                                endY = bottom,
                            ),
                        )
                    }
                    drawPath(
                        path = linePath,
                        color = entry.color,
                        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
                    )
                }
            }

            // Outlier markers: a hollow ring keeps them visible on top of the line.
            spikes[entry.name].orEmpty().forEach { index ->
                val center = offsets.getOrNull(index) ?: return@forEach
                drawCircle(color = entry.color.copy(alpha = 0.22f), radius = 6.dp.toPx(), center = center)
                drawCircle(color = entry.color, radius = 3.dp.toPx(), center = center)
            }
        }
    }

    // ── Selection guide ──────────────────────────────────────────────────────
    selected?.let { index ->
        val x = if (axisPoints.size > 1) {
            left + (plotWidth / (axisPoints.size - 1)) * index
        } else {
            left + slot / 2f
        }
        drawLine(
            color = palette.textSecondary.copy(alpha = 0.45f),
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = 1f,
        )
        series.forEach { entry ->
            val point = entry.points.getOrNull(index) ?: return@forEach
            val step = if (entry.points.size > 1) plotWidth / (entry.points.size - 1) else 0f
            val pointX = left + step * index
            val pointY = yFor(point.value)
            drawCircle(color = palette.cardElevated, radius = 5.dp.toPx(), center = Offset(pointX, pointY))
            drawCircle(color = entry.color, radius = 3.2f.dp.toPx(), center = Offset(pointX, pointY))
        }
    }
}

/** Monotone-ish cubic path plus its filled mirror, clamped to the plot area. */
private fun buildPaths(points: List<Offset>, baseline: Float): Pair<Path, Path> {
    val line = Path()
    val fill = Path()
    if (points.isEmpty()) return line to fill

    val tension = 0.18f
    val first = points.first()
    line.moveTo(first.x, first.y)
    fill.moveTo(first.x, baseline)
    fill.lineTo(first.x, first.y)

    for (i in 0 until points.lastIndex) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        val c1x = p1.x + (p2.x - p0.x) * tension
        val c1y = (p1.y + (p2.y - p0.y) * tension).coerceIn(0f, baseline)
        val c2x = p2.x - (p3.x - p1.x) * tension
        val c2y = (p2.y - (p3.y - p1.y) * tension).coerceIn(0f, baseline)

        line.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
        fill.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }

    val last = points.last()
    fill.lineTo(last.x, baseline)
    fill.close()
    return line to fill
}
