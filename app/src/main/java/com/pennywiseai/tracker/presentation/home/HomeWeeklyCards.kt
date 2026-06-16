package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.spendGreen
import com.pennywiseai.tracker.ui.theme.spendGreenBg
import com.pennywiseai.tracker.ui.theme.spendRed
import com.pennywiseai.tracker.ui.theme.spendRedBg
import com.pennywiseai.tracker.ui.theme.textMuted
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
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

    PennyWiseCardV2(modifier = modifier, onClick = onClick) {
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
                style = MaterialTheme.typography.headlineMedium,
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
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
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

@Composable
fun HomeLast7DaysCard(
    last7DaysSpend: List<Pair<LocalDate, BigDecimal>>,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (last7DaysSpend.isEmpty()) return

    val amounts = last7DaysSpend.map { it.second.toFloat() }
    val avgAmount = if (amounts.isNotEmpty()) amounts.average().toFloat() else 0f
    val avgFormatted = CurrencyFormatter.formatCurrency(
        BigDecimal(avgAmount.toDouble().coerceAtLeast(0.0)), currency
    )

    val greenColor = MaterialTheme.colorScheme.spendGreen
    val redColor = MaterialTheme.colorScheme.spendRed
    val greenBgColor = MaterialTheme.colorScheme.spendGreenBg
    val redBgColor = MaterialTheme.colorScheme.spendRedBg
    val avgLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val mutedColor = MaterialTheme.colorScheme.textMuted
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface

    PennyWiseCardV2(modifier = modifier, onClick = onClick) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LegendDot(color = greenColor, label = "Below avg")
                LegendDot(color = redColor, label = "Above avg")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        SpendingBarChart(
            data = last7DaysSpend,
            currency = currency,
            avgAmount = avgAmount,
            greenColor = greenColor,
            redColor = redColor,
            greenBgColor = greenBgColor,
            redBgColor = redBgColor,
            avgLineColor = avgLineColor,
            mutedColor = mutedColor,
            tooltipBg = tooltipBg,
            tooltipFg = tooltipFg,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "7-day average: $avgFormatted/day",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawRoundRect(
                color = color,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color
        )
    }
}

@Composable
private fun SpendingBarChart(
    data: List<Pair<LocalDate, BigDecimal>>,
    currency: String,
    avgAmount: Float,
    greenColor: Color,
    redColor: Color,
    greenBgColor: Color,
    redBgColor: Color,
    avgLineColor: Color,
    mutedColor: Color,
    tooltipBg: Color,
    tooltipFg: Color,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val tooltipTexts = remember(data, currency) {
        data.map { (_, amount) -> CurrencyFormatter.formatCurrency(amount, currency) }
    }

    Column(modifier = modifier) {
        // Chart area: BoxWithConstraints so we can position the tooltip overlay using Dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val chartWidth = maxWidth
            val tooltipW = 72.dp
            val tooltipH = 22.dp
            val chartTopDp = tooltipH + 4.dp

            // Bars drawn on Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(data) {
                        detectTapGestures { offset ->
                            val slotW = size.width.toFloat() / data.size
                            val tapped = (offset.x / slotW).toInt().coerceIn(0, data.size - 1)
                            selectedIndex = if (selectedIndex == tapped) null else tapped
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val chartTopPx = chartTopDp.toPx()
                val chartH = h - chartTopPx

                val barCount = data.size
                val maxAmt = data.maxOfOrNull { it.second.toFloat() }?.coerceAtLeast(1f) ?: 1f
                val slotWidth = w / barCount
                val barWidth = slotWidth * 0.55f
                val barGap = (slotWidth - barWidth) / 2f
                val cornerR = CornerRadius(4.dp.toPx())

                // Dashed average line
                if (avgAmount > 0f) {
                    val avgY = chartTopPx + chartH * (1f - avgAmount / maxAmt)
                    drawLine(
                        color = avgLineColor,
                        start = Offset(0f, avgY),
                        end = Offset(w, avgY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                    )
                }

                data.forEachIndexed { i, (_, amount) ->
                    val amt = amount.toFloat()
                    val isAbove = amt > avgAmount
                    val barH = if (maxAmt > 0f) chartH * (amt / maxAmt) else 0f
                    val x = i * slotWidth + barGap
                    val barColor = if (isAbove) redColor else greenColor
                    val bgColor  = if (isAbove) redBgColor else greenBgColor
                    val isSelected = selectedIndex == i
                    val finalBg = if (isSelected)
                        bgColor.copy(alpha = (bgColor.alpha + 0.25f).coerceAtMost(1f))
                    else bgColor

                    drawRoundRect(
                        color = finalBg,
                        topLeft = Offset(x, chartTopPx),
                        size = Size(barWidth, chartH),
                        cornerRadius = cornerR
                    )
                    if (barH > 0.5f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, chartTopPx + chartH - barH),
                            size = Size(barWidth, barH),
                            cornerRadius = cornerR
                        )
                    }
                }
            }

            // Tooltip Compose overlay (avoids native canvas text)
            selectedIndex?.let { idx ->
                val slotWidthDp = chartWidth / data.size
                val barCenterX = slotWidthDp * idx + slotWidthDp / 2
                val tooltipX = (barCenterX - tooltipW / 2)
                    .coerceIn(0.dp, chartWidth - tooltipW)

                Box(
                    modifier = Modifier
                        .offset(x = tooltipX, y = 0.dp)
                        .size(width = tooltipW, height = tooltipH)
                        .background(color = tooltipBg, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tooltipTexts.getOrElse(idx) { "" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tooltipFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // X-axis day labels — bold + highlighted when selected
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, (date, _) ->
                Text(
                    text = date.dayOfMonth.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (selectedIndex == i) 11.sp else 10.sp,
                    fontWeight = if (selectedIndex == i) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedIndex == i) MaterialTheme.colorScheme.onSurface else mutedColor
                )
            }
        }
    }
}




