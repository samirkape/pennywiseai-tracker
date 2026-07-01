package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.InsightsRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.model.InsightConfidence
import com.spendly.tracker.domain.model.InsightType
import com.spendly.tracker.domain.model.SmartInsight
import com.spendly.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComputeInsightsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val insightsRepository: InsightsRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke() {
        val windowMonths = userPreferencesRepository.insightsDataWindowMonths.first()
        val startDate = if (windowMonths == -1) {
            LocalDateTime.of(1970, 1, 1, 0, 0)
        } else {
            LocalDateTime.now().minusMonths(windowMonths.toLong())
        }
        val endDate = LocalDateTime.now()

        val transactions = transactionRepository.getTransactionsBetweenDates(startDate, endDate).first()
        if (transactions.isEmpty()) return

        val insights = computeForMonth(
            transactions = transactions,
            targetMonth = YearMonth.now()
        )

        val totalCount = transactions.count { it.transactionType == TransactionType.EXPENSE }
        insights.forEach { insight ->
            insightsRepository.cacheInsight(insight, windowMonths, totalCount)
        }
    }

    suspend fun computeForMonth(targetMonth: YearMonth): List<SmartInsight> {
        val currentRange = targetMonth.atDay(1) to targetMonth.atEndOfMonth()
        val previousMonth = targetMonth.minusMonths(1)
        val previousRange = previousMonth.atDay(1) to previousMonth.atEndOfMonth()
        return computeForPeriod(
            anchorMonth = targetMonth,
            dateRange = currentRange,
            previousDateRange = previousRange,
        )
    }

    suspend fun computeForPeriod(
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): List<SmartInsight> {
        val comparisonStart = previousDateRange.first.atStartOfDay()
        val periodEndExclusive = dateRange.second.plusDays(1).atStartOfDay()
        val transactions = transactionRepository
            .getTransactionsBetweenDates(comparisonStart, periodEndExclusive)
            .first()

        return computeForPeriod(
            transactions = transactions,
            anchorMonth = anchorMonth,
            dateRange = dateRange,
            previousDateRange = previousDateRange,
        )
    }

    private fun computeForMonth(
        transactions: List<TransactionEntity>,
        targetMonth: YearMonth
    ): List<SmartInsight> {
        val currentRange = targetMonth.atDay(1) to targetMonth.atEndOfMonth()
        val previousMonth = targetMonth.minusMonths(1)
        val previousRange = previousMonth.atDay(1) to previousMonth.atEndOfMonth()
        return computeForPeriod(
            transactions = transactions,
            anchorMonth = targetMonth,
            dateRange = currentRange,
            previousDateRange = previousRange,
        )
    }

    private fun computeForPeriod(
        transactions: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): List<SmartInsight> {
        val expenses = transactions.filter { it.transactionType == TransactionType.EXPENSE }

        return listOfNotNull(
            generatePaceInsight(expenses, anchorMonth, dateRange),
            generateTopGrowingCategoryInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateTopMerchantsInsight(expenses, anchorMonth, dateRange),
            generateRecurringRatioInsight(expenses, anchorMonth, dateRange),
            generateSavingsWinInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateTopCategoriesInsight(expenses, anchorMonth, dateRange)
        )
    }

    private fun periodExpenses(
        expenses: List<TransactionEntity>,
        dateRange: Pair<LocalDate, LocalDate>,
    ): List<TransactionEntity> {
        val start = dateRange.first.atStartOfDay()
        val endExclusive = dateRange.second.plusDays(1).atStartOfDay()
        return expenses.filter { exp ->
            !exp.dateTime.isBefore(start) && exp.dateTime.isBefore(endExclusive)
        }
    }

    private fun periodMetadata(dateRange: Pair<LocalDate, LocalDate>): Map<String, String> {
        return mapOf(
            "startDate" to dateRange.first.toEpochDay().toString(),
            "endDate" to dateRange.second.toEpochDay().toString(),
            "period" to "CUSTOM"
        )
    }

    private fun generatePaceInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.isEmpty()) return null

        val totalSpend = thisMonthExpenses.sumOf { it.amount }
        val today = LocalDate.now()
        val isCurrentPeriod = !today.isBefore(dateRange.first) && !today.isAfter(dateRange.second)
        val elapsedDays = if (isCurrentPeriod) {
            ChronoUnit.DAYS.between(dateRange.first, today) + 1
        } else {
            ChronoUnit.DAYS.between(dateRange.first, dateRange.second) + 1
        }
        val dailyRate = totalSpend.divide(BigDecimal(elapsedDays), 2, RoundingMode.HALF_UP)

        val daysInPeriod = ChronoUnit.DAYS.between(dateRange.first, dateRange.second) + 1
        val projectedTotal = dailyRate.multiply(BigDecimal(daysInPeriod))

        return SmartInsight(
            id = "pace_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.PACE,
            title = if (isCurrentPeriod) "Period-end projection" else "Period spending pace",
            primaryValue = "₹${projectedTotal.toInt()}",
            secondaryText = "Based on ₹${dailyRate.toInt()}/day avg",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange)
        )
    }

    private fun generateSimpleAnomalyInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.size < 10) return null

        val mean = expenses.sumOf { it.amount }.divide(BigDecimal(expenses.size), 2, RoundingMode.HALF_UP)
        val threshold = mean.multiply(BigDecimal(5)) // 5x mean is definitely an anomaly

        val largeTxn = expenses.firstOrNull { it.amount > threshold } ?: return null

        return SmartInsight(
            id = "anomaly_${largeTxn.id}",
            type = InsightType.ANOMALY,
            title = "Unusual spend detected",
            primaryValue = "₹${largeTxn.amount.toInt()}",
            secondaryText = "at ${largeTxn.merchantName}",
            confidence = InsightConfidence.HIGH,
            metadata = mapOf(
                "merchant" to largeTxn.merchantName,
                "startDate" to largeTxn.dateTime.toLocalDate().toEpochDay().toString(),
                "endDate" to largeTxn.dateTime.toLocalDate().toEpochDay().toString(),
                "period" to "CUSTOM"
            )
        )
    }

    private fun generateTopGrowingCategoryInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        if (expenses.size < 5) return null

        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        val lastMonthExpenses = periodExpenses(expenses, previousDateRange)

        if (thisMonthExpenses.isEmpty() || lastMonthExpenses.isEmpty()) return null

        // Group by category and sum
        val thisMonthByCategory = thisMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val lastMonthByCategory = lastMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

        // Find top 5 categories with growth, sorted by growth percentage
        val topGrowingCategories = thisMonthByCategory
            .mapNotNull { (category, thisMonthTotal) ->
                val lastMonthTotal = lastMonthByCategory[category] ?: BigDecimal.ZERO
                val growth = if (lastMonthTotal > BigDecimal.ZERO) {
                    ((thisMonthTotal - lastMonthTotal) / lastMonthTotal * BigDecimal(100)).toInt()
                } else if (thisMonthTotal > BigDecimal.ZERO) {
                    100 // New category
                } else {
                    0
                }
                if (growth > 0) {
                    Triple(category, growth, thisMonthTotal.toInt())
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .take(5)

        if (topGrowingCategories.isEmpty()) return null

        val topCategory = topGrowingCategories.first()
        val allCategoriesData = topGrowingCategories.joinToString("|") { "${it.first}:${it.second}%:₹${it.third}" }

        return SmartInsight(
            id = "top_grower_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.TOP_GROWER,
            title = "Top growing categories",
            primaryValue = "${topCategory.second}%",
            secondaryText = "${topCategory.first} — ₹${topCategory.third}",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "category" to topCategory.first,
                "topItems" to allCategoriesData
            )
        )
    }

    private fun generateTopMerchantsInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        if (expenses.size < 5) return null

        val thisMonthExpenses = periodExpenses(expenses, dateRange)

        if (thisMonthExpenses.isEmpty()) return null

        // Group by merchant and sum, get top 5
        val topMerchants = thisMonthExpenses
            .groupingBy { it.merchantName }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        if (topMerchants.isEmpty()) return null

        val topMerchant = topMerchants.first()
        val merchantsData = topMerchants.joinToString("|") { "${it.first}:₹${it.second.toInt()}" }
        val totalSpent = topMerchants.sumOf { it.second }

        return SmartInsight(
            id = "top_merchants_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.MERCHANT_JUMP,
            title = "Top 5 merchants this period",
            primaryValue = "₹${topMerchant.second.toInt()}",
            secondaryText = "${topMerchant.first} — Total: ₹${totalSpent.toInt()}",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "merchant" to topMerchant.first,
                "topItems" to merchantsData
            )
        )
    }

    private fun generateRecurringRatioInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        if (expenses.size < 5) return null

        val thisMonthExpenses = periodExpenses(expenses, dateRange)

        if (thisMonthExpenses.isEmpty()) return null

        // Count transactions with same merchant appearing multiple times (simple recurring detection)
        val merchantCounts = thisMonthExpenses.groupingBy { it.merchantName }.eachCount()
        val recurringTransactions = thisMonthExpenses.filter { merchantCounts[it.merchantName] ?: 0 > 1 }
        val recurringMerchants = merchantCounts.filter { it.value > 1 }.toList().sortedByDescending { it.second }.take(5)

        val recurringAmount = recurringTransactions.sumOf { it.amount }
        val totalAmount = thisMonthExpenses.sumOf { it.amount }

        val recurringPercent = if (totalAmount > BigDecimal.ZERO) {
            (recurringAmount / totalAmount * BigDecimal(100)).toInt()
        } else {
            0
        }

        if (recurringPercent < 10) return null // Only show if meaningful

        val recurringData = recurringMerchants.joinToString("|") { "${it.first}:${it.second}x" }

        return SmartInsight(
            id = "recurring_ratio_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.RECURRING_RATIO,
            title = "Your recurring subscriptions",
            primaryValue = "$recurringPercent%",
            secondaryText = "₹${recurringAmount.toInt()} of ₹${totalAmount.toInt()} from ${recurringMerchants.size} merchants",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange) + mapOf(
                "topItems" to recurringData
            )
        )
    }

    private fun generateSavingsWinInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        if (expenses.size < 5) return null

        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        val lastMonthExpenses = periodExpenses(expenses, previousDateRange)

        if (thisMonthExpenses.isEmpty() || lastMonthExpenses.isEmpty()) return null

        // Group by category
        val thisMonthByCategory = thisMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val lastMonthByCategory = lastMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

        // Find categories with savings (decrease), top 5
        val categoriesWithSavings = thisMonthByCategory
            .mapNotNull { (category, thisMonthTotal) ->
                val lastMonthTotal = lastMonthByCategory[category] ?: BigDecimal.ZERO
                val savings = lastMonthTotal - thisMonthTotal // Positive = savings
                if (savings > BigDecimal.ZERO) {
                    val decreasePercent = (savings / lastMonthTotal * BigDecimal(100)).toInt()
                    Pair(Pair(category, decreasePercent), savings)
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .take(5)

        if (categoriesWithSavings.isEmpty()) return null

        val topSaving = categoriesWithSavings.first()
        val savingsData = categoriesWithSavings.joinToString("|") { "${it.first.first}:↓${it.first.second}%:₹${it.second.toInt()}" }

        return SmartInsight(
            id = "savings_win_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.SAVINGS_WIN,
            title = "Your biggest savings",
            primaryValue = "↓ ${topSaving.first.second}%",
            secondaryText = "${topSaving.first.first} — saved ₹${topSaving.second.toInt()} — great job!",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange) + mapOf(
                "category" to topSaving.first.first,
                "topItems" to savingsData
            )
        )
    }

    private fun generateTopCategoriesInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        if (expenses.size < 5) return null

        val thisMonthExpenses = periodExpenses(expenses, dateRange)

        if (thisMonthExpenses.isEmpty()) return null

        // Get top 5 categories by spending
        val topCategories = thisMonthExpenses
            .groupingBy { it.category }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        if (topCategories.isEmpty()) return null

        val topCategory = topCategories.first()
        val totalSpent = topCategories.sumOf { it.second }
        val topCategoryPercent = (topCategory.second / totalSpent * BigDecimal(100)).toInt()
        val categoriesData = topCategories.joinToString("|") { "${it.first}:₹${it.second.toInt()}" }

        return SmartInsight(
            id = "top_categories_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.ANOMALY, // Using ANOMALY type as placeholder for top categories
            title = "Your top categories this period",
            primaryValue = "₹${topCategory.second.toInt()}",
            secondaryText = "${topCategory.first} at $topCategoryPercent% of ₹${totalSpent.toInt()}",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "category" to topCategory.first,
                "topItems" to categoriesData
            )
        )
    }
}
