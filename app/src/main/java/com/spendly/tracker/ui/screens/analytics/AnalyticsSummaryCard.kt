package com.spendly.tracker.ui.screens.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun AnalyticsSummaryCard(
    totalAmount: BigDecimal,
    transactionCount: Int,
    averageAmount: BigDecimal,
    topCategory: String?,
    topCategoryPercentage: Float,
    currency: String,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val loadAlpha by animateFloatAsState(
        targetValue = if (visible && !isLoading) 1f else if (isLoading) 0.5f else 0f,
        animationSpec = tween(500),
        label = "summary_alpha",
    )

    AnalyticsMetricTile(
        content = AnalyticsMetricTileContent(
            topLabel = "TOTAL",
            primaryValue = CurrencyFormatter.formatCurrency(totalAmount, currency),
            transactionCount = transactionCount,
            countBadgeIcon = Icons.Default.Receipt,
            bottomLeftLabel = "AVERAGE",
            bottomLeftValue = CurrencyFormatter.formatCurrency(
                if (transactionCount > 0) averageAmount else BigDecimal.ZERO,
                currency,
            ),
            bottomLeftSuffix = " /day",
            bottomRightCaption = if (topCategory != null && topCategoryPercentage > 0) {
                "${topCategoryPercentage.toInt()}% of total"
            } else {
                null
            },
            bottomRightPill = topCategory?.takeIf { topCategoryPercentage > 0 }
                ?.let { AnalyticsTilePill.Category(it) },
        ),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().alpha(loadAlpha),
    )
}
