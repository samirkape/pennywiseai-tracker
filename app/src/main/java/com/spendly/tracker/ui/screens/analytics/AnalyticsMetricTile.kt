package com.spendly.tracker.ui.screens.analytics

// HTML reference: spending_invested_tiles.html  .hero-card "Invested" tab
// .card-label "INVESTED"
// .amount-row : amount + delta-badge
// .txn-count "6 transactions · via Groww"
// .divider
// .stat-row  2-col: [Largest single / ₹20k / 1 txn]  [Average per txn / ₹8,833 / across 6 txns]
// .invested-badge  [📈 View investment breakdown]

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.components.CategoryIcon
import com.spendly.tracker.ui.components.SpendlyCard
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.expense_dark
import com.spendly.tracker.ui.theme.expense_light
import com.spendly.tracker.ui.theme.income_dark
import com.spendly.tracker.ui.theme.income_light

sealed interface AnalyticsTilePill {
    data class Category(val name: String) : AnalyticsTilePill
    data class Labeled(val text: String, val icon: ImageVector? = null) : AnalyticsTilePill
}

data class AnalyticsMetricTileContent(
    val topLabel: String,
    val primaryValue: String,
    val transactionCount: Int,
    val countBadgeIcon: ImageVector = Icons.Default.Receipt,
    val bottomLeftLabel: String,
    val bottomLeftValue: String,
    val bottomLeftSuffix: String? = null,
    val bottomRightCaption: String? = null,
    val bottomRightPill: AnalyticsTilePill? = null,
    // delta for amount-row badge
    val deltaPercent: Float? = null,
    // optional subtitle after txn count (e.g. "· via Groww")
    val txnCountSuffix: String? = null,
)

@Composable
fun AnalyticsMetricTile(
    content: AnalyticsMetricTileContent,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            // .card-label
            Text(
                text = content.topLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // .amount-row : large amount + delta-badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val isLong = content.primaryValue.length > 14
                Text(
                    text = content.primaryValue,
                    style = if (isLong) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val delta = content.deltaPercent
                if (delta != null) {
                    val isUp = delta >= 0f
                    val isDark = isSystemInDarkTheme()
                    // for investments: higher = more invested = good (green); lower = bad (red)
                    val fg = if (isUp) {
                        if (isDark) income_dark else income_light
                    } else {
                        if (isDark) expense_dark else expense_light
                    }
                    val bg = fg.copy(alpha = 0.15f)
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = fg,
                        )
                        Text(
                            text = "${if (isUp) "+" else ""}${delta.toInt()}% vs last",
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // .txn-count  "6 transactions · via Groww"
            val txnLabel = buildString {
                append("${content.transactionCount} transaction${if (content.transactionCount != 1) "s" else ""}")
                content.txnCountSuffix?.let { append(" $it") }
            }
            Text(
                text = txnLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // .divider
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(14.dp))

            // .stat-row  2-col stat boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // left stat box (.stat-box)
                StatBox(
                    label = content.bottomLeftLabel,
                    value = content.bottomLeftValue,
                    sub = content.bottomLeftSuffix,
                    modifier = Modifier.weight(1f),
                )
                // right stat box — shown when bottomRightCaption + pill present
                content.bottomRightCaption?.let { caption ->
                    val pillText = when (val pill = content.bottomRightPill) {
                        is AnalyticsTilePill.Labeled -> pill.text
                        is AnalyticsTilePill.Category -> pill.name
                        null -> null
                    }
                    StatBox(
                        label = caption,
                        value = pillText ?: "",
                        sub = null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // .invested-badge  "View investment breakdown"
            if (onClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "View investment breakdown",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// .stat-box
@Composable
private fun StatBox(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sub?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
