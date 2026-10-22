package com.spendly.tracker.ui.screens.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One donut segment + its legend row. */
data class BreakdownSlice(
    val label: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int,
    val color: Color,
)

/** Distinct colors for card-based breakdowns (cards have no category color). */
val spendingBreakdownPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFFEF9A3C), Color(0xFF42A5F5), Color(0xFF8D6E63), Color(0xFF90A4AE),
)

/** Category accent color, with a readable substitute for near-black category colors. */
fun categoryBreakdownColor(name: String): Color {
    val base = CategoryMapping.categories[name]?.color
        ?: spendingBreakdownPalette[(name.hashCode() and 0x7FFFFFFF) % spendingBreakdownPalette.size]
    return if (base.luminance() < 0.08f) Color(0xFF5E7A94) else base
}

/**
 * Category/card breakdown as a single card: a flat segmented donut with the total in the
 * middle, followed by legend rows that double as the breakdown list (no duplicated card).
 * Tapping a row selects that slice; tapping it again clears the selection.
 */
@Composable
fun SpendingBreakdownCard(
    slices: List<BreakdownSlice>,
    currency: String,
    modifier: Modifier = Modifier,
    selectedLabel: String? = null,
    onSliceClick: (BreakdownSlice) -> Unit = {},
    onClearSelection: () -> Unit = {},
) {
    val total = remember(slices) { slices.fold(BigDecimal.ZERO) { acc, s -> acc + s.amount } }
    val sorted = remember(slices) { slices.sortedByDescending { it.amount } }
    val selectedIndex = sorted.indexOfFirst { it.label == selectedLabel }.takeIf { it >= 0 }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) sorted else sorted.take(6)
    val hiddenCount = (sorted.size - visible.size).coerceAtLeast(0)

    // Reveal the selected row if it came from tapping a slice hidden past the first page.
    LaunchedEffect(selectedLabel) {
        if (!expanded && selectedIndex != null && selectedIndex >= visible.size) expanded = true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            DonutChart(
                slices = sorted,
                total = total,
                currency = currency,
                selectedIndex = selectedIndex,
                onSliceTap = { slice ->
                    if (slice == null) onClearSelection() else onSliceClick(slice)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            visible.forEachIndexed { index, slice ->
                BreakdownRow(
                    slice = slice,
                    currency = currency,
                    isSelected = index == selectedIndex,
                    onClick = { onSliceClick(slice) },
                )
                if (index != visible.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 28.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                    )
                }
            }

            if (hiddenCount > 0 || expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "Show less" else "$hiddenCount more",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    slices: List<BreakdownSlice>,
    total: BigDecimal,
    currency: String,
    selectedIndex: Int?,
    onSliceTap: (BreakdownSlice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(750)) }

    val totalValue = total.toFloat().takeIf { it > 0f } ?: 1f
    val selected = selectedIndex?.let { slices.getOrNull(it) }
    val strokeWidthDp = 24.dp
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(198.dp)
            .pointerInput(slices, totalValue) {
                detectTapGestures { offset ->
                    val stroke = with(density) { strokeWidthDp.toPx() }
                    val minSide = min(size.width, size.height).toFloat()
                    val outerRadius = minSide / 2f
                    val innerRadius = (minSide - 2f * stroke).coerceAtLeast(0f) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val distance = sqrt(dx * dx + dy * dy)

                    when {
                        distance < innerRadius -> onSliceTap(null)
                        distance > outerRadius -> Unit
                        else -> {
                            // Arc angles start at 12 o'clock and run clockwise.
                            val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val position = ((degrees + 90f) % 360f + 360f) % 360f
                            var cursor = 0f
                            var hit: BreakdownSlice? = null
                            for (slice in slices) {
                                val next = cursor + (slice.amount.toFloat() / totalValue) * 360f
                                if (position >= cursor && position < next) {
                                    hit = slice
                                    break
                                }
                                cursor = next
                            }
                            onSliceTap(hit)
                        }
                    }
                }
            }
            .semantics {
                contentDescription =
                    "Spending donut chart. Tap a slice for details, tap the center to clear."
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = strokeWidthDp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            val gap = if (slices.size > 1) 2.5f else 0f

            // Pre-compute offsets so draw order can put the selected slice last.
            val starts = FloatArray(slices.size)
            val sweeps = FloatArray(slices.size)
            var cursor = 0f
            slices.forEachIndexed { index, slice ->
                val fraction = slice.amount.toFloat() / totalValue
                starts[index] = -90f + 360f * progress.value * cursor
                sweeps[index] = 360f * progress.value * fraction
                cursor += fraction
            }

            val order = buildList {
                for (i in slices.indices) if (i != selectedIndex) add(i)
                selectedIndex?.let { add(it) }
            }

            order.forEach { index ->
                val isSelected = index == selectedIndex
                val dimmed = selectedIndex != null && !isSelected
                val color = if (dimmed) slices[index].color.copy(alpha = 0.22f) else slices[index].color
                val width = if (isSelected) strokeWidth + 4.dp.toPx() else strokeWidth
                val sweep = max(0f, sweeps[index] - gap)
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = starts[index] + gap / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = width, cap = StrokeCap.Butt),
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = selected?.label ?: "Total",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 34.dp),
            )
            Text(
                text = CurrencyFormatter.formatAbbreviated((selected?.amount ?: total).toDouble(), currency),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (selected != null) "${selected.percentage.toInt()}% of spend" else "this period",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    slice: BreakdownSlice,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(slice.color),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slice.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${slice.percentage.toInt()}% · ${slice.transactionCount} txn" +
                    if (slice.transactionCount != 1) "s" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = CurrencyFormatter.formatCurrency(slice.amount, currency),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
