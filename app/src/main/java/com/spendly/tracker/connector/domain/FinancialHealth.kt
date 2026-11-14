package com.spendly.tracker.connector.domain

import java.math.BigDecimal

/**
 * Health indicators derived from the reporting period.
 *
 * Every formula is stated next to the number it produces so an assistant can
 * re-check it instead of trusting it. Money that was not spent is *not* assumed
 * to be saved or invested: cash savings and investments are reported apart.
 */
data class FinancialHealth(
    /** Calendar months covered by the period, including months without transactions. */
    val months: Int,
    val totalIncome: BigDecimal,
    /** totalIncome / months. */
    val averageMonthlyIncome: BigDecimal,
    val totalNetSpending: BigDecimal,
    /** totalNetSpending / months. */
    val averageMonthlySpending: BigDecimal,
    /** Median of the monthly net spending figures. */
    val medianMonthlySpending: BigDecimal,
    /** (totalIncome - totalNetSpending) / months. */
    val averageMonthlySavings: BigDecimal,
    /** (totalIncome - totalNetSpending) / totalIncome * 100. */
    val savingsRatePercent: Double?,
    /** Essential net spending / total net spending * 100. */
    val essentialRatioPercent: Double?,
    /** Discretionary net spending / total net spending * 100. */
    val discretionaryRatioPercent: Double?,
    /** Fixed behaviour net spending / total net spending * 100. */
    val fixedRatioPercent: Double?,
    /** Investments / totalIncome * 100. */
    val investmentRatePercent: Double?,
    val totalInvestments: BigDecimal,
    /** Income minus net spending. Money left in the account; not proof of a saving habit. */
    val cashSavings: BigDecimal,
    /** Net spending of merchants that recur every period. */
    val recurringExpenseTotal: BigDecimal,
    val topCategories: List<CategorySpend>,
    /** Months whose spending stands out against the median of the other months. */
    val spikes: List<SpendingSpike>,
    /** Change of the last month against the month before it. */
    val lastMonthChangePercent: Double?,
    /** Provident fund contributions, when the source data carries them. */
    val providentFundContribution: BigDecimal?,
    /** Net worth change. Only filled when real asset and liability data exists. */
    val netWorthAccumulation: BigDecimal?
)

/** A month whose spending is far above the usual level. */
data class SpendingSpike(
    val month: String,
    val amount: BigDecimal,
    /** Spending divided by the median monthly spending. */
    val timesMedian: Double
)
