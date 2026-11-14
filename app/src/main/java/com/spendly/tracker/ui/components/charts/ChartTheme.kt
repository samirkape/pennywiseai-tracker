package com.spendly.tracker.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.utils.CurrencyFormatter

/**
 * Shared colour and type tokens for every analytics visualisation.
 *
 * The values are deliberately fixed instead of being pulled from the Material colour
 * scheme: charts need a stable, low-noise palette that stays legible regardless of the
 * user's accent choice, while still adapting between dark and light mode.
 */
@Immutable
data class ChartPalette(
    val accent: Color,
    val positive: Color,
    val negative: Color,
    val grid: Color,
    val track: Color,
    val card: Color,
    val cardElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val series: List<Color>,
) {
    fun seriesColor(index: Int): Color {
        if (series.isEmpty()) return accent
        val wrapped = ((index % series.size) + series.size) % series.size
        return series[wrapped]
    }

    fun colorFor(tone: ChartTone): Color = when (tone) {
        ChartTone.POSITIVE -> positive
        ChartTone.NEGATIVE -> negative
        ChartTone.NEUTRAL -> textSecondary
    }
}

/** Semantic direction of a change, independent of increase/decrease. */
enum class ChartTone { POSITIVE, NEGATIVE, NEUTRAL }

private val DarkChartPalette = ChartPalette(
    accent = Color(0xFF91AEFF),
    positive = Color(0xFF7ED3A3),
    negative = Color(0xFFE98884),
    grid = Color(0xFF26262B),
    track = Color(0xFF2A2A31),
    card = Color(0xFF171719),
    cardElevated = Color(0xFF202023),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1A8),
    series = listOf(
        Color(0xFF91AEFF),
        Color(0xFF7ED3A3),
        Color(0xFFE3B778),
        Color(0xFFC79BEA),
        Color(0xFF7CCFD4),
        Color(0xFFE39BB6),
        Color(0xFFA1A1A8),
    ),
)

private val LightChartPalette = ChartPalette(
    accent = Color(0xFF3F63C8),
    positive = Color(0xFF2C7A4B),
    negative = Color(0xFFB4473F),
    grid = Color(0xFFE6E6EC),
    track = Color(0xFFEDEDF2),
    card = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFF4F5F9),
    textPrimary = Color(0xFF16161A),
    textSecondary = Color(0xFF6B6B75),
    series = listOf(
        Color(0xFF3F63C8),
        Color(0xFF2C7A4B),
        Color(0xFF9A6B12),
        Color(0xFF7A4FA8),
        Color(0xFF1C7C86),
        Color(0xFFB2456C),
        Color(0xFF6B6B75),
    ),
)

@Composable
fun chartPalette(): ChartPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkChartPalette else LightChartPalette
}

/** Tabular figures keep digits from jittering as values animate between periods. */
val ChartNumberStyle: TextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/**
 * Every visualisation sits on the same quiet panel: one surface tone, one radius,
 * no borders. Grouping comes from spacing and typography rather than outlines.
 */
@Composable
fun ChartCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = chartPalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.cardElevated)
            .padding(contentPadding),
        content = content,
    )
}

/** Small uppercase label used above a metric or a section. */
@Composable
fun chartSectionLabelStyle(): TextStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.7.sp,
    color = chartPalette().textSecondary,
)

/**
 * Axis money label: short enough for a chart gutter ("₹20K") and never long decimals.
 */
fun formatAxisMoney(value: Double, currency: String): String {
    val symbol = CurrencyFormatter.getCurrencySymbol(currency)
    val magnitude = kotlin.math.abs(value)
    return when {
        magnitude >= 1_000_000 -> "$symbol${trimDecimal(magnitude / 1_000_000)}M"
        magnitude >= 1_000 -> "$symbol${trimDecimal(magnitude / 1_000)}K"
        else -> "$symbol${magnitude.toInt()}"
    }
}

private fun trimDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
fun chartNumberStyle(size: Int): TextStyle = ChartNumberStyle.copy(
    fontSize = size.sp,
    color = chartPalette().textPrimary,
)
