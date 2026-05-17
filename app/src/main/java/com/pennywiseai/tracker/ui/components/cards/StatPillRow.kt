package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.expense_light
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.income_light
import com.pennywiseai.tracker.ui.theme.loan_dark
import com.pennywiseai.tracker.ui.theme.loan_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatPillRow(
    incomeText: String,
    netText: String,
    onIncomeClick: () -> Unit,
    onNetClick: () -> Unit,
    modifier: Modifier = Modifier,
    expenseText: String? = null,
    onExpenseClick: () -> Unit = {},
    budgetText: String? = null,
    budgetDotWarning: Boolean = false,
    onBudgetClick: () -> Unit = {},
    loanLabel: String? = null,
    loanText: String? = null,
    onLoanClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatPill(
            dotColor = if (!isSystemInDarkTheme()) income_light else income_dark,
            label = "Income",
            value = incomeText,
            onClick = onIncomeClick,
            modifier = Modifier.weight(1f),
        )
        if (expenseText != null) {
            StatPill(
                dotColor = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                label = "Expense",
                value = expenseText,
                onClick = onExpenseClick,
                modifier = Modifier.weight(1f),
            )
        }
        StatPill(
            dotColor = MaterialTheme.colorScheme.tertiary,
            label = "Net",
            value = netText,
            onClick = onNetClick,
            modifier = Modifier.weight(1f),
        )
        if (budgetText != null) {
            StatPill(
                dotColor = if (budgetDotWarning) {
                    if (!isSystemInDarkTheme()) expense_light else expense_dark
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                label = "Budget",
                value = budgetText,
                onClick = onBudgetClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (loanLabel != null && loanText != null && onLoanClick != null) {
            StatPill(
                dotColor = if (!isSystemInDarkTheme()) loan_light else loan_dark,
                label = loanLabel,
                value = loanText,
                onClick = onLoanClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatPill(
    dotColor: Color,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "pillScale",
    )
    Surface(
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun formatStatAmount(value: BigDecimal, currency: String): String =
    CurrencyFormatter.formatCurrency(value, currency)

/**
 * Stat pills for transaction group detail: count plus optional income/expense when non-zero.
 * Matches [StatPill] styling from the home [StatPillRow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSummaryStatPillRow(
    transactionCount: Int,
    totalIncome: BigDecimal,
    totalExpense: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val showIncome = totalIncome > BigDecimal.ZERO
    val showExpense = totalExpense > BigDecimal.ZERO
    val noOp: () -> Unit = {}

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatPill(
            dotColor = MaterialTheme.colorScheme.tertiary,
            label = "Transactions",
            value = transactionCount.toString(),
            onClick = noOp,
            modifier = Modifier.weight(1f),
        )
        if (showIncome) {
            StatPill(
                dotColor = if (!isSystemInDarkTheme()) income_light else income_dark,
                label = "Income",
                value = formatStatAmount(totalIncome, currency),
                onClick = noOp,
                modifier = Modifier.weight(1f),
            )
        }
        if (showExpense) {
            StatPill(
                dotColor = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                label = "Expenses",
                value = formatStatAmount(totalExpense, currency),
                onClick = noOp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
