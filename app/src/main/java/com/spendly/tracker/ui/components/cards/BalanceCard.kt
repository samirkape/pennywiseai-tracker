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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import java.time.LocalDate

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
    paceLabel: String,
    payPeriodStartEpochDay: Long,
    payPeriodEndEpochDay: Long,
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
    useFixedReferenceColors: Boolean = false,
) {
    val view = LocalView.current
    // Effective rendered theme (not the system dark-mode toggle) — the app's own theme
    // preference can override the system setting, so isSystemInDarkTheme() can disagree
    // with what's actually on screen (e.g. app forced to dark while system is light).
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showOptionsSheet by remember { mutableStateOf(false) }
    val useExternalPeriodSheet = onPeriodChipClick != null

    // When rendering the HTML reference redesign, pin the exact hex values from the
    // approved mockup instead of deriving from theme roles — this card is a fixed,
    // pixel-matched design, not a theme-adaptive component.
    val heroContainerColor: Color
    val heroOnContainerColor: Color
    val heroMutedColor: Color
    val progressAccentColor: Color
    val progressTrackColor: Color
    val tickColor: Color
    val heroStatBgColor: Color
    val heroStatLabelColor: Color

    if (useFixedReferenceColors) {
        heroContainerColor = Color(0xFF4F378B)
        heroOnContainerColor = Color(0xFFEADDFF)
        heroMutedColor = Color(0xFFC9B8E8)
        progressAccentColor = Color(0xFFD0BCFF)
        progressTrackColor = Color(0xFF3E2E68)
        tickColor = Color(0xFFEADDFF)
        heroStatBgColor = Color(0x2E000000)
        heroStatLabelColor = heroMutedColor
    } else {
        // Built by darkening/lightening `primary` (never neutralized), instead of reading
        // `primaryContainer` directly — AMOLED mode intentionally flattens primary/secondary/
        // tertiary *Container roles to grays (see Theme.kt), which otherwise makes the hero
        // card an indistinguishable gray instead of a bold, saturated "hero" surface.
        val heroBaseColor = MaterialTheme.colorScheme.primary
        heroContainerColor = if (isDark) {
            lerp(heroBaseColor, Color.Black, 0.42f)
        } else {
            lerp(heroBaseColor, Color.White, 0.72f)
        }
        heroOnContainerColor = if (isDark) Color.White else Color(0xFF1C1B1F)
        heroMutedColor = heroOnContainerColor.copy(alpha = 0.85f)
        progressAccentColor = MaterialTheme.colorScheme.primary
        progressTrackColor = heroOnContainerColor.copy(alpha = 0.15f)
        tickColor = heroOnContainerColor
        heroStatBgColor = heroOnContainerColor.copy(alpha = 0.12f)
        heroStatLabelColor = heroOnContainerColor.copy(alpha = 0.75f)
    }
    val containerColor = heroContainerColor
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
    val todayEpoch = LocalDate.now().toEpochDay()
    val hasPeriodRange = payPeriodStartEpochDay >= 0L && payPeriodEndEpochDay >= payPeriodStartEpochDay
    val elapsedDays = if (hasPeriodRange) {
        (todayEpoch - payPeriodStartEpochDay + 1).coerceAtLeast(1L)
    } else {
        1L
    }
    val totalDays = if (hasPeriodRange) {
        (payPeriodEndEpochDay - payPeriodStartEpochDay + 1).coerceAtLeast(1L)
    } else {
        elapsedDays
    }
    val remainingDays = if (hasPeriodRange) {
        (payPeriodEndEpochDay - todayEpoch).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val projectedMonthEnd = if (currentMonthExpenses > BigDecimal.ZERO) {
        currentMonthExpenses
            .multiply(BigDecimal(totalDays))
            .divide(BigDecimal(elapsedDays), 0, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }
    val projectedMonthEndText = CurrencyFormatter.formatCurrency(projectedMonthEnd, currency)
    val dailyBudgetLeft = if (trueRemaining > BigDecimal.ZERO && remainingDays > 0) {
        trueRemaining.divide(BigDecimal(remainingDays), 0, RoundingMode.HALF_UP)
    } else {
        null
    }
    val dailyBudgetLeftText = dailyBudgetLeft?.let { "${CurrencyFormatter.formatCurrency(it, currency)}/day" } ?: "—"
    val paceDisplay = paceLabel.trim().let {
        if (it.isEmpty()) it else it.replaceFirstChar { ch -> ch.titlecase() }
    }


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
        border = BorderStroke(0.dp, Color.Transparent),
        colors = CardDefaults.cardColors(
            containerColor = heroContainerColor
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            // ── Top row: period range (left) + day progress (right) ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (useExternalPeriodSheet) {
                            onPeriodChipClick!!.invoke()
                        } else {
                            showOptionsSheet = true
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = periodChipText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = heroMutedColor,
                    letterSpacing = 0.sp,
                )
                if (periodDayLabel.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = periodDayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = heroMutedColor,
                            letterSpacing = 0.sp,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = heroMutedColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-0.25).sp,
                    ),
                    fontWeight = FontWeight.Medium,
                    brush = null,
                    color = heroOnContainerColor,
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
                            text = "spent of $incomeFormatted",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = heroMutedColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val projectedFraction = if (incomeForProgress > BigDecimal.ZERO) {
                projectedMonthEnd.divide(incomeForProgress, 4, RoundingMode.HALF_UP)
                    .toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val spentPercent = (spendFraction * 100).toInt()
            val projectedPercent = (projectedFraction * 100).toInt()

            // ── Progress bar with projection tick ─────────────────────────
            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onNavigateToBudgets()
                },
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = Color.Transparent,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SpendProgressBar(
                        expenseFraction = spendFraction,
                        investmentFraction = investmentFraction,
                        accentColor = progressAccentColor,
                        trackColor = progressTrackColor,
                        trackHeightDp = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (projectedFraction > 0f && projectedFraction <= 1f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(projectedFraction)
                                .height(8.dp)
                                .align(Alignment.CenterStart),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(tickColor, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }

            // ── Track labels: spent% (left) + projected% (right) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$spentPercent% spent",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = progressAccentColor,
                )
                if (projectedFraction > 0f && projectedFraction <= 1f) {
                    Text(
                        text = "\u2191$projectedPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = heroMutedColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // ── Status row: On track (left) + remaining (right) ───────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = heroOnContainerColor,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = paceDisplay.ifEmpty { "On track" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = heroOnContainerColor,
                    )
                }
                if (incomeForProgress > BigDecimal.ZERO) {
                    Text(
                        text = "$remainingFormatted left",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = heroOnContainerColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                HeroMoreStatCell(
                    label = stringResource(R.string.home_hero_projected_month_end),
                    value = projectedMonthEndText,
                    subLabel = null,
                    onClick = onNavigateToTransactions,
                    containerColor = heroStatBgColor,
                    labelColor = heroStatLabelColor,
                    valueColor = heroOnContainerColor,
                    subLabelColor = heroOnContainerColor.copy(alpha = 0.95f),
                    modifier = Modifier.weight(1f),
                )
                HeroMoreStatCell(
                    label = stringResource(R.string.home_hero_daily_budget_left),
                    value = dailyBudgetLeftText,
                    subLabel = null,
                    onClick = onNavigateToBudgets,
                    containerColor = heroStatBgColor,
                    labelColor = heroStatLabelColor,
                    valueColor = heroOnContainerColor,
                    subLabelColor = heroOnContainerColor.copy(alpha = 0.95f),
                    modifier = Modifier.weight(1f),
                )
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
    trackColor: Color? = null,
    trackHeightDp: Int = 6,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedAccent = accentColor ?: (if (isDark) income_dark else income_light)
    val expenseColor = resolvedAccent
    val investmentColor = resolvedAccent.copy(alpha = 0.5f)
    val resolvedTrackColor = trackColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
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
                .background(resolvedTrackColor),
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
    containerColor: Color,
    labelColor: Color,
    valueColor: Color,
    subLabelColor: Color,
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
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = labelColor,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = valueColor,
                maxLines = 1,
            )
            if (!subLabel.isNullOrEmpty()) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = subLabelColor,
                    maxLines = 1,
                )
            }
        }
    }
}
