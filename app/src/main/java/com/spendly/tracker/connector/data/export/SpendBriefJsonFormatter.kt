package com.spendly.tracker.connector.data.export

import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.TransactionBrief
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The same brief as a JSON document, for tools that want to compute on the data
 * instead of reading a text file.
 *
 * The structure mirrors the plain-text brief: same section names, same field
 * names, same numbers. Nothing that is privacy safe in the text file becomes
 * visible here.
 */
object SpendBriefJsonFormatter {

    fun format(
        analysis: SpendAnalysis,
        transactions: List<TransactionBrief> = emptyList(),
        generatedAt: LocalDateTime = LocalDateTime.now()
    ): String = buildJsonObject {
        put("format", "spendly-spending-brief")
        put("version", 2)
        put("generated_at", generatedAt.format(TIMESTAMP))
        put("currency", analysis.currency)
        putJsonObject("period") {
            put("label", analysis.window.label())
            put("start", analysis.startDate.toString())
            put("end", analysis.endDate.toString())
        }
        putJsonObject("summary") {
            money("gross_expense", analysis.grossExpense)
            money("refunds_received", analysis.refundsReceived)
            money("net_expense", analysis.netExpense)
            money("total_income", analysis.totalIncome)
            money("net_cash_flow", analysis.netCashFlow)
            put("spend_transactions", analysis.expenseCount)
            put("income_transactions", analysis.incomeCount)
            put("refund_transactions", analysis.refundCount)
            money("average_daily_spend", analysis.dailyAverage)
            percent("savings_rate_percent", analysis.savingsRatePercent)
            analysis.previousExpense?.let { money("previous_period_net_spend", it) }
            percent("spend_change_vs_previous_percent", analysis.previousExpenseChangePercent)
        }
        putJsonArray("categories") {
            analysis.categories.forEach { category ->
                add(
                    buildJsonObject {
                        put("category", category.category)
                        money("gross_expense", category.grossExpense)
                        money("refunds_received", category.refundsReceived)
                        money("net_expense", category.netExpense)
                        put(
                            "share_of_net_spending_percent",
                            category.shareOfNetSpendingPercent
                        )
                        put("transactions", category.transactionCount)
                        put("essentiality", category.essentiality.label)
                        put("behaviour", category.behaviour.label)
                    }
                )
            }
        }
        putJsonArray("months") {
            analysis.months.forEach { month ->
                add(
                    buildJsonObject {
                        put("month", month.label)
                        put("partial", month.isPartial)
                        put("days_in_period", month.daysInPeriod)
                        money("income", month.income)
                        money("gross_expense", month.grossExpense)
                        money("refunds", month.refunds)
                        money("net_expense", month.netExpense)
                        money("net_cash_flow", month.netCashFlow)
                        percent("savings_rate_percent", month.savingsRatePercent)
                        money("investments", month.investments)
                        money("transfers_out", month.transfersOut)
                        money("transfers_in", month.transfersIn)
                        money("loan_inflow", month.loanInflow)
                        money("loan_outflow", month.loanOutflow)
                        putJsonArray("categories") {
                            month.categories.forEach { category ->
                                add(
                                    buildJsonObject {
                                        put("category", category.category)
                                        money("gross_expense", category.grossExpense)
                                        money("refunds_received", category.refundsReceived)
                                        money("net_expense", category.netExpense)
                                        put("transactions", category.transactionCount)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
        putJsonObject("refunds") {
            money("total", analysis.refunds.total)
            money("matched_amount", analysis.refunds.matched.amount)
            put("matched_transactions", analysis.refunds.matched.transactionCount)
            money("unmatched_amount", analysis.refunds.unmatched.amount)
            put("unmatched_transactions", analysis.refunds.unmatched.transactionCount)
            putJsonArray("by_kind") {
                analysis.refunds.byKind.forEach { kind ->
                    add(
                        buildJsonObject {
                            put("kind", kind.kind.label)
                            money("amount", kind.amount)
                            put("transactions", kind.transactionCount)
                            put("match_status", kind.matchStatus.label)
                        }
                    )
                }
            }
            putJsonArray("unmatched") {
                analysis.refunds.unmatchedDetails.forEach { refund ->
                    add(
                        buildJsonObject {
                            put("date", refund.date.toString())
                            money("amount", refund.amount)
                            put("merchant", refund.merchant)
                        }
                    )
                }
            }
        }
        analysis.moneyMoved?.takeIf { !it.isEmpty }?.let { moved ->
            putJsonObject("money_moved") {
                money("total_outflow", moved.outflowTotal)
                money("credit_card_bill_payments", moved.creditCardBillPayments.amount)
                money("transfers_to_own_accounts", moved.selfTransfers.amount)
                money("transfers_to_others", moved.transfersToOthers.amount)
                money("investments", moved.investments.amount)
                money("loan_linked_outflow", moved.loanLinkedOutflow.amount)
                money("loan_linked_inflow", moved.loanLinkedInflow.amount)
                putJsonArray("by_month") {
                    moved.monthly.forEach { month ->
                        add(
                            buildJsonObject {
                                put("month", month.label)
                                money("credit_card_bill_payments", month.creditCardBillPayments)
                                money("self_transfers", month.selfTransfers)
                                money("transfers_to_others", month.transfersToOthers)
                                money("investments", month.investments)
                                money("loan_linked_outflow", month.loanLinkedOutflow)
                                money("loan_linked_inflow", month.loanLinkedInflow)
                            }
                        )
                    }
                }
            }
        }
        analysis.health?.let { health ->
            putJsonObject("financial_health") {
                put("months_in_period", health.months)
                money("total_income", health.totalIncome)
                money("average_monthly_income", health.averageMonthlyIncome)
                money("total_net_spending", health.totalNetSpending)
                money("average_monthly_spending", health.averageMonthlySpending)
                money("median_monthly_spending", health.medianMonthlySpending)
                money("average_monthly_savings", health.averageMonthlySavings)
                money("cash_savings", health.cashSavings)
                money("total_investments", health.totalInvestments)
                money("recurring_expense_total", health.recurringExpenseTotal)
                percent("savings_rate_percent", health.savingsRatePercent)
                percent("essential_spending_ratio_percent", health.essentialRatioPercent)
                percent("discretionary_spending_ratio_percent", health.discretionaryRatioPercent)
                percent("fixed_expense_ratio_percent", health.fixedRatioPercent)
                percent("investment_rate_percent", health.investmentRatePercent)
                percent(
                    "last_complete_month_spend_change_percent",
                    health.lastMonthChangePercent
                )
                if (health.providentFundContribution == null) {
                    put("provident_fund_contribution", JsonNull)
                } else {
                    money("provident_fund_contribution", health.providentFundContribution)
                }
                if (health.netWorthAccumulation == null) {
                    put("net_worth_accumulation", JsonNull)
                } else {
                    money("net_worth_accumulation", health.netWorthAccumulation)
                }
                putJsonArray("spending_spikes") {
                    health.spikes.forEach { spike ->
                        add(
                            buildJsonObject {
                                put("month", spike.month)
                                money("amount", spike.amount)
                                put("times_median", spike.timesMedian)
                            }
                        )
                    }
                }
            }
        }
        putJsonObject("data_quality") {
            put("reporting_period_start", analysis.dataQuality.periodStart.toString())
            put("reporting_period_end", analysis.dataQuality.periodEnd.toString())
            put("reporting_period_days", analysis.dataQuality.periodDays)
            put("reporting_months", analysis.dataQuality.periodMonths)
            put("partial_months", analysis.dataQuality.partialMonths)
            put("source_transactions", analysis.dataQuality.sourceTransactions)
            put("included_transactions", analysis.dataQuality.includedTransactions)
            put("excluded_transactions", analysis.dataQuality.excludedTransactions)
            put("duplicate_transactions", analysis.dataQuality.duplicateTransactions)
            put("uncategorized_transactions", analysis.dataQuality.uncategorizedTransactions)
            put("unmatched_refunds", analysis.dataQuality.unmatchedRefundCount)
            money("unmatched_refund_amount", analysis.dataQuality.unmatchedRefundAmount)
            put(
                "transactions_with_unknown_payment_method",
                analysis.dataQuality.unknownPaymentMethodCount
            )
            put("transactions_missing_merchant", analysis.dataQuality.missingMerchantCount)
            money(
                "summary_to_transactions_difference",
                analysis.dataQuality.summaryToTransactionsDifference
            )
            money(
                "category_to_summary_difference",
                analysis.dataQuality.categoryToSummaryDifference
            )
            put("categories_not_listed", analysis.dataQuality.categoriesOmitted)
            put("merchants_not_listed", analysis.dataQuality.merchantsOmitted)
            put(
                "monthly_category_rows_not_listed",
                analysis.dataQuality.monthlyCategoryRowsOmitted
            )
        }
        putJsonArray("transactions") {
            transactions.forEach { row ->
                add(
                    buildJsonObject {
                        put("transaction_id", row.transactionId)
                        put("date", row.date.toString())
                        put("type", row.type.label)
                        money("amount", row.amount)
                        put("currency", row.currency)
                        put("category", row.category)
                        text("subcategory", row.subcategory)
                        put("merchant_original", row.merchantOriginal)
                        put("merchant_normalized", row.merchantNormalized)
                        put("payment_method", row.paymentMethod.label)
                        text("account_alias", row.accountAlias)
                        text("tag", row.tag)
                        text("linked_transaction_id", row.linkedTransactionId)
                        text("notes", row.notes)
                        put("essentiality", row.essentiality.label)
                        put("behaviour", row.behaviour.label)
                        text("refund_kind", row.refundKind?.label)
                        text("refund_status", row.refundMatchStatus?.label)
                    }
                )
            }
        }
    }.toString()

    /** Money always carries two decimals, never an exponent. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.money(key: String, value: BigDecimal) {
        put(key, JsonPrimitive(value.setScale(2, RoundingMode.HALF_UP).toPlainString()))
    }

    /** Percentages keep one decimal, matching the text brief. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.percent(key: String, value: Double?) {
        if (value == null) {
            put(key, JsonNull)
        } else {
            put(key, JsonPrimitive(String.format(Locale.US, "%.1f", value)))
        }
    }

    /** A text field that may be absent. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.text(key: String, value: String?) {
        if (value == null) {
            put(key, JsonNull)
        } else {
            put(key, JsonPrimitive(value))
        }
    }

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
}
