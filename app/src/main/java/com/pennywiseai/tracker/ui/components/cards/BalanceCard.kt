package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
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
 * Hero spend: period context, amount, trend sparkline, remaining headroom,
 * and a collapsible "More stats" fold for secondary period metrics.
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
    /** When set, a short tap on the “Spend so far” row opens this (e.g. timeline sheet) instead of Transactions. */
    onSpendSoFarClick: (() -> Unit)? = null,
    onNavigateToBudgets: () -> Unit,
    onShowBreakdown: () -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)? = null,
    onPeriodChipClick: (() -> Unit)? = null,
    currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    onNavigateToInvestmentTransactions: (() -> Unit)? = null,
    // More-stats fold data — all optional; fold hidden when all null/empty
    moreStatsIncomeText: String? = null,
    moreStatsIncomeSubLabel: String? = null,
    moreStatsTopCategoryName: String? = null,
    moreStatsTopCategorySubLabel: String? = null,
    moreStatsPaceText: String? = null,
    moreStatsPaceSubLabel: String? = null,
    moreStatsLoanLabel: String? = null,
    moreStatsLoanText: String? = null,
    moreStatsSubscriptionsLabel: String? = null,
    moreStatsSubscriptionsValue: String? = null,
    onMoreStatsIncomeClick: (() -> Unit)? = null,
    onMoreStatsTopCategoryClick: (() -> Unit)? = null,
    onMoreStatsPaceClick: (() -> Unit)? = null,
    onMoreStatsLoanClick: (() -> Unit)? = null,
    onMoreStatsSubscriptionsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    var showOptionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val useExternalPeriodSheet = onPeriodChipClick != null
    var moreStatsExpanded by remember { mutableStateOf(false) }
    val hasSubscriptionsMoreStat = onMoreStatsSubscriptionsClick != null &&
        !moreStatsSubscriptionsLabel.isNullOrEmpty()
    val hasMoreStats = !moreStatsIncomeText.isNullOrEmpty() ||
        !moreStatsTopCategoryName.isNullOrEmpty() ||
        !moreStatsPaceText.isNullOrEmpty() ||
        !moreStatsLoanLabel.isNullOrEmpty() ||
        hasSubscriptionsMoreStat

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

    fun pct(amount: BigDecimal): Int =
        if (incomeForProgress > BigDecimal.ZERO)
            amount.multiply(BigDecimal(100))
                .divide(incomeForProgress, 0, RoundingMode.HALF_UP)
                .toInt()
        else 0

    fun fraction(amount: BigDecimal): Float =
        if (incomeForProgress > BigDecimal.ZERO)
            amount.divide(incomeForProgress, 4, RoundingMode.HALF_UP)
                .toFloat().coerceIn(0f, 1f)
        else 0f

    val spendFraction = fraction(currentMonthExpenses)
    val investmentFraction = fraction(currentMonthInvestment)
        .coerceIn(0f, (1f - spendFraction).coerceAtLeast(0f))

    val spentPercent = pct(currentMonthExpenses)
    val investedPercent = pct(currentMonthInvestment)

    // "Left" = income minus both expenses AND investments — true available cash
    val trueRemaining = (currentMonthTotal - currentMonthInvestment).coerceAtLeast(BigDecimal.ZERO)
    val remainingFormatted = CurrencyFormatter.formatCurrency(trueRemaining, currency)

    PennyWiseCardV2(
        contentPadding = Spacing.sm,
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
                            onPeriodChipClick!!.invoke()
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
                            if (onSpendSoFarClick != null) {
                                onSpendSoFarClick()
                            } else {
                                onNavigateToTransactions()
                            }
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

            if (currentMonthInvestment > BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                val investmentColor = if (isDark) income_dark else income_light
                val investedFormatted = CurrencyFormatter.formatCurrency(currentMonthInvestment, currency)
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onNavigateToInvestmentTransactions?.invoke()
                    },
                    shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                    color = investmentColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
                                drawCircle(color = investmentColor)
                            }
                            Text(
                                text = stringResource(R.string.home_also_invested),
                                style = MaterialTheme.typography.labelSmall,
                                color = investmentColor,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = investedFormatted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = investmentColor,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = investmentColor,
                            )
                        }
                    }
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
                        expenseFraction = spendFraction,
                        investmentFraction = investmentFraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: "X% spent · Y% invested"  (or just "X% spent" when no investments)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "$spentPercent% spent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (investedPercent > 0) {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                val investColor = if (isDark) income_dark else income_light
                                Text(
                                    text = "$investedPercent% invested",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = investColor,
                                )
                            }
                        }
                        // Right: "₹X left →"
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

            if (hasMoreStats) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        moreStatsExpanded = !moreStatsExpanded
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_more_stats),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (moreStatsExpanded) 270f else 90f,
                            animationSpec = tween(200),
                            label = "chevron",
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = moreStatsExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            if (!moreStatsIncomeText.isNullOrEmpty()) {
                                HeroMoreStatCell(
                                    label = stringResource(R.string.home_summary_income),
                                    value = moreStatsIncomeText,
                                    subLabel = moreStatsIncomeSubLabel,
                                    onClick = onMoreStatsIncomeClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (!moreStatsTopCategoryName.isNullOrEmpty()) {
                                HeroMoreStatCell(
                                    label = stringResource(R.string.home_summary_top_spend),
                                    value = moreStatsTopCategoryName,
                                    subLabel = moreStatsTopCategorySubLabel,
                                    onClick = onMoreStatsTopCategoryClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (!moreStatsPaceText.isNullOrEmpty()) {
                                HeroMoreStatCell(
                                    label = stringResource(R.string.home_summary_pace),
                                    value = moreStatsPaceText,
                                    subLabel = moreStatsPaceSubLabel,
                                    onClick = onMoreStatsPaceClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (!moreStatsLoanLabel.isNullOrEmpty() && !moreStatsLoanText.isNullOrEmpty()) {
                            Surface(
                                onClick = { onMoreStatsLoanClick?.invoke() },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = moreStatsLoanLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = moreStatsLoanText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        if (!moreStatsSubscriptionsLabel.isNullOrEmpty() && onMoreStatsSubscriptionsClick != null) {
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onMoreStatsSubscriptionsClick.invoke()
                                },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = moreStatsSubscriptionsLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = moreStatsSubscriptionsValue.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
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
    expenseFraction: Float,
    investmentFraction: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val expenseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val investmentColor = if (isDark) income_dark else income_light
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val totalFraction = (expenseFraction + investmentFraction).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
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
