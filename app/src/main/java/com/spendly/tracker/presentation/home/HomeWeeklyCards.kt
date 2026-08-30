package com.spendly.tracker.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.components.SpendingBarChart
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.spendGreen
import com.spendly.tracker.ui.theme.spendGreenBg
import com.spendly.tracker.ui.theme.spendRed
import com.spendly.tracker.ui.theme.spendRedBg
import com.spendly.tracker.ui.theme.textMuted
import com.spendly.tracker.utils.CurrencyFormatter
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

    SpendlyCardV2(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "THIS WEEK",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.textMuted,
                letterSpacing = 0.66.sp
            )
            Text(
                text = weekLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
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
    val avgLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val mutedColor = MaterialTheme.colorScheme.textMuted
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
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
            data = last7DaysSpend.map { (date, amount) -> date.dayOfMonth.toString() to amount },
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
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
        )
    }
}

@Composable
fun HomeLast7DaysReferenceCard(
    last7DaysSpend: List<Pair<LocalDate, BigDecimal>>,
    currency: String,
    onClick: () -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (last7DaysSpend.isEmpty()) return

    val sorted = remember(last7DaysSpend) { last7DaysSpend.sortedBy { it.first }.takeLast(7) }
    val amounts = sorted.map { it.second }
    val avgAmount = remember(amounts) {
        if (amounts.isNotEmpty()) {
            amounts.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }
                .divide(BigDecimal(amounts.size), 2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }
    val avgFormatted = CurrencyFormatter.formatCurrency(avgAmount, currency)
    val maxAmount = amounts.maxOrNull() ?: BigDecimal.ZERO
    val maxItem = sorted.maxByOrNull { it.second }
    val minItem = sorted.minByOrNull { it.second }
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val greenColor = MaterialTheme.colorScheme.spendGreen
    val redColor = MaterialTheme.colorScheme.spendRed
    val dayFmt = remember { DateTimeFormatter.ofPattern("d") }
    val fullDateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
    val trackHeight = 64.dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last 7 days \u00B7 avg $avgFormatted/day",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                color = headerColor,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LegendDot(color = greenColor, label = "Below avg")
                LegendDot(color = redColor, label = "Above avg")
            }
        }
        SpendlyCardV2(
            onClick = onClick,
            contentPadding = Spacing.md
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight + 36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                sorted.forEach { (date, amount) ->
                    val isPeak = amount == maxAmount && maxAmount > BigDecimal.ZERO
                    val hasSpend = amount > BigDecimal.ZERO
                    val isAboveAvg = amount > avgAmount
                    val barHeightRatio = if (maxAmount > BigDecimal.ZERO) {
                        (amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val barHeight = (barHeightRatio * trackHeight.value).dp
                    val topText = amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                    val barColor = if (!hasSpend) headerColor.copy(alpha = 0.3f)
                                   else if (isAboveAvg) redColor else greenColor
                    val labelColor = if (isPeak) barColor else headerColor

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDayClick(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = topText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = labelColor,
                            fontWeight = if (isPeak) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(if (hasSpend) barHeight.coerceAtLeast(2.dp) else 2.dp)
                                .background(
                                    color = barColor.copy(alpha = if (isPeak) 1f else barColor.alpha.coerceAtMost(0.75f)),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                        Text(
                            text = date.format(dayFmt),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = labelColor,
                            fontWeight = if (isPeak) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            val highestText = maxItem?.let { "${it.first.format(fullDateFmt)} (${CurrencyFormatter.formatCurrency(it.second, currency)})" } ?: "\u2014"
            val lowestText = minItem?.let { "${it.first.format(fullDateFmt)} (${CurrencyFormatter.formatCurrency(it.second, currency)})" } ?: "\u2014"
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Highest: $highestText \u00B7 Lowest: $lowestText",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

