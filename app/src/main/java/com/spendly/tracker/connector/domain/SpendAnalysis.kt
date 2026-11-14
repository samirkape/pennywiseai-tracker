package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Privacy-safe summary of the user's money over an [AnalysisWindow].
 *
 * Only aggregate figures are kept — never descriptions, SMS bodies, account numbers
 * or bank names. This is the only shape that ever reaches a remote provider.
 *
 * The accounting rules the figures follow:
 * - `grossExpense` is every expense row in the period, before any refund.
 * - `refundsReceived` is every refund and reimbursement received in the period.
 *   Reimbursements are never counted as income.
 * - `netExpense` is `grossExpense - refundsReceived`.
 * - Transfers, credit card settlements, investments and loan-linked rows are
 *   never spending and never income; they live in [moneyMoved].
 */
data class SpendAnalysis(
    val window: AnalysisWindow,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currency: String,
    /** Spending before refunds. */
    val grossExpense: BigDecimal,
    /** Money received back during the period. */
    val refundsReceived: BigDecimal,
    /** grossExpense - refundsReceived. */
    val netExpense: BigDecimal,
    /** Income that is not a refund, a loan movement or a transfer. */
    val totalIncome: BigDecimal,
    val expenseCount: Int,
    val incomeCount: Int,
    val refundCount: Int,
    /** netExpense divided by the number of days in the period. */
    val dailyAverage: BigDecimal,
    val categories: List<CategorySpend>,
    val merchants: List<MerchantSpend>,
    /** Compact month view kept for backwards compatibility. */
    val monthlyTrend: List<MonthSpend>,
    /** Full month view with partial-month flags and per-category spend. */
    val months: List<MonthBreakdown>,
    val recurring: List<RecurringSpend>,
    val largestExpenses: List<NamedExpense>,
    val weekdaySpend: List<WeekdaySpend>,
    val previousExpense: BigDecimal?,
    val previousExpenseChangePercent: Double?,
    val tags: List<TagSpend>,
    /** Refunds and reimbursements, split by kind and by match status. */
    val refunds: RefundSummary,
    /**
     * Money that left the account without being spending: credit card bill
     * payments, transfers, investments and loan-linked rows. Reported separately
     * so the model knows the totals above deliberately exclude them.
     */
    val moneyMoved: MoneyMovedSummary? = null,
    /** Derived indicators with their formulas stated next to them. */
    val health: FinancialHealth? = null,
    /** What the file could and could not see. */
    val dataQuality: DataQualityReport
) {
    val netCashFlow: BigDecimal get() = totalIncome.subtract(netExpense)

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
        appendLine("gross_expense: ${plain(grossExpense)}")
        appendLine("refunds_received: ${plain(refundsReceived)}")
        appendLine("net_expense: ${plain(netExpense)}")
        appendLine("total_income: ${plain(totalIncome)}")
        appendLine("net_cash_flow: ${plain(netCashFlow)}")
        appendLine("spend_transactions: $expenseCount")
        appendLine("income_transactions: $incomeCount")
        appendLine("refund_transactions: $refundCount")
        appendLine("average_daily_spend: ${plain(dailyAverage)}")
        savingsRatePercent?.let { appendLine("savings_rate_percent: $it") }
        previousExpense?.let { previous ->
            appendLine("previous_period_net_spend: ${plain(previous)}")
            previousExpenseChangePercent?.let {
                val sign = if (it >= 0) "+" else ""
                appendLine("spend_change_vs_previous_percent: $sign${formatPercent(it)}")
            }
        }

        if (categories.isNotEmpty()) {
            appendLine()
            appendLine(
                "BY_CATEGORY (category | gross_expense | refunds_received | net_expense | " +
                    "share_of_net_spending_percent | transactions | essentiality | behaviour)"
            )
            categories.forEach {
                appendLine(
                    "- ${it.category} | ${plain(it.grossExpense)} | ${plain(it.refundsReceived)} | " +
                        "${plain(it.netExpense)} | ${formatPercent(it.shareOfNetSpendingPercent)} | " +
                        "${it.transactionCount} | ${it.essentiality.label} | ${it.behaviour.label}"
                )
            }
        }

        if (merchants.isNotEmpty()) {
            appendLine()
            appendLine("TOP_MERCHANTS (merchant | net_spend | transactions)")
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

        if (months.isNotEmpty()) {
            appendLine()
            appendLine(
                "MONTHLY_BREAKDOWN (month | partial | days | income | gross_expense | refunds | " +
                    "net_expense | net_cash_flow | savings_rate_percent | investments | " +
                    "transfers_out | transfers_in | loan_inflow | loan_outflow)"
            )
            months.forEach {
                appendLine(
                    "- ${it.label} | ${if (it.isPartial) "partial" else "full"} | ${it.daysInPeriod} | " +
                        "${plain(it.income)} | ${plain(it.grossExpense)} | ${plain(it.refunds)} | " +
                        "${plain(it.netExpense)} | ${plain(it.netCashFlow)} | " +
                        "${it.savingsRatePercent?.let { value -> formatPercent(value) } ?: "-"} | " +
                        "${plain(it.investments)} | ${plain(it.transfersOut)} | ${plain(it.transfersIn)} | " +
                        "${plain(it.loanInflow)} | ${plain(it.loanOutflow)}"
                )
            }
        }

        val monthlyCategoryRows = months.flatMap { month ->
            month.categories.map { category -> month.label to category }
        }
        if (monthlyCategoryRows.isNotEmpty()) {
            appendLine()
            appendLine(
                "MONTHLY_BY_CATEGORY (month | category | gross_expense | refunds | net_expense | transactions)"
            )
            monthlyCategoryRows.forEach { (monthLabel, category) ->
                appendLine(
                    "- $monthLabel | ${category.category} | ${plain(category.grossExpense)} | " +
                        "${plain(category.refundsReceived)} | ${plain(category.netExpense)} | " +
                        "${category.transactionCount}"
                )
            }
        }

        if (recurring.isNotEmpty()) {
            appendLine()
            appendLine("RECURRING_MERCHANTS (merchant | times | spend | typical_amount)")
            recurring.forEach {
                appendLine(
                    "- ${it.merchantName} | ${it.occurrences} | ${plain(it.amount)} | " +
                        "${plain(it.typicalAmount)}"
                )
            }
        }

        if (largestExpenses.isNotEmpty()) {
            appendLine()
            appendLine("LARGEST_SINGLE_SPENDINGS (merchant | gross_amount | refunded | net_amount | category | date)")
            largestExpenses.forEach {
                appendLine(
                    "- ${it.name} | ${plain(it.grossAmount)} | ${plain(it.refundedAmount)} | " +
                        "${plain(it.netAmount)} | ${it.category} | ${it.date}"
                )
            }
        }

        if (weekdaySpend.size == 7) {
            appendLine()
            appendLine("SPEND_BY_WEEKDAY (weekday | spend | transactions)")
            weekdaySpend.forEach {
                appendLine(
                    "- ${it.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }} | " +
                        "${plain(it.amount)} | ${it.transactionCount}"
                )
            }
        }

        if (tags.isNotEmpty()) {
            appendLine()
            appendLine("SPEND_BY_TAG (tag | spend | transactions)")
            tags.forEach {
                appendLine("- ${it.tag} | ${plain(it.amount)} | ${it.transactionCount}")
            }
        }

        if (!refunds.isEmpty) {
            appendLine()
            appendLine("REFUNDS_AND_REIMBURSEMENTS")
            appendLine("total_refunds: ${plain(refunds.total)}")
            appendLine("matched_refunds: ${plain(refunds.matched.amount)} (${refunds.matched.transactionCount})")
            appendLine("unmatched_refunds: ${plain(refunds.unmatched.amount)} (${refunds.unmatched.transactionCount})")
            refunds.byKind.forEach {
                appendLine(
                    "- ${it.kind.label} | ${plain(it.amount)} | ${it.transactionCount} | " +
                        "${it.matchStatus.label}"
                )
            }
            if (refunds.unmatchedDetails.isNotEmpty()) {
                appendLine("UNMATCHED_REFUNDS (date | amount | merchant)")
                refunds.unmatchedDetails.forEach {
                    appendLine("- ${it.date} | ${plain(it.amount)} | ${it.merchant}")
                }
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
            if (moved.monthly.isNotEmpty()) {
                appendLine("MONEY_MOVED_BY_MONTH (month | cc_bill_payments | self_transfers | transfers_to_others | investments | loan_outflow | loan_inflow)")
                moved.monthly.forEach {
                    appendLine(
                        "- ${it.label} | ${plain(it.creditCardBillPayments)} | ${plain(it.selfTransfers)} | " +
                            "${plain(it.transfersToOthers)} | ${plain(it.investments)} | " +
                            "${plain(it.loanLinkedOutflow)} | ${plain(it.loanLinkedInflow)}"
                    )
                }
            }
            if (moved.destinations.isNotEmpty()) {
                appendLine("MONEY_MOVED_DESTINATIONS (alias | bucket | amount | transactions)")
                moved.destinations.forEach {
                    appendLine(
                        "- ${it.alias} | ${it.bucket} | ${plain(it.amount)} | ${it.transactionCount}"
                    )
                }
            }
        }

        health?.let { health ->
            appendLine()
            appendLine("FINANCIAL_HEALTH")
            appendLine("months_in_period: ${health.months}")
            appendLine("total_income: ${plain(health.totalIncome)}")
            appendLine("average_monthly_income: ${plain(health.averageMonthlyIncome)}")
            appendLine("total_net_spending: ${plain(health.totalNetSpending)}")
            appendLine("average_monthly_spending: ${plain(health.averageMonthlySpending)}")
            appendLine("median_monthly_spending: ${plain(health.medianMonthlySpending)}")
            appendLine("average_monthly_savings: ${plain(health.averageMonthlySavings)}")
            appendLine("cash_savings: ${plain(health.cashSavings)}")
            appendLine("total_investments: ${plain(health.totalInvestments)}")
            appendLine("recurring_expense_total: ${plain(health.recurringExpenseTotal)}")
            health.savingsRatePercent?.let { appendLine("savings_rate_percent: $it") }
            health.essentialRatioPercent?.let { appendLine("essential_spending_ratio_percent: $it") }
            health.discretionaryRatioPercent?.let { appendLine("discretionary_spending_ratio_percent: $it") }
            health.fixedRatioPercent?.let { appendLine("fixed_expense_ratio_percent: $it") }
            health.investmentRatePercent?.let { appendLine("investment_rate_percent: $it") }
            health.lastMonthChangePercent?.let {
                val sign = if (it >= 0) "+" else ""
                appendLine("last_month_spend_change_percent: $sign${formatPercent(it)}")
            }
            appendLine("provident_fund_contribution: ${health.providentFundContribution?.let { plain(it) } ?: "unavailable"}")
            appendLine("net_worth_accumulation: ${health.netWorthAccumulation?.let { plain(it) } ?: "unavailable"}")
            if (health.spikes.isNotEmpty()) {
                appendLine("SPENDING_SPIKES (month | amount | times_median)")
                health.spikes.forEach {
                    appendLine("- ${it.month} | ${plain(it.amount)} | ${formatPercent(it.timesMedian)}")
                }
            }
        }

        appendLine()
        appendLine("DATA_QUALITY")
        appendLine("reporting_period: ${dataQuality.periodStart} to ${dataQuality.periodEnd} (${dataQuality.periodDays} days)")
        appendLine("reporting_months: ${dataQuality.periodMonths} (${dataQuality.partialMonths} partial)")
        appendLine("source_transactions: ${dataQuality.sourceTransactions}")
        appendLine("included_transactions: ${dataQuality.includedTransactions}")
        appendLine("excluded_transactions: ${dataQuality.excludedTransactions}")
        appendLine("duplicate_transactions: ${dataQuality.duplicateTransactions}")
        appendLine("uncategorized_transactions: ${dataQuality.uncategorizedTransactions}")
        appendLine("unmatched_refunds: ${dataQuality.unmatchedRefundCount} (${plain(dataQuality.unmatchedRefundAmount)})")
        appendLine("transactions_with_unknown_payment_method: ${dataQuality.unknownPaymentMethodCount}")
        appendLine("transactions_missing_merchant: ${dataQuality.missingMerchantCount}")
        if (dataQuality.reversalTransactions > 0) {
            appendLine("reversal_transactions: ${dataQuality.reversalTransactions} (${plain(dataQuality.reversalAmount)})")
        }
        appendLine("summary_to_transactions_difference: ${plain(dataQuality.summaryToTransactionsDifference)}")
        appendLine("category_to_summary_difference: ${plain(dataQuality.categoryToSummaryDifference)} (equals the unmatched refunds, which cannot be attributed to a category)")
        if (dataQuality.categoriesOmitted > 0) {
            appendLine("categories_not_listed: ${dataQuality.categoriesOmitted}")
        }
        if (dataQuality.merchantsOmitted > 0) {
            appendLine("merchants_not_listed: ${dataQuality.merchantsOmitted}")
        }
        if (dataQuality.monthlyCategoryRowsOmitted > 0) {
            appendLine("monthly_category_rows_not_listed: ${dataQuality.monthlyCategoryRowsOmitted}")
        }
    }

    private fun plain(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** Locale-independent percentage so the model always reads a dot decimal separator. */
    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.1f", value)
}

/** Net spending of one category, with the refunds that were netted into it. */
data class CategorySpend(
    val category: String,
    val grossExpense: BigDecimal,
    val refundsReceived: BigDecimal,
    val netExpense: BigDecimal,
    val shareOfNetSpendingPercent: Double,
    val transactionCount: Int,
    val essentiality: Essentiality,
    val behaviour: ExpenseBehaviour
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
    /** Amount of the original expense. */
    val grossAmount: BigDecimal,
    /** Amount refunded against it. */
    val refundedAmount: BigDecimal,
    /** Amount actually spent after refunds. */
    val netAmount: BigDecimal,
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
    val loanLinkedInflow: FlowSummary = FlowSummary.EMPTY,
    /** Same buckets per calendar month. */
    val monthly: List<MonthMoneyMoved> = emptyList(),
    /** Where the money went, using privacy-safe account aliases. */
    val destinations: List<MoneyDestination> = emptyList()
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

/** One privacy-safe destination of a money-movement bucket. */
data class MoneyDestination(
    val alias: String,
    val bucket: String,
    val amount: BigDecimal,
    val transactionCount: Int
)

/** Refunds and reimbursements, split by kind and by whether an original was found. */
data class RefundSummary(
    val total: BigDecimal = BigDecimal.ZERO,
    val matched: FlowSummary = FlowSummary.EMPTY,
    val unmatched: FlowSummary = FlowSummary.EMPTY,
    val byKind: List<RefundKindSpend> = emptyList(),
    val unmatchedDetails: List<UnmatchedRefund> = emptyList()
) {
    val isEmpty: Boolean get() = total.signum() == 0 && matched.transactionCount == 0
}

/** One refund kind with the amount and the match status it was reported under. */
data class RefundKindSpend(
    val kind: RefundKind,
    val amount: BigDecimal,
    val transactionCount: Int,
    val matchStatus: RefundMatchStatus
)

/** A refund that could not be tied to an original inside the reporting period. */
data class UnmatchedRefund(
    val date: LocalDate,
    val amount: BigDecimal,
    val merchant: String
)
