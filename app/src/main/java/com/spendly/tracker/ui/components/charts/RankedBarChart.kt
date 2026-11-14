package com.spendly.tracker.ui.components.charts

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

/** One ranked row (merchant, account, or any other "who took the money" dimension). */
@Immutable
data class RankedItem(
    val name: String,
    val value: BigDecimal,
    val count: Int,
    val caption: String? = null,
    val color: Color? = null,
)

/**
 * Ranked comparison where every row carries a proportional bar against the leader.
 *
 * Compact enough to scan five merchants without scrolling, with the exact amount,
 * transaction count and average kept next to the bar.
 */
@Composable
fun RankedBarChart(
    items: List<RankedItem>,
    currency: String,
    modifier: Modifier = Modifier,
    collapsedCount: Int = 4,
    onSelect: (RankedItem) -> Unit,
) {
    val palette = chartPalette()
    var expanded by remember { mutableStateOf(false) }

    if (items.isEmpty()) {
        ChartEmptyState(message = "Nothing to rank in this period", modifier = modifier)
        return
    }

    val sorted = remember(items) { items.sortedByDescending { it.value } }
    val max = remember(sorted) { sorted.first().value.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE }
    val visible = if (expanded) sorted else sorted.take(collapsedCount)

    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEachIndexed { index, item ->
            RankedRow(
                rank = index + 1,
                item = item,
                max = max,
                currency = currency,
                onClick = { onSelect(item) },
            )
            if (index != visible.lastIndex) {
                Spacer(Modifier.height(2.dp))
            }
        }

        if (sorted.size > collapsedCount) {
            val hidden = sorted.size - visible.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "Show less" else "$hidden more",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RankedRow(
    rank: Int,
    item: RankedItem,
    max: BigDecimal,
    currency: String,
    onClick: () -> Unit,
) {
    val palette = chartPalette()
    val fraction = if (max > BigDecimal.ZERO) {
        item.value.divide(max, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    } else 0f
    val average = if (item.count > 0) {
        item.value.divide(BigDecimal(item.count), 0, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO
    val barColor = item.color ?: palette.seriesColor(rank - 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.toString().padStart(2, '0'),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary.copy(alpha = 0.65f),
                modifier = Modifier.width(20.dp),
            )
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = CurrencyFormatter.formatCurrencyWhole(item.value, currency),
                style = ChartNumberStyle.copy(fontSize = 13.sp, color = palette.textPrimary),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(palette.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = buildString {
                    append(item.count)
                    append(if (item.count == 1) " txn" else " txns")
                    append(" · ")
                    append(CurrencyFormatter.formatCurrencyWhole(average, currency))
                    append(" avg")
                },
                fontSize = 10.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.caption?.let { caption ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = caption,
                fontSize = 10.sp,
                color = palette.textSecondary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
