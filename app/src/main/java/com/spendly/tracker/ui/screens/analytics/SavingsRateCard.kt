package com.spendly.tracker.ui.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.spendly.tracker.R
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val CUSTOM_MIN_MONTHS = 2
private const val CUSTOM_MAX_MONTHS = 24
private const val SLIDER_AUTO_HIDE_MS = 3500L
private const val MAX_BARS_WITH_VALUE_LABELS = 8

/**
 * Savings rate (share of income kept) for the last N months.
 * Bars above the zero line are surplus months, bars below are overspent months.
 * Tap a bar to see that month; tap again to return to the window total.
 */
@Composable
fun SavingsRateCard(
    summary: SavingsRateSummary,
    onWindowSelected: (Int) -> Unit,
    /** Persisted month count for the Custom slider. */
    customMonths: Int,
    onCustomMonthsChanged: (Int) -> Unit,
    /** Date-picked window; an end month of null means "Now". */
    onRangeSelected: (start: YearMonth, end: YearMonth?) -> Unit,
    /** Forward-looking estimate for the next [projectionHorizon] months. */
    projection: SavingsProjection?,
    projectionHorizon: Int,
    onProjectionHorizonSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customMode by rememberSaveable { mutableStateOf(summary.windowMonths !in SAVINGS_RATE_WINDOW_OPTIONS) }
    var sliderVisible by rememberSaveable { mutableStateOf(false) }
    // Bumped on every slider/chip interaction so the auto-hide countdown restarts.
    var interactionTick by remember { mutableIntStateOf(0) }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingEdge by rememberSaveable { mutableStateOf<RangeEdge?>(null) }
    // Remembered separately from the presets so returning to Custom restores the last custom choice.
    var sliderValue by rememberSaveable {
        mutableStateOf(summary.windowMonths.toFloat().coerceIn(CUSTOM_MIN_MONTHS.toFloat(), CUSTOM_MAX_MONTHS.toFloat()))
    }

    // A new window invalidates any tapped bar.
    LaunchedEffect(summary.windowMonths) { selectedIndex = null }
    // A window restored from storage that isn't a preset belongs to Custom.
    LaunchedEffect(summary.windowMonths) {
        if (summary.windowMonths !in SAVINGS_RATE_WINDOW_OPTIONS) {
            customMode = true
        }
    }
    LaunchedEffect(customMonths) {
        sliderValue = customMonths.toFloat().coerceIn(CUSTOM_MIN_MONTHS.toFloat(), CUSTOM_MAX_MONTHS.toFloat())
    }
    // Keep the slider reachable while it is in use, then tuck it away.
    LaunchedEffect(sliderVisible, interactionTick, pickingEdge) {
        if (sliderVisible && pickingEdge == null) {
            delay(SLIDER_AUTO_HIDE_MS)
            sliderVisible = false
        }
    }

    SpendlyCardV2(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SAVINGS_RATE_WINDOW_OPTIONS.forEach { months ->
                    SavingsChip(
                        label = "${months}M",
                        selected = !customMode && summary.windowMonths == months,
                        onClick = {
                            customMode = false
                            sliderVisible = false
                            onWindowSelected(months)
                        },
                    )
                }
                SavingsChip(
                    label = if (customMode) {
                        stringResource(
                            R.string.savings_rate_custom_with_range,
                            customRangeLabel(summary),
                        )
                    } else {
                        stringResource(R.string.savings_rate_custom)
                    },
                    selected = customMode,
                    onClick = {
                        // Tapping an already-active Custom chip toggles the slider.
                        sliderVisible = !(customMode && sliderVisible)
                        interactionTick++
                        customMode = true
                        onWindowSelected(sliderValue.roundToInt())
                    },
                )
            }

            AnimatedVisibility(visible = customMode && sliderVisible) {
                Column {
                    Text(
                        text = stringResource(
                            R.string.savings_preview_last_months,
                            sliderValue.roundToInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            val previous = sliderValue.roundToInt()
                            sliderValue = it
                            interactionTick++
                            // Recalculate live as the slider crosses each month.
                            if (it.roundToInt() != previous) {
                                onWindowSelected(it.roundToInt())
                                onCustomMonthsChanged(it.roundToInt())
                            }
                        },
                        valueRange = CUSTOM_MIN_MONTHS.toFloat()..CUSTOM_MAX_MONTHS.toFloat(),
                        steps = CUSTOM_MAX_MONTHS - CUSTOM_MIN_MONTHS - 1,
                    )
                    val range = summary.customRange
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SavingsChip(
                            label = stringResource(
                                R.string.savings_rate_from,
                                range?.startMonth?.let(::formatMonthYear)
                                    ?: stringResource(R.string.savings_rate_pick_date),
                            ),
                            selected = range != null,
                            onClick = { pickingEdge = RangeEdge.START; interactionTick++ },
                        )
                        SavingsChip(
                            label = stringResource(
                                R.string.savings_rate_to,
                                range?.endMonth?.let(::formatMonthYear)
                                    ?: stringResource(R.string.savings_rate_now),
                            ),
                            selected = false,
                            onClick = { pickingEdge = RangeEdge.END; interactionTick++ },
                        )
                    }
                }
            }

            pickingEdge?.let { edge ->
                val current = summary.customRange
                val fallbackStart = YearMonth.now().minusMonths(summary.windowMonths.toLong() - 1)
                SavingsDatePickerDialog(
                    edge = edge,
                    initial = when (edge) {
                        RangeEdge.START -> (current?.startMonth ?: fallbackStart).atDay(1)
                        RangeEdge.END -> current?.endMonth?.atEndOfMonth()
                    },
                    onDismiss = { pickingEdge = null },
                    onNow = {
                        pickingEdge = null
                        customMode = true
                        onRangeSelected(current?.startMonth ?: fallbackStart, null)
                    },
                    onConfirm = { date ->
                        pickingEdge = null
                        customMode = true
                        val picked = YearMonth.from(date)
                        val now = YearMonth.now()
                        val start = current?.startMonth ?: fallbackStart
                        val end = current?.endMonth
                        when (edge) {
                            RangeEdge.START -> {
                                val newStart = picked.coerceAtMost(now.minusMonths(1))
                                onRangeSelected(newStart, end?.takeIf { it > newStart })
                            }
                            RangeEdge.END -> {
                                val newEnd = picked.coerceAtMost(now)
                                if (newEnd >= now) onRangeSelected(start, null)
                                else onRangeSelected(minOf(start, newEnd.minusMonths(1)), newEnd)
                            }
                        }
                    },
                )
            }

            if (!summary.hasData) {
                Text(
                    text = stringResource(R.string.savings_rate_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val month = selectedIndex?.let { summary.months.getOrNull(it) }
            SavingsHeadline(summary = summary, month = month)

            SavingsRateChart(
                months = summary.months,
                averagePercent = summary.averagePercent,
                selectedIndex = selectedIndex,
                onSelect = { index -> selectedIndex = if (selectedIndex == index) null else index },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val averageColor = MaterialTheme.colorScheme.tertiary
                Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
                    drawLine(
                        color = averageColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = size.height,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                    )
                }
                Text(
                    text = stringResource(R.string.savings_rate_overall),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (summary.months.any { it.isCurrent }) {
                            R.string.savings_rate_tap_bar_current
                        } else {
                            R.string.savings_rate_tap_bar
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            SavingsProjectionFooter(
                projection = projection,
                horizon = projectionHorizon,
                currency = summary.currency,
                onHorizonSelected = onProjectionHorizonSelected,
            )
        }
    }
}

/**
 * Compact forward-looking strip: estimated savings rate for the next [horizon] months,
 * with an independent horizon selector. Updates with every new transaction.
 */
@Composable
private fun SavingsProjectionFooter(
    projection: SavingsProjection?,
    horizon: Int,
    currency: String,
    onHorizonSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = projection?.range?.let { (start, end) ->
                    stringResource(
                        R.string.savings_rate_projected_range,
                        projectionRangeLabel(start, end),
                    )
                } ?: stringResource(R.string.savings_rate_projected),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SAVINGS_PROJECTION_HORIZON_OPTIONS.forEach { months ->
                    SavingsChip(
                        label = "${months}M",
                        selected = horizon == months,
                        onClick = { onHorizonSelected(months) },
                    )
                }
            }
        }

        if (projection == null || !projection.hasData) {
            Text(
                text = stringResource(R.string.savings_rate_projection_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val percent = projection.projectedPercent
        Text(
            text = when {
                percent == null -> stringResource(R.string.savings_rate_no_estimate)
                percent < 0f -> stringResource(
                    R.string.savings_preview_overspent,
                    abs(percent.roundToInt()),
                )
                else -> stringResource(
                    R.string.savings_preview_saved,
                    percent.roundToInt(),
                )
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = rateColor(percent),
        )
        val projectedSaved = CurrencyFormatter.formatCurrencyWhole(
            projection.projectedSaved.abs(),
            currency,
        )
        Text(
            text = when {
                projection.projectedSaved.signum() >= 0 && projection.basisMonths > 0 ->
                    stringResource(
                        R.string.savings_rate_projection_months,
                        projectedSaved,
                        projection.basisMonths,
                    )
                projection.projectedSaved.signum() >= 0 ->
                    stringResource(R.string.savings_rate_projection_current, projectedSaved)
                projection.basisMonths > 0 ->
                    stringResource(
                        R.string.savings_rate_projection_overspent_months,
                        projectedSaved,
                        projection.basisMonths,
                    )
                else ->
                    stringResource(
                        R.string.savings_rate_projection_overspent_current,
                        projectedSaved,
                    )
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Oct – Dec", or "Dec 26 – Feb 27" when the horizon crosses a year. */
private fun projectionRangeLabel(start: YearMonth, end: YearMonth): String {
    val pattern = if (start.year == end.year) "MMM" else "MMM yy"
    val fmt = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return "${start.format(fmt)} – ${end.format(fmt)}"
}

private enum class RangeEdge { START, END }

private fun formatMonthYear(month: YearMonth): String =
    "${month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())} ${month.year}"

/** Single-date picker for one edge of the range; the end edge can also snap back to "Now". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsDatePickerDialog(
    edge: RangeEdge,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onNow: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // Epoch-day millis keep the picked day stable across timezones.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toEpochDay()?.times(86_400_000L),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis / 86_400_000L <= LocalDate.now().toEpochDay()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onConfirm(LocalDate.ofEpochDay(it / 86_400_000L)) } },
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            Row {
                if (edge == RangeEdge.END) {
                    TextButton(onClick = onNow) {
                        Text(stringResource(R.string.savings_rate_now))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(
                        if (edge == RangeEdge.START) {
                            R.string.savings_rate_start_date
                        } else {
                            R.string.savings_rate_end_date
                        }
                    ),
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.sm,
                        top = Spacing.md,
                    ),
                )
            },
        )
    }
}

/** Same pill styling as the Analytics period chips. */
@Composable
private fun SavingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun SavingsHeadline(summary: SavingsRateSummary, month: MonthlySavingsRate?) {
    val percent = if (month != null) month.ratePercent else summary.averagePercent
    val income: BigDecimal
    val spent: BigDecimal
    if (month != null) {
        income = month.income
        spent = month.spent
    } else {
        income = summary.ratedMonths.fold(BigDecimal.ZERO) { acc, m -> acc + m.income }
        spent = summary.ratedMonths.fold(BigDecimal.ZERO) { acc, m -> acc + m.spent }
    }
    val saved = income - spent

    val caption = when {
        month != null ->
            month.month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) +
                " " + month.month.year
        summary.customRange != null -> stringResource(
            R.string.savings_rate_headline_custom,
            customRangeLabel(summary),
        )
        else -> stringResource(R.string.savings_rate_headline_overall, summary.windowMonths)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                percent == null -> stringResource(R.string.savings_history_no_income)
                percent < 0f -> stringResource(
                    R.string.savings_history_overspent,
                    abs(percent.roundToInt()),
                )
                else -> stringResource(
                    R.string.savings_history_rate,
                    percent.roundToInt(),
                )
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = rateColor(percent),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatCell(
            stringResource(R.string.savings_rate_stat_income),
            CurrencyFormatter.formatCurrencyWhole(income, summary.currency),
            Modifier.weight(1f),
        )
        StatCell(
            stringResource(R.string.savings_rate_stat_spent),
            CurrencyFormatter.formatCurrencyWhole(spent, summary.currency),
            Modifier.weight(1f),
        )
        StatCell(
            stringResource(
                if (saved.signum() >= 0) {
                    R.string.savings_rate_stat_saved
                } else {
                    R.string.savings_rate_stat_overspent
                }
            ),
            CurrencyFormatter.formatCurrencyWhole(saved.abs(), summary.currency),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SavingsRateChart(
    months: List<MonthlySavingsRate>,
    averagePercent: Float?,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val positiveColor = colors.primary
    val negativeColor = colors.error
    val gridColor = colors.onSurface.copy(alpha = 0.1f)
    val baselineColor = colors.onSurface.copy(alpha = 0.35f)
    val averageColor = colors.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface.copy(alpha = 0.7f))
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface.copy(alpha = 0.85f))

    val axis = remember(months) { rateAxis(months) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(months) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(500))
    }

    val yAxisWidthDp = 40.dp
    val xLabelHeightDp = 24.dp
    val topPadDp = 16.dp
    val bottomPadDp = 12.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(months.size) {
                detectTapGestures { offset ->
                    val left = yAxisWidthDp.toPx()
                    val width = size.width - left
                    if (offset.x < left || width <= 0f) return@detectTapGestures
                    val index = ((offset.x - left) / (width / months.size)).toInt()
                    if (index in months.indices) onSelect(index)
                }
            },
    ) {
        val left = yAxisWidthDp.toPx()
        val right = size.width
        val top = topPadDp.toPx()
        val bottom = size.height - xLabelHeightDp.toPx() - bottomPadDp.toPx()
        val chartHeight = bottom - top
        val chartWidth = right - left
        if (chartHeight <= 0f || chartWidth <= 0f) return@Canvas

        val span = axis.max - axis.min
        fun yFor(value: Float) = top + (axis.max - value) / span * chartHeight
        val zeroY = yFor(0f)

        // Grid, y labels and the stronger zero baseline.
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
        var tick = axis.min
        while (tick <= axis.max + 0.01f) {
            val y = yFor(tick)
            val isZero = tick == 0f
            drawLine(
                color = if (isZero) baselineColor else gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = if (isZero) 1.5f else 1f,
                pathEffect = if (isZero) null else dash,
            )
            val text = textMeasurer.measure("${tick.roundToInt()}%", labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y - text.size.height / 2f))
            tick += axis.step
        }

        val slot = chartWidth / months.size
        val barWidth = (slot * 0.55f).coerceAtMost(28.dp.toPx())
        val showValues = months.size <= MAX_BARS_WITH_VALUE_LABELS

        val sampleWidth = textMeasurer.measure(xLabel(months.first(), months.size), labelStyle).size.width
        val maxLabels = (chartWidth / (sampleWidth + 8.dp.toPx())).toInt().coerceAtLeast(2)
        val labelStep = if (months.size > maxLabels) ceil(months.size.toFloat() / maxLabels).toInt() else 1

        months.forEachIndexed { index, m ->
            val centerX = left + slot * index + slot / 2f
            val rate = m.ratePercent
            val dimmed = selectedIndex != null && selectedIndex != index

            if (rate != null) {
                val target = yFor(rate.coerceIn(axis.min, axis.max))
                val barEnd = zeroY + (target - zeroY) * progress.value
                val topY = minOf(zeroY, barEnd)
                val height = abs(barEnd - zeroY)
                if (height > 0.5f) {
                    drawRoundRect(
                        color = (if (rate >= 0f) positiveColor else negativeColor).copy(
                            alpha = when {
                                dimmed -> 0.3f
                                m.isCurrent && selectedIndex != index -> 0.55f
                                else -> 0.9f
                            },
                        ),
                        topLeft = Offset(centerX - barWidth / 2f, topY),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                }
                if (showValues && progress.value > 0.9f) {
                    val outside = textMeasurer.measure("${rate.roundToInt()}%", valueStyle)
                    val outsideY = if (rate >= 0f) target - outside.size.height - 3.dp.toPx() else target + 3.dp.toPx()
                    // Keep the label clear of the average line by tucking it inside the bar.
                    val hitsAverage = averagePercent != null && selectedIndex == null && rate >= 0f &&
                        yFor(averagePercent.coerceIn(axis.min, axis.max)).let { it > outsideY - 2.dp.toPx() && it < outsideY + outside.size.height + 2.dp.toPx() }
                    if (hitsAverage && height > outside.size.height + 8.dp.toPx()) {
                        val inside = textMeasurer.measure("${rate.roundToInt()}%", valueStyle.copy(color = colors.onPrimary))
                        drawText(inside, topLeft = Offset(centerX - inside.size.width / 2f, target + 4.dp.toPx()))
                    } else {
                        drawText(outside, topLeft = Offset(centerX - outside.size.width / 2f, outsideY))
                    }
                }
            } else {
                // No income that month: a small tick on the baseline.
                drawLine(
                    color = baselineColor,
                    start = Offset(centerX - 4.dp.toPx(), zeroY),
                    end = Offset(centerX + 4.dp.toPx(), zeroY),
                    strokeWidth = 3f,
                )
            }

            if (index % labelStep == 0 || index == months.lastIndex) {
                val text = textMeasurer.measure(xLabel(m, months.size), labelStyle)
                drawText(
                    text,
                    topLeft = Offset(centerX - text.size.width / 2f, size.height - xLabelHeightDp.toPx() + 4.dp.toPx()),
                )
            }
        }

        if (averagePercent != null && selectedIndex == null) {
            val avgY = yFor(averagePercent.coerceIn(axis.min, axis.max))
            drawLine(
                color = averageColor,
                start = Offset(left, avgY),
                end = Offset(right, avgY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f),
            )
        }
    }
}

private data class RateAxis(val min: Float, val max: Float, val step: Float)

/** Axis in whole-percent steps that always includes 0 and every bar. */
private fun rateAxis(months: List<MonthlySavingsRate>): RateAxis {
    val rates = months.mapNotNull { it.ratePercent }
    val high = (rates.maxOrNull() ?: 0f).coerceAtLeast(25f)
    val low = (rates.minOrNull() ?: 0f).coerceAtMost(0f)
    val range = high - low
    val step = when {
        range <= 100f -> 25f
        range <= 200f -> 50f
        else -> 100f
    }
    return RateAxis(
        min = floor(low / step) * step,
        max = ceil(high / step) * step,
        step = step,
    )
}

private fun xLabel(m: MonthlySavingsRate, total: Int): String {
    val name = m.month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
    val withYear = if (total > 12) "$name ${m.month.year % 100}" else name
    return if (m.isCurrent) "$withYear*" else withYear
}

@Composable
private fun rateColor(percent: Float?): Color = when {
    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
    percent < 0f -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

/** "Apr – Oct", or "Oct 24 – Mar 25" when the window spans years; falls back to the month count. */
private fun customRangeLabel(summary: SavingsRateSummary): String {
    val first = summary.months.minOfOrNull { it.month } ?: return "${summary.windowMonths}M"
    val last = summary.months.maxOf { it.month }
    val pattern = if (first.year == last.year) "MMM" else "MMM yy"
    val fmt = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return "${first.format(fmt)} – ${last.format(fmt)}"
}
