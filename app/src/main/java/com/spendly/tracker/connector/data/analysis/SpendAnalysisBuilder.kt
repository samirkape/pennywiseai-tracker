package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.CategorySpend
import com.spendly.tracker.connector.domain.MerchantSpend
import com.spendly.tracker.connector.domain.MonthSpend
import com.spendly.tracker.connector.domain.NamedExpense
import com.spendly.tracker.connector.domain.RecurringSpend
import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.TagSpend
import com.spendly.tracker.connector.domain.WeekdaySpend
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure aggregation of transactions into a [SpendAnalysis].
 *
 * Kept free of Android and database types so the numbers can be unit tested.
 */
object SpendAnalysisBuilder {

    fun build(
        transactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity>,
        window: AnalysisWindow,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        today: LocalDate = endDate
    ): SpendAnalysis {
        val expenses = transactions.filter(::isSpend)
        val incomes = transactions.filter { it.transactionType == TransactionType.INCOME }

        val totalExpense = expenses.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
        val totalIncome = incomes.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
        val previousExpense = previousTransactions
            .filter(::isSpend)
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val dayCount = window.dayCount(today)
        val dailyAverage = if (dayCount > 0) {
            totalExpense.divide(BigDecimal(dayCount), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val changePercent = if (previousExpense.signum() > 0) {
            totalExpense.subtract(previousExpense)
                .multiply(BigDecimal(100))
                .divide(previousExpense, 1, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            null
        }

        return SpendAnalysis(
            window = window,
            startDate = startDate,
            endDate = endDate,
            currency = currency,
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            expenseCount = expenses.size,
            incomeCount = incomes.size,
            dailyAverage = dailyAverage,
            categories = buildCategories(expenses, totalExpense),
            merchants = buildMerchants(expenses),
            monthlyTrend = buildMonthlyTrend(transactions),
            recurring = buildRecurring(expenses),
            largestExpenses = buildLargestExpenses(expenses),
            weekdaySpend = buildWeekdaySpend(expenses),
            previousExpense = previousExpense.takeIf { it.signum() > 0 },
            previousExpenseChangePercent = changePercent,
            tags = buildTags(expenses)
        )
    }

    /** Credit card purchases count as spend, transfers and investments do not. */
    fun isSpend(transaction: TransactionEntity): Boolean =
        transaction.transactionType == TransactionType.EXPENSE ||
            transaction.transactionType == TransactionType.CREDIT

    private fun buildCategories(
        expenses: List<TransactionEntity>,
        totalExpense: BigDecimal
    ): List<CategorySpend> {
        if (expenses.isEmpty()) return emptyList()
        return expenses
            .groupBy { it.category.takeIf { c -> c.isNotBlank() } ?: UNCATEGORISED }
            .map { (category, txs) ->
                val amount = txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
                val share = if (totalExpense.signum() > 0) {
                    amount.multiply(BigDecimal(100))
                        .divide(totalExpense, 1, RoundingMode.HALF_UP)
                        .toDouble()
                } else {
                    0.0
                }
                CategorySpend(category, amount, share, txs.size)
            }
            .sortedByDescending { it.amount }
            .take(MAX_CATEGORIES)
    }

    private fun buildMerchants(expenses: List<TransactionEntity>): List<MerchantSpend> =
        expenses
            .groupBy { it.merchantName.ifBlank { UNKNOWN_MERCHANT } }
            .map { (merchant, txs) ->
                MerchantSpend(
                    merchantName = merchant,
                    amount = txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount },
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { it.amount }
            .take(MAX_MERCHANTS)

    private fun buildMonthlyTrend(transactions: List<TransactionEntity>): List<MonthSpend> {
        if (transactions.isEmpty()) return emptyList()
        val months = transactions.map { YearMonth.from(it.dateTime.toLocalDate()) }.toSortedSet()
        return months.map { month ->
            val inMonth = transactions.filter { YearMonth.from(it.dateTime.toLocalDate()) == month }
            MonthSpend(
                month = month,
                expense = inMonth.filter(::isSpend).fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount },
                income = inMonth.filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
            )
        }
    }

    private fun buildRecurring(expenses: List<TransactionEntity>): List<RecurringSpend> =
        expenses
            .groupBy { it.merchantName.ifBlank { UNKNOWN_MERCHANT } }
            .filterValues { it.size >= MIN_RECURRING_OCCURRENCES }
            .map { (merchant, txs) ->
                val amount = txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
                RecurringSpend(
                    merchantName = merchant,
                    occurrences = txs.size,
                    amount = amount,
                    typicalAmount = amount.divide(BigDecimal(txs.size), 2, RoundingMode.HALF_UP)
                )
            }
            .sortedWith(compareByDescending<RecurringSpend> { it.occurrences }.thenByDescending { it.amount })
            .take(MAX_RECURRING)

    private fun buildLargestExpenses(expenses: List<TransactionEntity>): List<NamedExpense> =
        expenses
            .sortedByDescending { it.amount }
            .take(MAX_LARGEST)
            .map {
                NamedExpense(
                    name = it.merchantName.ifBlank { UNKNOWN_MERCHANT },
                    amount = it.amount,
                    category = it.category.takeIf { c -> c.isNotBlank() } ?: UNCATEGORISED,
                    date = it.dateTime.toLocalDate()
                )
            }

    private fun buildWeekdaySpend(expenses: List<TransactionEntity>): List<WeekdaySpend> =
        DayOfWeek.entries.map { day ->
            val matching = expenses.filter { it.dateTime.dayOfWeek == day }
            WeekdaySpend(
                dayOfWeek = day,
                amount = matching.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount },
                transactionCount = matching.size
            )
        }

    private fun buildTags(expenses: List<TransactionEntity>): List<TagSpend> =
        expenses
            .flatMap { tx ->
                tx.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { it to tx.amount }
            }
            .groupBy({ it.first }, { it.second })
            .map { (tag, amounts) ->
                TagSpend(
                    tag = tag,
                    amount = amounts.fold(BigDecimal.ZERO) { acc, value -> acc + value },
                    transactionCount = amounts.size
                )
            }
            .sortedByDescending { it.amount }
            .take(MAX_TAGS)

    private const val MAX_CATEGORIES = 12
    private const val MAX_MERCHANTS = 12
    private const val MAX_RECURRING = 8
    private const val MAX_LARGEST = 6
    private const val MAX_TAGS = 8
    private const val MIN_RECURRING_OCCURRENCES = 3
    private const val UNCATEGORISED = "Uncategorised"
    private const val UNKNOWN_MERCHANT = "Unknown"
}
