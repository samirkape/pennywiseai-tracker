package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.time.YearMonth

/**
 * One calendar month of the reporting period.
 *
 * Every month between the period start and end is present, even when it holds no
 * transactions at all, so the model can tell "nothing happened" apart from
 * "the month is missing". Months that the period only partly covers are flagged.
 */
data class MonthBreakdown(
    val month: YearMonth,
    val label: String,
    /** True when the reporting period covers only part of this calendar month. */
    val isPartial: Boolean,
    /** Days of this month that fall inside the reporting period. */
    val daysInPeriod: Int,
    /** Money received that is not a refund, a loan movement or a transfer. */
    val income: BigDecimal,
    /** Spending before refunds. */
    val grossExpense: BigDecimal,
    /** Money received back during this month. */
    val refunds: BigDecimal,
    /** Gross spending minus refunds. */
    val netExpense: BigDecimal,
    /** Income minus net spending. */
    val netCashFlow: BigDecimal,
    val savingsRatePercent: Double?,
    /** Allocation to investments. */
    val investments: BigDecimal,
    /** Money that left the account without being spent. */
    val transfersOut: BigDecimal,
    /** Money that arrived as a transfer rather than as income. */
    val transfersIn: BigDecimal,
    /** Borrowed money received, or repayments received on money lent out. */
    val loanInflow: BigDecimal,
    /** Repayments paid, or money lent out. */
    val loanOutflow: BigDecimal,
    /** Net spending per category for this month. */
    val categories: List<CategoryMonthSpend>
)

/** Net and gross spend of one category inside one month. */
data class CategoryMonthSpend(
    val category: String,
    val grossExpense: BigDecimal,
    val refundsReceived: BigDecimal,
    val netExpense: BigDecimal,
    val transactionCount: Int
)

/** Money that moved without being spent, broken down per month. */
data class MonthMoneyMoved(
    val label: String,
    val creditCardBillPayments: BigDecimal,
    val selfTransfers: BigDecimal,
    val transfersToOthers: BigDecimal,
    val investments: BigDecimal,
    val loanLinkedOutflow: BigDecimal,
    val loanLinkedInflow: BigDecimal
)
