package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Hero spend: period chip, amount, and delta vs last month (no chart).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroSpendCard(
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentMonthExpenses: BigDecimal,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    spendingPeriodLabel: String,
    useFinancialMonth: Boolean,
    onToggleSpendingMode: () -> Unit,
    onCurrencySelect: (String) -> Unit,
    onShowBreakdown: () -> Unit,
    onOpenPayPeriodSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    var showOptionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val spendingIncreased = monthlyChange >= BigDecimal.ZERO
    val deltaColor = if (spendingIncreased) {
        if (isDark) expense_dark else expense_light
    } else {
        if (isDark) income_dark else income_light
    }
    val absPercent = kotlin.math.abs(monthlyChangePercent)
    val arrow = if (spendingIncreased) "↑" else "↓"
    val deltaText = "$arrow $absPercent% vs last month"

    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    val periodChipText = if (useFinancialMonth && spendingPeriodLabel.isNotEmpty()) {
        spendingPeriodLabel
    } else if (spendingPeriodLabel.isNotEmpty()) {
        spendingPeriodLabel
    } else {
        stringResource(R.string.period_type_calendar)
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
                horizontalAlignment = Alignment.Start,
            ) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        showOptionsSheet = true
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

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = "Spend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AnimatedCurrencyText(
                    text = CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    brush = null,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onShowBreakdown()
                        },
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = deltaColor,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
    }

    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
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
                        showOptionsSheet = false
                    },
                )
                SheetOptionRow(
                    title = stringResource(R.string.period_type_pay_month),
                    selected = useFinancialMonth,
                    onClick = {
                        if (!useFinancialMonth) onToggleSpendingMode()
                        showOptionsSheet = false
                    },
                )
                if (useFinancialMonth && onOpenPayPeriodSettings != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(
                        onClick = {
                            showOptionsSheet = false
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
                        text = "Currency",
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
                                showOptionsSheet = false
                            },
                        )
                    }
                }
            }
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
