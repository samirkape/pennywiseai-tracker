package com.spendly.tracker.ui.screens.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendly.tracker.R
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.screens.analytics.SavingsProjection
import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact savings-rate teaser on Analytics. Shows only the headline percentages and opens
 * the full Savings page, which owns the chart, window controls and goal planner.
 */
@Composable
fun SavingsRatePreviewCard(
    summary: SavingsRateSummary,
    projection: SavingsProjection?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpendlyCardV2(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.savings_preview_overall),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!summary.hasData) {
                    Text(
                        text = stringResource(R.string.savings_preview_no_income),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.savings_preview_add_income),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = summary.averagePercent.rateText(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = summary.averagePercent.rateColor(),
                    )
                    Text(
                        text = stringResource(
                            R.string.savings_preview_last_months,
                            summary.windowMonths,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (summary.hasData) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.savings_preview_projected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = projection?.projectedPercent?.rateText() ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = projection?.projectedPercent?.rateColor()
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.savings_preview_next_months,
                            projection?.horizonMonths ?: 3,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.small),
            )
        }
    }
}

@Composable
private fun Float?.rateText(): String = when {
    this == null -> "—"
    this < 0f -> stringResource(R.string.savings_preview_overspent, abs(this.roundToInt()))
    else -> stringResource(R.string.savings_preview_saved, this.roundToInt())
}

@Composable
private fun Float?.rateColor(): Color = when {
    this == null -> MaterialTheme.colorScheme.onSurfaceVariant
    this < 0f -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
