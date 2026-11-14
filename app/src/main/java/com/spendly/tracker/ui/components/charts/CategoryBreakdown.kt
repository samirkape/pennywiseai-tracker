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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.components.currencyAnnotated
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/** Two ways of reading the same category totals. */
enum class CategoryView { BARS, TABLE }

/** One category row, already aggregated by the caller. [share] is 0f..1f of total spend. */
@Immutable
data class CategorySlice(
    val name: String,
    val amount: BigDecimal,
    val count: Int,
    val share: Float,
    val color: Color,
    val icon: ImageVector? = null,
)

/**
 * Category comparison built around horizontal bars.
 *
 * A thin proportion strip at the top answers "what is the mix?" in one glance, while the
 * bars below make exact comparisons easy. A table mode swaps the bars for dense numbers.
 */
@Composable
fun CategoryBreakdown(
    slices: List<CategorySlice>,
    currency: String,
    modifier: Modifier = Modifier,
    view: CategoryView = CategoryView.BARS,
    collapsedCount: Int = 5,
    onSelect: (CategorySlice) -> Unit,
) {
    val palette = chartPalette()
    var expanded by remember { mutableStateOf(false) }

    if (slices.isEmpty()) {
        ChartEmptyState(message = "No categories in this period", modifier = modifier)
        return
    }

    val sorted = remember(slices) { slices.sortedByDescending { it.amount } }
    val visible = if (expanded) sorted else sorted.take(collapsedCount)

    Column(modifier = modifier.fillMaxWidth()) {
        if (view == CategoryView.BARS) {
            ProportionStrip(slices = sorted)
            Spacer(Modifier.height(14.dp))
        } else {
            TableHeader()
            Spacer(Modifier.height(2.dp))
        }

        visible.forEach { slice ->
            when (view) {
                CategoryView.BARS -> CategoryBarRow(slice = slice, currency = currency, onClick = { onSelect(slice) })
                CategoryView.TABLE -> CategoryTableRow(slice = slice, currency = currency, onClick = { onSelect(slice) })
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
                    text = if (expanded) "Show less" else "$hidden more categories",
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

/** 100% stacked strip: category proportions without a donut's footprint. */
@Composable
private fun ProportionStrip(slices: List<CategorySlice>) {
    val palette = chartPalette()
    if (slices.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        slices.forEach { slice ->
            val weight = slice.share.coerceAtLeast(0.001f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(slice.color.copy(alpha = 0.9f)),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.take(3).forEach { slice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(slice.color),
                )
                Text(
                    text = "${(slice.share * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CategoryBarRow(
    slice: CategorySlice,
    currency: String,
    onClick: () -> Unit,
) {
    val palette = chartPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(slice.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                slice.icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = slice.color,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = slice.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = CurrencyFormatter.formatCurrencyWhole(slice.amount, currency),
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
                        .fillMaxWidth(slice.share.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(slice.color),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${percentOf(slice.share)}% · ${slice.count} ${if (slice.count == 1) "txn" else "txns"}",
                fontSize = 10.sp,
                color = palette.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TableHeader() {
    val palette = chartPalette()
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = "Category",
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Share",
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = "Amount",
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(92.dp),
        )
    }
}

@Composable
private fun CategoryTableRow(
    slice: CategorySlice,
    currency: String,
    onClick: () -> Unit,
) {
    val palette = chartPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(slice.color),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = slice.name,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${slice.count} ${if (slice.count == 1) "txn" else "txns"}",
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                )
            }
        }
        Text(
            text = "${percentOf(slice.share)}%",
            fontSize = 12.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = currencyAnnotated(
                CurrencyFormatter.formatCurrencyWhole(slice.amount, currency),
                13f,
            ),
            style = ChartNumberStyle.copy(fontSize = 13.sp, color = palette.textPrimary),
            textAlign = TextAlign.End,
            modifier = Modifier.width(92.dp),
            maxLines = 1,
        )
    }
}

private fun percentOf(share: Float): Int = kotlin.math.round(share * 100f).toInt()
