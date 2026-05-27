package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.expense_light
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.income_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Hero spend: period context, amount, trend sparkline, and remaining headroom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroSpendCard(
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentMonthExpenses: BigDecimal,
    currentMonthIncome: BigDecimal,
    currentMonthTotal: BigDecimal,
    spendingHistory: List<BigDecimal>,
    lastMonthSpendingHistory: List<BigDecimal>,
    periodDayLabel: String,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    spendingPeriodLabel: String,
    useFinancialMonth: Boolean,
    onToggleSpendingMode: () -> Unit,
    onCurrencySelect: (String) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onShowBreakdown: () -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)? = null,
    onPeriodChipClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    var showOptionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val useExternalPeriodSheet = onPeriodChipClick != null

    val spendingIncreased = monthlyChange >= BigDecimal.ZERO
    val deltaColor = if (spendingIncreased) {
        if (isDark) expense_dark else expense_light
    } else {
        if (isDark) income_dark else income_light
    }
    val absPercent = kotlin.math.abs(monthlyChangePercent)
    val arrow = if (spendingIncreased) "↑" else "↓"
    val deltaText = "$arrow $absPercent% vs last"

    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val periodChipText = when {
        spendingPeriodLabel.isNotEmpty() -> spendingPeriodLabel
        else -> stringResource(R.string.period_type_calendar)
    }

    val incomeForProgress = currentMonthIncome.coerceAtLeast(BigDecimal.ZERO)
    val spendFraction = if (incomeForProgress > BigDecimal.ZERO) {
        currentMonthExpenses
            .divide(incomeForProgress, 4, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val percentOfIncome = if (incomeForProgress > BigDecimal.ZERO) {
        currentMonthExpenses
            .multiply(BigDecimal(100))
            .divide(incomeForProgress, 0, RoundingMode.HALF_UP)
            .toInt()
    } else {
        0
    }
    val remainingFormatted = CurrencyFormatter.formatCurrency(
        currentMonthTotal.coerceAtLeast(BigDecimal.ZERO),
        currency,
    )

    PennyWiseCardV2(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEffects) {
                    Modifier
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                        .hazeEffect(
                            state = hazeState,
                            block = fun HazeEffectScope.() {
                                style = HazeDefaults.style(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(containerColor),
                                    blurRadius = 20.dp,
                                    noiseFactor = -1f,
                                )
                                blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                            },
                        )
                } else {
                    Modifier
                },
            ),
        onClick = null,
        colors = CardDefaults.cardColors(
            containerColor = if (blurEffects) containerColor.copy(alpha = 0.5f) else containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (useExternalPeriodSheet) {
                            onPeriodChipClick?.invoke()
                        } else {
                            showOptionsSheet = true
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = periodChipText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (periodDayLabel.isNotEmpty()) {
                    Text(
                        text = periodDayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onNavigateToTransactions()
                        },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onShowBreakdown()
                        },
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_spent_so_far),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedCurrencyText(
                        text = CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        brush = null,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    HeroSparkline(
                        history = spendingHistory,
                        lastHistory = lastMonthSpendingHistory,
                        spendingIncreased = spendingIncreased,
                        modifier = Modifier
                            .width(110.dp)
                            .height(32.dp),
                    )
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = deltaColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onNavigateToBudgets()
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SpendProgressBar(
                        progress = spendFraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_percent_of_income, percentOfIncome),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_remaining, remainingFormatted),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOptionsSheet && !useExternalPeriodSheet) {
        HeroPeriodOptionsSheet(
            useFinancialMonth = useFinancialMonth,
            availableCurrencies = availableCurrencies,
            isUnifiedMode = isUnifiedMode,
            currency = currency,
            onDismiss = { showOptionsSheet = false },
            onToggleSpendingMode = onToggleSpendingMode,
            onCurrencySelect = onCurrencySelect,
            onOpenPayPeriodSettings = onOpenPayPeriodSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeroPeriodOptionsSheet(
    useFinancialMonth: Boolean,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    currency: String,
    onDismiss: () -> Unit,
    onToggleSpendingMode: () -> Unit,
    onCurrencySelect: (String) -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.period_type_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            SheetOptionRow(
                title = stringResource(R.string.period_type_calendar),
                selected = !useFinancialMonth,
                onClick = {
                    if (useFinancialMonth) onToggleSpendingMode()
                    onDismiss()
                },
            )
            SheetOptionRow(
                title = stringResource(R.string.period_type_pay_month),
                selected = useFinancialMonth,
                onClick = {
                    if (!useFinancialMonth) onToggleSpendingMode()
                    onDismiss()
                },
            )
            if (useFinancialMonth && onOpenPayPeriodSettings != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenPayPeriodSettings()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pay_period_open_settings))
                }
            }

            if (availableCurrencies.size > 1 && !isUnifiedMode) {
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.home_currency_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                availableCurrencies.forEach { code ->
                    SheetOptionRow(
                        title = code,
                        selected = code.equals(currency, ignoreCase = true),
                        onClick = {
                            if (!code.equals(currency, ignoreCase = true)) {
                                onCurrencySelect(code)
                            }
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSparkline(
    history: List<BigDecimal>,
    lastHistory: List<BigDecimal>,
    spendingIncreased: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val currentColor = if (spendingIncreased) {
        if (isDark) expense_dark else expense_light
    } else {
        if (isDark) income_dark else income_light
    }
    val lastColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    if (history.isEmpty() && lastHistory.isEmpty()) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        fun buildPath(values: List<BigDecimal>): Path? {
            if (values.isEmpty()) return null
            val maxVal = values.maxOf { it }.coerceAtLeast(BigDecimal.ONE)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) w / 2f else index * w / (values.size - 1)
                val y = h - (value.toFloat() / maxVal.toFloat()) * h
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        buildPath(lastHistory)?.let { path ->
            drawPath(
                path = path,
                color = lastColor,
                style = Stroke(
                    width = 1.25.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                ),
            )
        }
        buildPath(history)?.let { path ->
            drawPath(
                path = path,
                color = currentColor,
                style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun SpendProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(6.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
