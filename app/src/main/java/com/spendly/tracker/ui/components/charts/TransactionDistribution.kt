package com.spendly.tracker.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

/** One spending-size bucket (e.g. ₹500–₹1,000). */
@Immutable
data class DistributionBucket(
    val label: String,
    val amount: BigDecimal,
    val count: Int,
)

/**
 * Vertical column distribution for transaction sizes.
 *
 * Column height encodes the total amount, while the count sits underneath: together they
 * show whether the money came from many small payments or a few large ones.
 */
@Composable
fun TransactionDistribution(
    buckets: List<DistributionBucket>,
    currency: String,
    modifier: Modifier = Modifier,
    height: Int = 132,
    onSelect: ((Int) -> Unit)? = null,
) {
    val palette = chartPalette()
    if (buckets.isEmpty()) {
        ChartEmptyState(message = "No transactions to size", modifier = modifier)
        return
    }

    val max = remember(buckets) {
        buckets.maxOfOrNull { it.amount }?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
    }
    val totalCount = remember(buckets) { buckets.sumOf { it.count } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEachIndexed { index, bucket ->
                val fraction = if (max > BigDecimal.ZERO) {
                    bucket.amount.divide(max, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                } else 0f
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onSelect != null) Modifier.clickable { onSelect(index) } else Modifier
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = CurrencyFormatter.formatAbbreviated(bucket.amount.toDouble(), currency),
                        fontSize = 10.sp,
                        color = if (bucket.count == 0) palette.textSecondary.copy(alpha = 0.4f)
                        else palette.textPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val barFraction = if (fraction <= 0f) 0.02f else fraction
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(barFraction)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    if (bucket.count == 0) palette.track
                                    else palette.accent.copy(alpha = 0.55f + 0.45f * fraction)
                                ),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = bucket.label,
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${bucket.count} ${if (bucket.count == 1) "txn" else "txns"}",
                        fontSize = 9.sp,
                        color = palette.textSecondary.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }
        }

        if (totalCount > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$totalCount transactions across ${buckets.count { it.count > 0 }} size bands",
                fontSize = 10.sp,
                color = palette.textSecondary.copy(alpha = 0.8f),
            )
        }
    }
}
