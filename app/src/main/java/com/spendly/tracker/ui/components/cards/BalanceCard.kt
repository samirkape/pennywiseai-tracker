package com.spendly.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
import com.spendly.tracker.R
import com.spendly.tracker.ui.components.AnimatedCurrencyText
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.PlayfairDisplayFontFamily
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.textMuted
import com.spendly.tracker.ui.theme.expense_dark
import com.spendly.tracker.ui.theme.expense_light
import com.spendly.tracker.ui.theme.income_dark
import com.spendly.tracker.ui.theme.income_light
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Hero spend: period context, amount, trend sparkline, remaining headroom,
 * and a collapsible "More stats" fold for secondary period metrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroSpendCard(
    currency: String,
    currentMonthExpenses: BigDecimal,
    currentMonthIncome: BigDecimal,
    currentMonthTotal: BigDecimal,
    periodDayLabel: String,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    spendingPeriodLabel: String,
    useFinancialMonth: Boolean,
    onToggleSpendingMode: () -> Unit,
    onCurrencySelect: (String) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onSpendSoFarClick: (() -> Unit)? = null,
    onNavigateToBudgets: () -> Unit,
    onShowBreakdown: () -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)? = null,
    onPeriodChipClick: (() -> Unit)? = null,
    currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    onNavigateToInvestmentTransactions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    var showOptionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val useExternalPeriodSheet = onPeriodChipClick != null

    val accentColor = if (isDark) income_dark else income_light

    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val periodChipText = when {
        spendingPeriodLabel.isNotEmpty() -> spendingPeriodLabel
        else -> stringResource(R.string.period_type_calendar)
    }

    val incomeForProgress = currentMonthIncome.coerceAtLeast(BigDecimal.ZERO)

    fun fraction(amount: BigDecimal): Float =
        if (incomeForProgress > BigDecimal.ZERO)
            amount.divide(incomeForProgress, 4, RoundingMode.HALF_UP)
                .toFloat().coerceIn(0f, 1f)
        else 0f

    val spendFraction = fraction(currentMonthExpenses)
    val investmentFraction = fraction(currentMonthInvestment)
        .coerceIn(0f, (1f - spendFraction).coerceAtLeast(0f))

    // "Left" = income minus both expenses AND investments — true available cash
    val trueRemaining = (currentMonthTotal - currentMonthInvestment).coerceAtLeast(BigDecimal.ZERO)
    val remainingFormatted = CurrencyFormatter.formatCurrency(trueRemaining.setScale(0, RoundingMode.HALF_UP), currency)
    val incomeFormatted = CurrencyFormatter.formatCurrency(incomeForProgress.setScale(0, RoundingMode.HALF_UP), currency)


    SpendlyCardV2(
        contentPadding = Spacing.md,
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
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (onSpendSoFarClick != null) onSpendSoFarClick() else onNavigateToTransactions()
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            // ── Eyebrow: "SPENDING" section label (left) + period date ▼ + status pill (right) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: fixed section label — same pattern as "THIS WEEK", "LAST 7 DAYS"
                Text(
                    text = stringResource(R.string.home_spent_so_far).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted,
                    letterSpacing = 0.66.sp,
                )
                // Right: tappable period date range + optional status pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (useExternalPeriodSheet) {
                            onPeriodChipClick!!.invoke()
                        } else {
                            showOptionsSheet = true
                        }
                    },
                ) {
                    Text(
                        text = periodChipText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted,
                        letterSpacing = 0.66.sp,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // ── Amount row: big expense + "of [income]" ──────────────────────
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
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedCurrencyText(
                    text = CurrencyFormatter.formatCurrency(currentMonthExpenses.setScale(0, RoundingMode.HALF_UP), currency),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        letterSpacing = (-0.25).sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    brush = null,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (incomeForProgress > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onShowBreakdown()
                            }
                            .padding(bottom = 4.dp),
                    ) {
                        Text(
                            text = "of $incomeFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.textMuted,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "How is this calculated?",
                            tint = MaterialTheme.colorScheme.textMuted.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(13.dp)
                                .padding(bottom = 1.dp)
                                .align(Alignment.Bottom),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ── Progress bar (4dp, teal accent) ─────────────────────────────
            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onNavigateToBudgets()
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = Color.Transparent,
            ) {
                SpendProgressBar(
                    expenseFraction = spendFraction,
                    investmentFraction = investmentFraction,
                    accentColor = accentColor,
                    trackHeightDp = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // ── Footer: day progress (left) + remaining (right) ──────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (periodDayLabel.isNotEmpty()) {
                    Text(
                        text = periodDayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted,
                    )
                }
                if (incomeForProgress > BigDecimal.ZERO) {
                    Text(
                        text = "$remainingFormatted remaining",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                    )
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
    expenseFraction: Float,
    investmentFraction: Float = 0f,
    accentColor: Color? = null,
    trackHeightDp: Int = 6,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedAccent = accentColor ?: (if (isDark) income_dark else income_light)
    val expenseColor = resolvedAccent
    val investmentColor = resolvedAccent.copy(alpha = 0.5f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val totalFraction = (expenseFraction + investmentFraction).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(trackHeightDp.dp)
            .clip(RoundedCornerShape((trackHeightDp / 2).dp)),
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackColor),
        )
        // Investment segment — drawn first (fills from 0 to total), shows as green
        if (totalFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(totalFraction)
                    .background(investmentColor),
            )
        }
        // Expense segment — overlays investment from left, shows expense color
        if (expenseFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(expenseFraction)
                    .background(expenseColor),
            )
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroMoreStatCell(
    label: String,
    value: String,
    subLabel: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick?.invoke()
        },
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.CornerRadius.small),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!subLabel.isNullOrEmpty()) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
