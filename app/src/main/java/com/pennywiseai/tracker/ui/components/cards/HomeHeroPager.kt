package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.investment_dark
import com.pennywiseai.tracker.ui.theme.investment_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.hazeEffect
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Swipeable hero: page 1 = spend (excl. investments), page 2 = total outflow when investments exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHeroPager(
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentMonthExpenses: BigDecimal,
    currentMonthIncome: BigDecimal,
    currentMonthTotal: BigDecimal,
    currentMonthInvestment: BigDecimal,
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
    onNavigateToInvestmentTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onShowBreakdown: () -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    val hasInvestmentPage = currentMonthInvestment > BigDecimal.ZERO
    var showOptionsSheet by remember { mutableStateOf(false) }

    val openPeriodOptions: () -> Unit = { showOptionsSheet = true }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasInvestmentPage) {
            val pagerState = rememberPagerState(pageCount = { 2 })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> HeroSpendCard(
                        modifier = Modifier.fillMaxWidth(),
                        blurEffects = blurEffects,
                        hazeState = hazeState,
                        monthlyChange = monthlyChange,
                        monthlyChangePercent = monthlyChangePercent,
                        currency = currency,
                        currentMonthExpenses = currentMonthExpenses,
                        currentMonthIncome = currentMonthIncome,
                        currentMonthTotal = currentMonthTotal,
                        spendingHistory = spendingHistory,
                        lastMonthSpendingHistory = lastMonthSpendingHistory,
                        periodDayLabel = periodDayLabel,
                        availableCurrencies = availableCurrencies,
                        isUnifiedMode = isUnifiedMode,
                        spendingPeriodLabel = spendingPeriodLabel,
                        useFinancialMonth = useFinancialMonth,
                        onToggleSpendingMode = onToggleSpendingMode,
                        onCurrencySelect = onCurrencySelect,
                        onNavigateToTransactions = onNavigateToTransactions,
                        onNavigateToBudgets = onNavigateToBudgets,
                        onShowBreakdown = onShowBreakdown,
                        onOpenPayPeriodSettings = onOpenPayPeriodSettings,
                        onPeriodChipClick = openPeriodOptions,
                    )
                    else -> HeroOutflowCard(
                        modifier = Modifier.fillMaxWidth(),
                        blurEffects = blurEffects,
                        hazeState = hazeState,
                        currency = currency,
                        currentMonthExpenses = currentMonthExpenses,
                        currentMonthInvestment = currentMonthInvestment,
                        currentMonthIncome = currentMonthIncome,
                        periodDayLabel = periodDayLabel,
                        spendingPeriodLabel = spendingPeriodLabel,
                        onPeriodChipClick = openPeriodOptions,
                        onNavigateToTransactions = onNavigateToTransactions,
                        onNavigateToInvestmentTransactions = onNavigateToInvestmentTransactions,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            HeroPagerIndicator(
                pageCount = 2,
                currentPage = pagerState.currentPage,
                pageLabels = listOf(
                    stringResource(R.string.home_hero_page_spend),
                    stringResource(R.string.home_hero_page_outflow),
                ),
            )
        } else {
            HeroSpendCard(
                modifier = Modifier.fillMaxWidth(),
                blurEffects = blurEffects,
                hazeState = hazeState,
                monthlyChange = monthlyChange,
                monthlyChangePercent = monthlyChangePercent,
                currency = currency,
                currentMonthExpenses = currentMonthExpenses,
                currentMonthIncome = currentMonthIncome,
                currentMonthTotal = currentMonthTotal,
                spendingHistory = spendingHistory,
                lastMonthSpendingHistory = lastMonthSpendingHistory,
                periodDayLabel = periodDayLabel,
                availableCurrencies = availableCurrencies,
                isUnifiedMode = isUnifiedMode,
                spendingPeriodLabel = spendingPeriodLabel,
                useFinancialMonth = useFinancialMonth,
                onToggleSpendingMode = onToggleSpendingMode,
                onCurrencySelect = onCurrencySelect,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToBudgets = onNavigateToBudgets,
                onShowBreakdown = onShowBreakdown,
                onOpenPayPeriodSettings = onOpenPayPeriodSettings,
            )
        }
    }

    if (showOptionsSheet) {
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

@Composable
private fun HeroPagerIndicator(
    pageCount: Int,
    currentPage: Int,
    pageLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = currentPage == index
            val indicatorWidth by animateDpAsState(
                targetValue = if (isActive) 16.dp else 6.dp,
                animationSpec = tween(200),
                label = "hero_indicator_$index",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(indicatorWidth)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isActive) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        ),
                )
                Text(
                    text = pageLabels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Hero page 2: total cash outflow including investments for the period.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroOutflowCard(
    currency: String,
    currentMonthExpenses: BigDecimal,
    currentMonthInvestment: BigDecimal,
    currentMonthIncome: BigDecimal,
    periodDayLabel: String,
    spendingPeriodLabel: String,
    onPeriodChipClick: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToInvestmentTransactions: () -> Unit,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val investmentColor = if (!isDark) investment_light else investment_dark
    val totalOutflow = currentMonthExpenses + currentMonthInvestment
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    val periodChipText = spendingPeriodLabel.ifEmpty {
        stringResource(R.string.period_type_calendar)
    }

    val incomeForProgress = currentMonthIncome.coerceAtLeast(BigDecimal.ZERO)
    val outflowFraction = if (incomeForProgress > BigDecimal.ZERO) {
        totalOutflow
            .divide(incomeForProgress, 4, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val percentOfIncome = if (incomeForProgress > BigDecimal.ZERO) {
        totalOutflow
            .multiply(BigDecimal(100))
            .divide(incomeForProgress, 0, RoundingMode.HALF_UP)
            .toInt()
    } else {
        0
    }

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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onPeriodChipClick()
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onNavigateToTransactions()
                    },
            ) {
                Text(
                    text = stringResource(R.string.home_total_outflow),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.home_incl_investments),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedCurrencyText(
                    text = CurrencyFormatter.formatCurrency(totalOutflow, currency),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    brush = null,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutflowBreakdownCell(
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = stringResource(R.string.home_spending_portion_label),
                    value = CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTransactions,
                )
                OutflowBreakdownCell(
                    dotColor = investmentColor,
                    label = stringResource(R.string.home_invested_portion_label),
                    value = CurrencyFormatter.formatCurrency(currentMonthInvestment, currency),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToInvestmentTransactions,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            SpendProgressBar(
                progress = outflowFraction,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.home_percent_of_income, percentOfIncome),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutflowBreakdownCell(
    dotColor: Color,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
