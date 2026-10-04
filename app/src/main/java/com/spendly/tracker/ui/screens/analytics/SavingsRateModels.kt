package com.spendly.tracker.ui.screens.analytics

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.presentation.common.matchesAnalyticsSpendingFilter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Month windows offered by the savings-rate card. */
val SAVINGS_RATE_WINDOW_OPTIONS = listOf(3, 6, 12)
const val SAVINGS_RATE_DEFAULT_WINDOW = 6

/** One bucket (calendar or pay month) of income vs. spending. */
data class MonthlySavingsRate(
    val month: YearMonth,
    val start: LocalDate,
    val end: LocalDate,
    val income: BigDecimal,
    val spent: BigDecimal,
    /** True for the bucket that is still in progress. */
    val isCurrent: Boolean,
) {
    val saved: BigDecimal get() = income - spent

    /** Percent of income saved; null when the month has no income. Negative when overspent. */
    val ratePercent: Float?
        get() = if (income.signum() > 0) {
            saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
        } else {
            null
        }
}

data class SavingsRateSummary(
    val months: List<MonthlySavingsRate> = emptyList(),
    val windowMonths: Int = SAVINGS_RATE_DEFAULT_WINDOW,
    val currency: String = "INR",
) {
    /** Months that have income, so a rate can be computed. */
    val ratedMonths: List<MonthlySavingsRate> get() = months.filter { it.ratePercent != null }

    /** Window rate = total saved / total income, not a mean of monthly percentages. */
    val averagePercent: Float?
        get() {
            val rated = ratedMonths
            val income = rated.fold(BigDecimal.ZERO) { acc, m -> acc + m.income }
            if (income.signum() <= 0) return null
            val saved = rated.fold(BigDecimal.ZERO) { acc, m -> acc + m.saved }
            return saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
        }

    val hasData: Boolean get() = ratedMonths.isNotEmpty()

    /** Date range covered by [months], used to tell whether the page is showing the same window. */
    val range: Pair<LocalDate, LocalDate>?
        get() = if (months.isEmpty()) null else months.minOf { it.start } to months.maxOf { it.end }
}

/**
 * Buckets [transactions] into [ranges] (oldest first) and computes income vs. spending.
 * Spending excludes loan repayments, investments and transfers; excluded rows are ignored.
 * [amountOf] lets the caller convert currencies.
 */
suspend fun computeMonthlySavingsRates(
    transactions: List<TransactionEntity>,
    ranges: List<Pair<YearMonth, Pair<LocalDate, LocalDate>>>,
    currentMonth: YearMonth,
    amountOf: suspend (TransactionEntity) -> BigDecimal,
): List<MonthlySavingsRate> {
    val tracked = transactions.filter { !it.isExcludedFromTracking }
    return ranges.map { (month, range) ->
        val (start, end) = range
        var income = BigDecimal.ZERO
        var spent = BigDecimal.ZERO
        for (tx in tracked) {
            val date = tx.dateTime.toLocalDate()
            if (date.isBefore(start) || date.isAfter(end)) continue
            when {
                tx.transactionType == TransactionType.INCOME && tx.loanId == null -> income += amountOf(tx)
                tx.matchesAnalyticsSpendingFilter() -> spent += amountOf(tx)
            }
        }
        MonthlySavingsRate(
            month = month,
            start = start,
            end = end,
            income = income,
            spent = spent,
            isCurrent = month == currentMonth,
        )
    }
}
