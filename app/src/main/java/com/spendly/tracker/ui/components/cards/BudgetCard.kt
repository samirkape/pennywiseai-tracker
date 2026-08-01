package com.spendly.tracker.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.data.repository.BudgetGroupSpending
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pctUsed = groupSpending.percentageUsed
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 900),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        animatedProgress = (pctUsed / 100f).coerceIn(0f, 1f)
    }

    val statusColor: Color = when {
        pctUsed >= 90f -> MaterialTheme.colorScheme.error
        pctUsed >= 70f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val cardColors = if (isOverBudget) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }
    val onCardColor = if (isOverBudget) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val ringColor = if (isOverBudget) MaterialTheme.colorScheme.error else statusColor
    val trackColor = if (isOverBudget)
        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    SpendlyCardV2(
        modifier = modifier,
        onClick = onClick,
        colors = cardColors
    ) {
        // Bento layout: text content left, arc ring right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Left: text content
            Column(modifier = Modifier.weight(1f)) {
                // Budget name label
                Text(
                    text = groupSpending.group.budget.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = onCardColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Hero: remaining / over budget
                val remainingAbs = groupSpending.remaining.abs()
                Text(
                    text = if (isOverBudget) {
                        CurrencyFormatter.formatCurrency(remainingAbs, currency)
                    } else {
                        CurrencyFormatter.formatCurrency(
                            groupSpending.remaining.coerceAtLeast(BigDecimal.ZERO), currency
                        )
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isOverBudget) MaterialTheme.colorScheme.onErrorContainer else ringColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isOverBudget) "over budget" else "remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardColor.copy(alpha = 0.55f)
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Subtitle
                val subtitleText = when {
                    groupSpending.daysRemaining == 0 -> "Period ended"
                    isOverBudget -> "Over by ${CurrencyFormatter.formatCurrency(remainingAbs, currency)}"
                    else -> "${CurrencyFormatter.formatCurrency(groupSpending.dailyAllowance, currency)}/day · ${groupSpending.daysRemaining}d left"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = onCardColor.copy(alpha = Dimensions.Alpha.subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Spent ${CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)} of ${CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onCardColor.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: circular arc ring gauge
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 9.dp.toPx()
                    val startAngle = 135f
                    val sweepTotal = 270f

                    // Track (background arc)
                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    if (animatedProgressState > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = startAngle,
                            sweepAngle = sweepTotal * animatedProgressState.coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
                // Center: percentage text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${pctUsed.toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ringColor
                    )
                    Text(
                        text = "used",
                        style = MaterialTheme.typography.labelSmall,
                        color = onCardColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
