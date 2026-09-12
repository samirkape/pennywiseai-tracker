package com.spendly.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * Generic tap-to-select bar chart with a dashed average line, used for any
 * "amount per labeled bucket" series (days of the week, months of the year, etc).
 */
@Composable
fun SpendingBarChart(
    data: List<Pair<String, BigDecimal>>,
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
                .height(88.dp)
        ) {
            val chartWidth = maxWidth
            val tooltipW = 84.dp
            val tooltipH = 26.dp
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
                        style = MaterialTheme.typography.labelMedium,
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

        // X-axis labels — bold + highlighted when selected
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, (label, _) ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (selectedIndex == i) 13.sp else 12.sp),
                    fontWeight = if (selectedIndex == i) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedIndex == i) MaterialTheme.colorScheme.onSurface else mutedColor
                )
            }
        }
    }
}
