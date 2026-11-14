package com.spendly.tracker.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.components.currencyAnnotated
import com.spendly.tracker.ui.components.headlineMoneySize
import com.spendly.tracker.ui.components.inlineMoneySize

/** Visual weight of a metric tile. One hero per screen; the rest reads as supporting data. */
enum class MetricEmphasis { HERO, STANDARD, COMPACT }

/**
 * A change against a reference period.
 *
 * [tone] carries the judgement (good/bad/neutral) and is intentionally separate from
 * [increasing], because spending more is bad while earning more is good.
 */
@Immutable
data class MetricDelta(
    val text: String,
    val tone: ChartTone,
    val increasing: Boolean? = null,
    val caption: String? = null,
)

/**
 * Single financial figure with an optional comparison.
 *
 * Values are pre-formatted by the caller so the component stays free of currency rules;
 * the size ladder keeps long balances readable instead of clipping them.
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: MetricDelta? = null,
    caption: String? = null,
    emphasis: MetricEmphasis = MetricEmphasis.STANDARD,
    accent: Color = chartPalette().accent,
    fillHeight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = chartPalette()
    val shape = RoundedCornerShape(if (emphasis == MetricEmphasis.HERO) 22.dp else 16.dp)
    val background = if (emphasis == MetricEmphasis.HERO) {
        Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.18f), palette.cardElevated)
        )
    } else {
        Brush.verticalGradient(listOf(palette.cardElevated, palette.cardElevated))
    }
    val valueSize = when (emphasis) {
        MetricEmphasis.HERO -> headlineMoneySize(value.length) + 2f
        MetricEmphasis.STANDARD -> inlineMoneySize(value.length) + 5f
        MetricEmphasis.COMPACT -> inlineMoneySize(value.length) + 2f
    }
    val labelSize = if (emphasis == MetricEmphasis.COMPACT) 10 else 11

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .clip(shape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = if (emphasis == MetricEmphasis.COMPACT) 12.dp else 16.dp,
                vertical = if (emphasis == MetricEmphasis.HERO) 18.dp else 14.dp,
            ),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = labelSize.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = 0.7.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (emphasis == MetricEmphasis.HERO) 8.dp else 6.dp))
        Text(
            text = currencyAnnotated(value, valueSize),
            style = ChartNumberStyle.copy(
                fontSize = valueSize.sp,
                letterSpacing = if (emphasis == MetricEmphasis.HERO) (-0.6).sp else (-0.2).sp,
                color = palette.textPrimary,
            ),
            maxLines = 1,
        )
        if (delta != null) {
            Spacer(Modifier.height(8.dp))
            DeltaChip(delta)
        }
        if (caption != null) {
            Spacer(Modifier.height(if (delta != null) 6.dp else 8.dp))
            Text(
                text = caption,
                fontSize = if (emphasis == MetricEmphasis.COMPACT) 10.sp else 11.sp,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact trend badge: direction icon, value and the judgement colour. */
@Composable
fun DeltaChip(
    delta: MetricDelta,
    modifier: Modifier = Modifier,
) {
    val palette = chartPalette()
    val color = palette.colorFor(delta.tone)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = when (delta.increasing) {
                true -> Icons.AutoMirrored.Filled.TrendingUp
                false -> Icons.AutoMirrored.Filled.TrendingDown
                null -> Icons.Default.Remove
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = delta.text,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

/** Lays out supporting metrics on one row with equal weight. */
@Composable
fun MetricStrip(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/** Single row metric used inside a dense list (label left, value right). */
@Composable
fun MetricInlineRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tone: ChartTone? = null,
) {
    val palette = chartPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    fontSize = 11.sp,
                    color = palette.textSecondary.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = currencyAnnotated(value, inlineMoneySize(value.length) + 1),
            style = ChartNumberStyle.copy(
                fontSize = (inlineMoneySize(value.length) + 1).sp,
                color = tone?.let { palette.colorFor(it) } ?: palette.textPrimary,
            ),
            maxLines = 1,
        )
    }
}

/** Placeholder used by charts when a period has no data to plot. */
@Composable
fun ChartEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val palette = chartPalette()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = palette.textSecondary,
        )
    }
}
