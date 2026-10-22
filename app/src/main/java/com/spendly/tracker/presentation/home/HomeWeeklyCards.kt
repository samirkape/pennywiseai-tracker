package com.spendly.tracker.presentation.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.spendAmber
import com.spendly.tracker.ui.theme.spendGreen
import com.spendly.tracker.ui.theme.spendGreenBg
import com.spendly.tracker.ui.theme.spendRed
import com.spendly.tracker.ui.theme.spendRedBg
import com.spendly.tracker.ui.theme.textMuted
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeThisWeekCard(
    thisWeekSpend: BigDecimal,
    lastWeekSpend: BigDecimal,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val now = LocalDate.now()
    val weekStart = now.with(DayOfWeek.MONDAY)
    val weekEnd = weekStart.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    val weekLabel = if (weekStart.month == weekEnd.month) {
        "${weekStart.format(fmt)} – ${weekEnd.dayOfMonth}"
    } else {
        "${weekStart.format(fmt)} – ${weekEnd.format(fmt)}"
    }

    val delta = thisWeekSpend - lastWeekSpend
    val isHigher = delta > BigDecimal.ZERO

    SpendlyCardV2(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "THIS WEEK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                letterSpacing = 0.66.sp
            )
            Text(
                text = weekLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = CurrencyFormatter.formatCurrency(thisWeekSpend, currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (lastWeekSpend > BigDecimal.ZERO && delta.abs() > BigDecimal.ZERO) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isHigher) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isHigher) MaterialTheme.colorScheme.spendRed
                               else MaterialTheme.colorScheme.spendGreen
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(delta.abs(), currency)} ${if (isHigher) "more" else "less"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (isHigher) MaterialTheme.colorScheme.spendRed
                                else MaterialTheme.colorScheme.spendGreen
                    )
                }
            }
        }

        if (lastWeekSpend > BigDecimal.ZERO) {
            Text(
                text = "vs ${CurrencyFormatter.formatCurrency(lastWeekSpend, currency)} last week",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Last-7-days tile — glance-first variant.
 *
 * Leads with the period total and a delta chip against the previous 7 days, then
 * corroborates with a trackless bar chart: bar height alone carries magnitude, the
 * highest complete day is picked out in amber with a direct value label, the average
 * is drawn over the bars as a tagged line, and the in-progress day is outlined rather
 * than filled so it is never mistaken for a finished one.
 */
@Composable
fun HomeLast7DaysCard(
    last7DaysSpend: List<Pair<LocalDate, BigDecimal>>,
    previous7DaysTotal: BigDecimal,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (last7DaysSpend.isEmpty()) return

    val today = LocalDate.now()
    val todayIndex = last7DaysSpend.indexOfLast { it.first == today }

    // Average over complete days only — a partial today must not drag it down.
    val completeAmounts = last7DaysSpend
        .filter { it.first != today }
        .map { it.second.toFloat() }
    val avgAmount = if (completeAmounts.isNotEmpty())
        completeAmounts.average().toFloat()
    else
        last7DaysSpend.map { it.second.toFloat() }.average().toFloat()

    // Highlight the highest *finished* day; a partial today can never be "the spike".
    val maxIndex = last7DaysSpend.indices
        .filter { it != todayIndex }
        .maxByOrNull { last7DaysSpend[it].second.toFloat() } ?: -1

    val total = last7DaysSpend.fold(BigDecimal.ZERO) { acc, e -> acc + e.second }
    val delta = total - previous7DaysTotal
    val isHigher = delta > BigDecimal.ZERO
    val showDelta = previous7DaysTotal > BigDecimal.ZERO
    val percent = if (previous7DaysTotal > BigDecimal.ZERO) {
        delta.abs().multiply(BigDecimal(100))
            .divide(previous7DaysTotal, 0, RoundingMode.HALF_UP).toInt()
    } else null

    val neutralBarColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.spendAmber
    val onAccentColor = if (accentColor.luminance() > 0.5f) Color(0xFF1A1A18) else Color.White
    val mutedColor = MaterialTheme.colorScheme.textMuted
    val avgLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface

    SpendlyCardV2(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST 7 DAYS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                letterSpacing = 0.66.sp
            )
            if (showDelta) {
                DeltaChip(
                    delta = delta,
                    percent = percent,
                    isHigher = isHigher,
                    currency = currency
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = CurrencyFormatter.formatCurrencyWhole(total, currency),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (showDelta) {
            Text(
                text = "vs ${CurrencyFormatter.formatCurrencyWhole(previous7DaysTotal, currency)} previous 7 days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        SpendingBarChart(
            data = last7DaysSpend,
            currency = currency,
            avgAmount = avgAmount,
            maxIndex = maxIndex,
            neutralColor = neutralBarColor,
            accentColor = accentColor,
            onAccentColor = onAccentColor,
            mutedColor = mutedColor,
            avgLineColor = avgLineColor,
            tooltipBg = tooltipBg,
            tooltipFg = tooltipFg,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Pill showing the change vs the previous 7 days — direction, absolute and percent. */
@Composable
private fun DeltaChip(
    delta: BigDecimal,
    percent: Int?,
    isHigher: Boolean,
    currency: String
) {
    val fg = if (isHigher) MaterialTheme.colorScheme.spendRed
             else MaterialTheme.colorScheme.spendGreen
    val bg = if (isHigher) MaterialTheme.colorScheme.spendRedBg
             else MaterialTheme.colorScheme.spendGreenBg

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = if (isHigher) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = fg
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = buildString {
                append(CurrencyFormatter.formatCurrencyWhole(delta.abs(), currency))
                percent?.let { append(" · $it%") }
                append(if (isHigher) " more" else " less")
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

private fun weekdayInitial(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
}

@Composable
private fun SpendingBarChart(
    data: List<Pair<LocalDate, BigDecimal>>,
    currency: String,
    avgAmount: Float,
    maxIndex: Int,
    neutralColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    mutedColor: Color,
    avgLineColor: Color,
    tooltipBg: Color,
    tooltipFg: Color,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val today = remember(data) { LocalDate.now() }
    val todayIndex = remember(data) { data.indexOfLast { it.first == today } }

    val valueTexts = remember(data, currency) {
        data.map { (_, amount) -> CurrencyFormatter.formatCurrencyWhole(amount, currency) }
    }
    val avgText = remember(avgAmount, currency) {
        "avg " + CurrencyFormatter.formatCurrencyWhole(
            BigDecimal(avgAmount.toDouble().coerceAtLeast(0.0)), currency
        )
    }

    val a11ySummary = remember(data, avgAmount, currency) {
        buildString {
            append("Daily spending for the last 7 days: ")
            data.forEachIndexed { i, (date, amount) ->
                if (i > 0) append(", ")
                append(date.dayOfMonth)
                append(" ")
                append(CurrencyFormatter.formatCurrencyWhole(amount, currency))
            }
            append(". Average ")
            append(
                CurrencyFormatter.formatCurrencyWhole(
                    BigDecimal(avgAmount.toDouble().coerceAtLeast(0.0)), currency
                )
            )
            append(" per day.")
        }
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val slotW = size.width.toFloat() / data.size
                        val tapped = (offset.x / slotW).toInt().coerceIn(0, data.size - 1)
                        selectedIndex = if (selectedIndex == tapped) null else tapped
                    }
                }
                .semantics { contentDescription = a11ySummary }
        ) {
            val w = size.width
            val h = size.height
            val labelReserve = 18.dp.toPx()   // headroom for the value + avg pills
            val chartTop = labelReserve
            val chartH = h - chartTop

            val barCount = data.size
            val maxAmt = (data.maxOfOrNull { it.second.toFloat() } ?: 0f).coerceAtLeast(1f)
            val scaleMax = maxAmt * 1.12f
            val slotWidth = w / barCount
            val barWidth = slotWidth * 0.5f
            val barGap = (slotWidth - barWidth) / 2f
            val cornerR = CornerRadius(4.dp.toPx())

            // Faint weekend bands — spending patterns are weekday/weekend correlated.
            data.forEachIndexed { i, (date, _) ->
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                    drawRect(
                        color = neutralColor.copy(alpha = 0.04f),
                        topLeft = Offset(i * slotWidth, chartTop),
                        size = Size(slotWidth, chartH)
                    )
                }
            }

            // Bars — no background tracks; height alone carries magnitude.
            data.forEachIndexed { i, (_, amount) ->
                val amt = amount.toFloat()
                val x = i * slotWidth + barGap
                val barH = chartH * (amt / scaleMax)
                val yTop = chartTop + chartH - barH

                if (i == todayIndex) {
                    // In progress: faint fill + dashed outline, never solid.
                    drawRoundRect(
                        color = neutralColor.copy(alpha = 0.10f),
                        topLeft = Offset(x, yTop),
                        size = Size(barWidth, barH),
                        cornerRadius = cornerR
                    )
                    if (barH > 1.dp.toPx()) {
                        drawRoundRect(
                            color = neutralColor.copy(alpha = 0.55f),
                            topLeft = Offset(x, yTop),
                            size = Size(barWidth, barH),
                            cornerRadius = cornerR,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                            )
                        )
                    }
                } else {
                    val barColor = when {
                        i == maxIndex && amt > 0f -> accentColor
                        selectedIndex == i -> neutralColor.copy(alpha = 0.45f)
                        else -> neutralColor.copy(alpha = 0.22f)
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, yTop),
                        size = Size(barWidth, barH),
                        cornerRadius = cornerR
                    )
                }
            }

            // Average line drawn OVER the bars so it is never occluded.
            if (avgAmount > 0f) {
                val avgY = chartTop + chartH * (1f - avgAmount / scaleMax)
                drawLine(
                    color = avgLineColor,
                    start = Offset(0f, avgY),
                    end = Offset(w, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
                // Inline tag replaces the old legend + footnote.
                val tagLayout = textMeasurer.measure(
                    text = avgText,
                    style = TextStyle(
                        color = tooltipFg,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                val tagW = tagLayout.size.width + 12.dp.toPx()
                val tagH = tagLayout.size.height + 6.dp.toPx()
                val tagX = (w - tagW - 2.dp.toPx()).coerceAtLeast(0f)
                val tagY = (avgY - tagH - 3.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(
                    color = tooltipBg,
                    topLeft = Offset(tagX, tagY),
                    size = Size(tagW, tagH),
                    cornerRadius = CornerRadius(tagH / 2)
                )
                drawText(
                    textLayoutResult = tagLayout,
                    topLeft = Offset(tagX + 6.dp.toPx(), tagY + 3.dp.toPx())
                )
            }

            // Direct value label on the highest finished day.
            if (maxIndex in data.indices) {
                val maxAmtValue = data[maxIndex].second.toFloat()
                if (maxAmtValue > 0f && selectedIndex != maxIndex) {
                    val valLayout = textMeasurer.measure(
                        text = valueTexts[maxIndex],
                        style = TextStyle(
                            color = onAccentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val pw = valLayout.size.width + 14.dp.toPx()
                    val ph = valLayout.size.height + 8.dp.toPx()
                    val cx = maxIndex * slotWidth + slotWidth / 2
                    val barTop = chartTop + chartH * (1f - maxAmtValue / scaleMax)
                    val px = (cx - pw / 2).coerceIn(0f, w - pw)
                    val py = (barTop - ph - 4.dp.toPx()).coerceAtLeast(0f)
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(px, py),
                        size = Size(pw, ph),
                        cornerRadius = CornerRadius(ph / 2)
                    )
                    drawText(
                        textLayoutResult = valLayout,
                        topLeft = Offset(px + 7.dp.toPx(), py + 4.dp.toPx())
                    )
                }
            }

            // Tap-to-inspect tooltip.
            selectedIndex?.let { idx ->
                val tipLayout = textMeasurer.measure(
                    text = valueTexts.getOrElse(idx) { "" },
                    style = TextStyle(
                        color = tooltipFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                val tw = tipLayout.size.width + 14.dp.toPx()
                val th = tipLayout.size.height + 8.dp.toPx()
                val cx = idx * slotWidth + slotWidth / 2
                val tx = (cx - tw / 2).coerceIn(0f, w - tw)
                drawRoundRect(
                    color = tooltipBg,
                    topLeft = Offset(tx, 0f),
                    size = Size(tw, th),
                    cornerRadius = CornerRadius(th / 2)
                )
                drawText(
                    textLayoutResult = tipLayout,
                    topLeft = Offset(tx + 7.dp.toPx(), 4.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Weekday initial + date, weekend-tinted, today marked with a dot.
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, (date, _) ->
                val isToday = i == todayIndex
                val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY ||
                    date.dayOfWeek == DayOfWeek.SUNDAY
                val isSelected = selectedIndex == i

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekdayInitial(date.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = if (isWeekend) FontWeight.Medium else FontWeight.Normal,
                        color = if (isWeekend) MaterialTheme.colorScheme.onSurface else mutedColor
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isToday || isSelected) 10.5.sp else 9.5.sp
                        ),
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday || isSelected)
                            MaterialTheme.colorScheme.onSurface else mutedColor
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
