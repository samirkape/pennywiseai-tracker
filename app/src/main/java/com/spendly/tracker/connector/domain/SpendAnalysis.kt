package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Privacy-safe summary of the user's spending over an [AnalysisWindow].
 *
 * Only aggregate figures are kept — never descriptions, SMS bodies, account numbers
 * or bank names. This is the only shape that ever reaches a remote provider.
 */
data class SpendAnalysis(
    val window: AnalysisWindow,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currency: String,
    val totalExpense: BigDecimal,
    val totalIncome: BigDecimal,
    val expenseCount: Int,
    val incomeCount: Int,
    val dailyAverage: BigDecimal,
    val categories: List<CategorySpend>,
    val merchants: List<MerchantSpend>,
    val monthlyTrend: List<MonthSpend>,
    val recurring: List<RecurringSpend>,
    val largestExpenses: List<NamedExpense>,
    val weekdaySpend: List<WeekdaySpend>,
    val previousExpense: BigDecimal?,
    val previousExpenseChangePercent: Double?,
    val tags: List<TagSpend>,
    /**
     * Money that left the account without being spending: credit card bill
     * payments, transfers, investments and loan-linked rows. Reported separately
     * so the model knows the totals above deliberately exclude them.
     */
    val moneyMoved: MoneyMovedSummary? = null
) {
    val netCashFlow: BigDecimal get() = totalIncome.subtract(totalExpense)

    val savingsRatePercent: Double?
        get() = if (totalIncome.signum() > 0) {
            netCashFlow.multiply(BigDecimal(100))
                .divide(totalIncome, 1, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            null
        }

    /**
     * Renders the analysis as a compact, token-efficient brief for the model.
     * Amounts are plain decimals (no currency symbols) so the model cannot misread them.
     */
    fun renderPromptPayload(): String = buildString {
        appendLine("SPENDING BRIEF")
        appendLine("period: ${window.label()} (${startDate} to $endDate)")
        appendLine("currency: $currency")
        appendLine("total_spend: ${plain(totalExpense)}")
        appendLine("total_income: ${plain(totalIncome)}")
        appendLine("net_cash_flow: ${plain(netCashFlow)}")
        appendLine("spend_transactions: $expenseCount")
        appendLine("income_transactions: $incomeCount")
        appendLine("average_daily_spend: ${plain(dailyAverage)}")
        savingsRatePercent?.let { appendLine("savings_rate_percent: $it") }
        previousExpense?.let { previous ->
            appendLine("previous_period_spend: ${plain(previous)}")
            previousExpenseChangePercent?.let {
                val sign = if (it >= 0) "+" else ""
                appendLine("spend_change_vs_previous_percent: $sign${formatPercent(it)}")
            }
        }

        if (categories.isNotEmpty()) {
            appendLine()
            appendLine("BY_CATEGORY (category | spend | share_percent | transactions)")
            categories.forEach {
                appendLine("- ${it.category} | ${plain(it.amount)} | ${formatPercent(it.sharePercent)} | ${it.transactionCount}")
            }
        }

        if (merchants.isNotEmpty()) {
            appendLine()
            appendLine("TOP_MERCHANTS (merchant | spend | transactions)")
            merchants.forEach {
                appendLine("- ${it.merchantName} | ${plain(it.amount)} | ${it.transactionCount}")
            }
        }

        if (monthlyTrend.size > 1) {
            appendLine()
            appendLine("MONTHLY_TREND (month | spend | income)")
            monthlyTrend.forEach {
                appendLine("- ${it.month} | ${plain(it.expense)} | ${plain(it.income)}")
            }
        }

        if (recurring.isNotEmpty()) {
            appendLine()
            appendLine("RECURRING_MERCHANTS (merchant | times | spend | typical_amount)")
            recurring.forEach {
                appendLine("- ${it.merchantName} | ${it.occurrences} | ${plain(it.amount)} | ${plain(it.typicalAmount)}")
            }
        }

        if (largestExpenses.isNotEmpty()) {
            appendLine()
            appendLine("LARGEST_SINGLE_SPENDINGS (merchant | amount | category | date)")
            largestExpenses.forEach {
                appendLine("- ${it.name} | ${plain(it.amount)} | ${it.category} | ${it.date}")
            }
        }

        if (weekdaySpend.size == 7) {
            appendLine()
            appendLine("SPEND_BY_WEEKDAY (weekday | spend | transactions)")
            weekdaySpend.forEach {
                appendLine("- ${it.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }} | ${plain(it.amount)} | ${it.transactionCount}")
            }
        }

        if (tags.isNotEmpty()) {
            appendLine()
            appendLine("SPEND_BY_TAG (tag | spend | transactions)")
            tags.forEach {
                appendLine("- ${it.tag} | ${plain(it.amount)} | ${it.transactionCount}")
            }
        }

        moneyMoved?.takeIf { !it.isEmpty }?.let { moved ->
            appendLine()
            appendLine("MONEY_MOVED_NOT_COUNTED_AS_SPENDING")
            appendLine("total_outflow: ${plain(moved.outflowTotal)}")
            appendLine("credit_card_bill_payments: ${plain(moved.creditCardBillPayments.amount)} (${moved.creditCardBillPayments.transactionCount})")
            appendLine("transfers_to_own_accounts: ${plain(moved.selfTransfers.amount)} (${moved.selfTransfers.transactionCount})")
            appendLine("transfers_to_others: ${plain(moved.transfersToOthers.amount)} (${moved.transfersToOthers.transactionCount})")
            appendLine("investments: ${plain(moved.investments.amount)} (${moved.investments.transactionCount})")
            appendLine("loan_linked_outflow: ${plain(moved.loanLinkedOutflow.amount)} (${moved.loanLinkedOutflow.transactionCount})")
            appendLine("loan_linked_inflow: ${plain(moved.loanLinkedInflow.amount)} (${moved.loanLinkedInflow.transactionCount})")
        }
    }

    private fun plain(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** Locale-independent percentage so the model always reads a dot decimal separator. */
    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.1f", value)
}

data class CategorySpend(
    val category: String,
    val amount: BigDecimal,
    val sharePercent: Double,
    val transactionCount: Int
)

data class MerchantSpend(
    val merchantName: String,
    val amount: BigDecimal,
    val transactionCount: Int
)

data class MonthSpend(
    val month: YearMonth,
    val expense: BigDecimal,
    val income: BigDecimal
)

data class RecurringSpend(
    val merchantName: String,
    val occurrences: Int,
    val amount: BigDecimal,
    val typicalAmount: BigDecimal
)

data class NamedExpense(
    val name: String,
    val amount: BigDecimal,
    val category: String,
    val date: LocalDate
)

data class WeekdaySpend(
    val dayOfWeek: DayOfWeek,
    val amount: BigDecimal,
    val transactionCount: Int
)

data class TagSpend(
    val tag: String,
    val amount: BigDecimal,
    val transactionCount: Int
)

/** Aggregate of rows that are deliberately not treated as spending. */
data class FlowSummary(
    val amount: BigDecimal,
    val transactionCount: Int
) {
    companion object {
        val EMPTY = FlowSummary(BigDecimal.ZERO, 0)
    }
}

/**
 * Outflow that is not spending. Kept apart from the spend totals so the model can
 * tell a transfer or a credit card bill payment from real expenditure.
 */
data class MoneyMovedSummary(
    val creditCardBillPayments: FlowSummary = FlowSummary.EMPTY,
    val selfTransfers: FlowSummary = FlowSummary.EMPTY,
    val transfersToOthers: FlowSummary = FlowSummary.EMPTY,
    val investments: FlowSummary = FlowSummary.EMPTY,
    val loanLinkedOutflow: FlowSummary = FlowSummary.EMPTY,
    val loanLinkedInflow: FlowSummary = FlowSummary.EMPTY
) {
    val outflowTotal: BigDecimal
        get() = creditCardBillPayments.amount +
            selfTransfers.amount +
            transfersToOthers.amount +
            investments.amount +
            loanLinkedOutflow.amount

    val isEmpty: Boolean
        get() = creditCardBillPayments.transactionCount == 0 &&
            selfTransfers.transactionCount == 0 &&
            transfersToOthers.transactionCount == 0 &&
            investments.transactionCount == 0 &&
            loanLinkedOutflow.transactionCount == 0 &&
            loanLinkedInflow.transactionCount == 0
}
