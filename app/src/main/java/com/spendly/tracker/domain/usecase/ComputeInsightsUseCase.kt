package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.InsightsRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.model.InsightConfidence
import com.spendly.tracker.domain.model.InsightScope
import com.spendly.tracker.domain.model.InsightType
import com.spendly.tracker.domain.model.SmartInsight
import com.spendly.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
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
    companion object {
        // A near-zero previous-period total can blow the growth ratio up to absurd
        // figures (e.g. 200,000%) even though the underlying amounts are tiny. Cap it
        // so the headline stays a believable "big jump" rather than a nonsense number.
        private const val MAX_CATEGORY_GROWTH_PERCENT = 999

        // A ₹100 category that doubled shows a huge % but is noise next to a ₹5,000
        // category that grew modestly — require some minimum share of this month's
        // spend before a category is even eligible to be ranked by growth rate.
        private const val MIN_CATEGORY_SPEND_FLOOR = 200
        private val MIN_CATEGORY_SPEND_SHARE = BigDecimal("0.03")
    }

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

        // Lifetime insights are period-agnostic: computed once against full history and cached
        // under stable keys so they don't recompute (or get treated as stale) when the user
        // flips the month selector in InsightsScreen.
        val lifetimeInsights = computeLifetime()
        lifetimeInsights.forEach { insight ->
            insightsRepository.cacheInsight(insight, dataWindowMonths = -1, transactionCount = totalCount)
        }
    }

    /** Computes all-time (lifetime) insights, independent of the currently selected month. */
    suspend fun computeLifetime(): List<SmartInsight> {
        val allTransactions = transactionRepository.getAllTransactionsList()
        if (allTransactions.isEmpty()) return emptyList()
        return computeLifetimeInsights(allTransactions)
    }

    private fun computeLifetimeInsights(transactions: List<TransactionEntity>): List<SmartInsight> {
        val expenses = transactions.filter {
            (it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT) &&
                it.loanId == null
        }

        return listOfNotNull(
            generateLifetimeTotalSpendInsight(expenses),
            generateLifetimeTopMerchantInsight(expenses),
            generateLifetimeHighestMonthInsight(expenses),
            generateLifetimeAvgMonthlySpendInsight(expenses),
            generateLifetimeNoSpendStreakInsight(expenses),
            generateLifetimeMerchantLoyaltyInsight(expenses),
            generateLifetimeMemberSinceInsight(transactions),
        )
    }

    private fun lifetimeMetadata(transactions: List<TransactionEntity>): Map<String, String> {
        val earliest = transactions.minOfOrNull { it.dateTime.toLocalDate() } ?: LocalDate.now()
        return mapOf(
            "startDate" to earliest.toEpochDay().toString(),
            "endDate" to LocalDate.now().toEpochDay().toString(),
            "period" to "LIFETIME"
        )
    }

    /** Full rupee amount with thousand separators, e.g. ₹40,716. Safe for breakdown metadata since
     *  comma characters are stripped back out by [String.stripToFloat] when re-parsed for charts. */
    private fun rupee(amount: BigDecimal): String =
        CurrencyFormatter.formatCurrency(amount.setScale(0, RoundingMode.HALF_UP))

    /** Same as [rupee] but abbreviates amounts of ₹1L+ (e.g. ₹88.3L) — reserved for headline-scale
     *  single numbers (primaryValue/secondaryText), never for breakdown/topItems metadata, since
     *  mixing abbreviated and full-scale numbers in the same chart would break relative bar sizing. */
    private fun rupeeHeadline(amount: BigDecimal): String =
        if (amount.abs() >= BigDecimal(100_000)) {
            CurrencyFormatter.formatAbbreviated(amount.toDouble(), "INR")
        } else {
            rupee(amount)
        }

    private fun generateLifetimeTotalSpendInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.isEmpty()) return null
        val total = expenses.sumOf { it.amount }
        val firstMonth = YearMonth.from(expenses.minOf { it.dateTime })
        val monthsTracked = (ChronoUnit.MONTHS.between(firstMonth, YearMonth.now()) + 1).toInt()

        return SmartInsight(
            id = "lifetime_total_spend",
            type = InsightType.LIFETIME_TOTAL_SPEND,
            title = "Lifetime spend",
            primaryValue = rupeeHeadline(total),
            secondaryText = "Across $monthsTracked month${if (monthsTracked == 1) "" else "s"} of tracking",
            confidence = InsightConfidence.HIGH,
            metadata = lifetimeMetadata(expenses),
            scope = InsightScope.LIFETIME,
        )
    }

    private fun generateLifetimeTopMerchantInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.isEmpty()) return null
        val byMerchant = expenses.groupingBy { it.merchantName }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val (topMerchant, topTotal) = byMerchant.maxByOrNull { it.value } ?: return null
        val visitCount = expenses.count { it.merchantName == topMerchant }

        val topMerchants = byMerchant.toList().sortedByDescending { it.second }.take(5)
        val merchantsData = topMerchants.joinToString("|") { "${it.first}:${rupee(it.second)}" }

        return SmartInsight(
            id = "lifetime_top_merchant",
            type = InsightType.LIFETIME_TOP_MERCHANT,
            title = "All-time top merchant",
            primaryValue = topMerchant,
            secondaryText = "${rupeeHeadline(topTotal)} across $visitCount visits — all-time",
            confidence = InsightConfidence.HIGH,
            metadata = lifetimeMetadata(expenses) + mapOf(
                "merchant" to topMerchant,
                "topItems" to merchantsData
            ),
            scope = InsightScope.LIFETIME,
        )
    }

    private fun generateLifetimeHighestMonthInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.isEmpty()) return null
        val byMonth = expenses.groupingBy { YearMonth.from(it.dateTime) }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val (topMonth, topTotal) = byMonth.maxByOrNull { it.value } ?: return null
        val monthLabel = "${topMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${topMonth.year}"

        return SmartInsight(
            id = "lifetime_highest_month",
            type = InsightType.LIFETIME_HIGHEST_MONTH,
            title = "Highest spending month on record",
            primaryValue = rupeeHeadline(topTotal),
            secondaryText = "in $monthLabel",
            confidence = InsightConfidence.HIGH,
            metadata = lifetimeMetadata(expenses),
            scope = InsightScope.LIFETIME,
        )
    }

    private fun generateLifetimeAvgMonthlySpendInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.isEmpty()) return null
        val byMonth = expenses.groupingBy { YearMonth.from(it.dateTime) }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        if (byMonth.isEmpty()) return null
        val total = byMonth.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val avg = total.divide(BigDecimal(byMonth.size), 2, RoundingMode.HALF_UP)

        return SmartInsight(
            id = "lifetime_avg_monthly_spend",
            type = InsightType.LIFETIME_AVG_MONTHLY_SPEND,
            title = "All-time average monthly spend",
            primaryValue = rupeeHeadline(avg),
            secondaryText = "Based on ${byMonth.size} month${if (byMonth.size == 1) "" else "s"} of history — use as a baseline",
            confidence = InsightConfidence.MEDIUM,
            metadata = lifetimeMetadata(expenses),
            scope = InsightScope.LIFETIME,
        )
    }

    /** Walks every calendar day from the first recorded transaction to today looking for the longest gap with no spending. */
    private fun generateLifetimeNoSpendStreakInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.isEmpty()) return null
        val spendDays = expenses.map { it.dateTime.toLocalDate() }.toSortedSet()
        val firstDay = spendDays.first()
        val lastDay = LocalDate.now()

        var longestStreak = 0
        var currentStreak = 0
        var cursor = firstDay
        while (!cursor.isAfter(lastDay)) {
            if (cursor !in spendDays) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 0
            }
            cursor = cursor.plusDays(1)
        }
        if (longestStreak == 0) return null

        return SmartInsight(
            id = "lifetime_no_spend_streak",
            type = InsightType.LIFETIME_NO_SPEND_STREAK,
            title = "Longest no-spend streak",
            primaryValue = "$longestStreak day${if (longestStreak == 1) "" else "s"}",
            secondaryText = "Your longest run without any spending, ever recorded",
            confidence = InsightConfidence.MEDIUM,
            metadata = lifetimeMetadata(expenses),
            scope = InsightScope.LIFETIME,
        )
    }

    private fun generateLifetimeMerchantLoyaltyInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.size < 5) return null
        val merchantCounts = expenses
            .groupingBy { it.merchantName }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val topMerchant = merchantCounts.firstOrNull() ?: return null
        if (topMerchant.second < 3) return null

        val merchantTotal = expenses.filter { it.merchantName == topMerchant.first }.sumOf { it.amount }
        val countData = merchantCounts.joinToString("|") { "${it.first}:${it.second}x" }

        return SmartInsight(
            id = "lifetime_merchant_loyalty",
            type = InsightType.LIFETIME_MERCHANT_LOYALTY,
            title = "Most loyal merchant relationship",
            primaryValue = "${topMerchant.second} visits",
            secondaryText = "${topMerchant.first} — ${rupeeHeadline(merchantTotal)} total, all-time",
            confidence = InsightConfidence.HIGH,
            metadata = lifetimeMetadata(expenses) + mapOf(
                "merchant" to topMerchant.first,
                "topItems" to countData
            ),
            scope = InsightScope.LIFETIME,
        )
    }

    private fun generateLifetimeMemberSinceInsight(transactions: List<TransactionEntity>): SmartInsight? {
        if (transactions.isEmpty()) return null
        val firstDate = transactions.minOf { it.dateTime }.toLocalDate()
        val today = LocalDate.now()
        val daysTracked = ChronoUnit.DAYS.between(firstDate, today).toInt()
        if (daysTracked < 1) return null
        val monthsTracked = ChronoUnit.MONTHS.between(firstDate, today).toInt()

        val durationText = if (monthsTracked >= 1) {
            "$monthsTracked month${if (monthsTracked == 1) "" else "s"}"
        } else {
            "$daysTracked day${if (daysTracked == 1) "" else "s"}"
        }

        return SmartInsight(
            id = "lifetime_member_since",
            type = InsightType.LIFETIME_MEMBER_SINCE,
            title = "Tracking with Spendly",
            primaryValue = durationText,
            secondaryText = "Since $firstDate",
            confidence = InsightConfidence.HIGH,
            metadata = lifetimeMetadata(transactions),
            scope = InsightScope.LIFETIME,
        )
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
        val expenses = transactions.filter {
            (it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT) &&
                it.loanId == null
        }

        return listOfNotNull(
            generateCategoryAnomalyInsight(expenses, transactions, anchorMonth, dateRange)
                ?: generateSimpleAnomalyInsight(periodExpenses(expenses, dateRange)),
            generatePaceInsight(expenses, anchorMonth, dateRange),
            generateMonthlyComparisonInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateTopGrowingCategoryInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateTopCategoriesInsight(expenses, anchorMonth, dateRange),
            generateTopMerchantsInsight(expenses, anchorMonth, dateRange),
            generateLargestExpenseInsight(expenses, anchorMonth, dateRange),
            generateRecurringRatioInsight(expenses, anchorMonth, dateRange),
            generateSavingsWinInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateWeekendSpendInsight(expenses, anchorMonth, dateRange),
            generatePeakSpendDayInsight(expenses, anchorMonth, dateRange),
            generateZeroSpendDaysInsight(expenses, anchorMonth, dateRange),
            generateNewMerchantsInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateMerchantLoyaltyInsight(expenses, anchorMonth, dateRange),
            generateTransactionFrequencyInsight(expenses, anchorMonth, dateRange, previousDateRange),
            generateSpendSplitInsight(expenses, anchorMonth, dateRange),
            generateIncomeVsExpenseInsight(transactions, anchorMonth, dateRange),
            generateInvestmentRatioInsight(transactions, anchorMonth, dateRange),
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
            primaryValue = rupeeHeadline(projectedTotal),
            secondaryText = "Based on ${rupee(dailyRate)}/day avg",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange)
        )
    }

    private fun generateSimpleAnomalyInsight(expenses: List<TransactionEntity>): SmartInsight? {
        if (expenses.size < 10) return null

        val mean = expenses.sumOf { it.amount }.divide(BigDecimal(expenses.size), 2, RoundingMode.HALF_UP)
        val threshold = mean.multiply(BigDecimal(5))

        val largeTxn = expenses.firstOrNull { it.amount > threshold } ?: return null

        return SmartInsight(
            id = "anomaly_${largeTxn.id}",
            type = InsightType.ANOMALY,
            title = "Unusual spend detected",
            primaryValue = rupeeHeadline(largeTxn.amount),
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

    /**
     * Flags when a single category dominates total spend (>= 60%) and, when combined with
     * period income, results in a net cashflow deficit. Powers the "Insight Detail" drill-down
     * (narrative + contributing transactions) surfaced from the Insights list.
     */
    private fun generateCategoryAnomalyInsight(
        expenses: List<TransactionEntity>,
        allTransactions: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.size < 5) return null

        val totalSpend = thisMonthExpenses.sumOf { it.amount }
        if (totalSpend <= BigDecimal.ZERO) return null

        val byCategory = thisMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val (topCategory, topCategoryTotal) = byCategory.maxByOrNull { it.value } ?: return null

        val percent = (topCategoryTotal / totalSpend * BigDecimal(100)).toInt()
        if (percent < 60) return null

        val start = dateRange.first.atStartOfDay()
        val endExclusive = dateRange.second.plusDays(1).atStartOfDay()
        val income = allTransactions
            .filter {
                it.transactionType == TransactionType.INCOME &&
                    !it.dateTime.isBefore(start) && it.dateTime.isBefore(endExclusive)
            }
            .sumOf { it.amount }
        val deficit = income - totalSpend

        val topTransactionsInCategory = thisMonthExpenses
            .filter { it.category == topCategory }
            .sortedByDescending { it.amount }
            .take(10)

        val topMerchants = topTransactionsInCategory
            .groupingBy { it.merchantName }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        val merchantsData = topMerchants.joinToString("|") { "${it.first}:${rupee(it.second)}" }
        val txnIdsData = topTransactionsInCategory.joinToString(",") { it.id.toString() }

        return SmartInsight(
            id = "anomaly_category_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.ANOMALY,
            title = "$topCategory consumed ${rupeeHeadline(topCategoryTotal)} this period",
            primaryValue = "$percent%",
            secondaryText = "of total spend" + if (deficit < BigDecimal.ZERO) " — ${rupee(deficit.abs())} cashflow deficit" else "",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "category" to topCategory,
                "percent" to percent.toString(),
                "totalSpend" to totalSpend.toInt().toString(),
                "categoryTotal" to rupee(topCategoryTotal),
                "deficit" to deficit.toInt().toString(),
                "income" to income.toInt().toString(),
                "topItems" to merchantsData,
                "txnIds" to txnIdsData,
                "transactionCount" to thisMonthExpenses.size.toString(),
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

        val thisMonthByCategory = thisMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val lastMonthByCategory = lastMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

        val totalThisMonthSpend = thisMonthByCategory.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val minMeaningfulSpend = (totalThisMonthSpend * MIN_CATEGORY_SPEND_SHARE)
            .coerceAtLeast(BigDecimal(MIN_CATEGORY_SPEND_FLOOR))

        val topGrowingCategories = thisMonthByCategory
            .mapNotNull { (category, thisMonthTotal) ->
                if (thisMonthTotal < minMeaningfulSpend) return@mapNotNull null
                val lastMonthTotal = lastMonthByCategory[category] ?: BigDecimal.ZERO
                val growth = if (lastMonthTotal > BigDecimal.ZERO) {
                    ((thisMonthTotal - lastMonthTotal) / lastMonthTotal * BigDecimal(100))
                        .toInt()
                        .coerceAtMost(MAX_CATEGORY_GROWTH_PERCENT)
                } else if (thisMonthTotal > BigDecimal.ZERO) {
                    100
                } else {
                    0
                }
                if (growth > 0) Triple(category, growth, thisMonthTotal.toInt()) else null
            }
            .sortedByDescending { it.second }
            .take(5)

        if (topGrowingCategories.isEmpty()) return null

        val topCategory = topGrowingCategories.first()
        val topCategoryLastTotal = lastMonthByCategory[topCategory.first] ?: BigDecimal.ZERO
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
                "topItems" to allCategoriesData,
                "categoryLastTotal" to topCategoryLastTotal.toInt().toString(),
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

        val topMerchants = thisMonthExpenses
            .groupingBy { it.merchantName }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        if (topMerchants.isEmpty()) return null

        val topMerchant = topMerchants.first()
        val merchantsData = topMerchants.joinToString("|") { "${it.first}:${rupee(it.second)}" }
        val totalSpent = topMerchants.sumOf { it.second }

        return SmartInsight(
            id = "top_merchants_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.MERCHANT_JUMP,
            title = "Top 5 merchants this period",
            primaryValue = rupeeHeadline(topMerchant.second),
            secondaryText = "${topMerchant.first} — Total: ${rupee(totalSpent)}",
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

        val merchantCounts = thisMonthExpenses.groupingBy { it.merchantName }.eachCount()
        val recurringTransactions = thisMonthExpenses.filter { merchantCounts[it.merchantName] ?: 0 > 1 }
        val recurringMerchants = merchantCounts.filter { it.value > 1 }.toList().sortedByDescending { it.second }.take(5)

        val recurringAmount = recurringTransactions.sumOf { it.amount }
        val totalAmount = thisMonthExpenses.sumOf { it.amount }

        val recurringPercent = if (totalAmount > BigDecimal.ZERO) {
            (recurringAmount / totalAmount * BigDecimal(100)).toInt()
        } else 0

        if (recurringPercent < 10) return null

        val recurringData = recurringMerchants.joinToString("|") { "${it.first}:${it.second}x" }

        return SmartInsight(
            id = "recurring_ratio_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.RECURRING_RATIO,
            title = "Your recurring subscriptions",
            primaryValue = "$recurringPercent%",
            secondaryText = "${rupee(recurringAmount)} of ${rupee(totalAmount)} from ${recurringMerchants.size} merchants",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to recurringData)
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

        val thisMonthByCategory = thisMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val lastMonthByCategory = lastMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

        val categoriesWithSavings = thisMonthByCategory
            .mapNotNull { (category, thisMonthTotal) ->
                val lastMonthTotal = lastMonthByCategory[category] ?: BigDecimal.ZERO
                val savings = lastMonthTotal - thisMonthTotal
                if (savings > BigDecimal.ZERO) {
                    val decreasePercent = (savings / lastMonthTotal * BigDecimal(100)).toInt()
                    Pair(Pair(category, decreasePercent), savings)
                } else null
            }
            .sortedByDescending { it.second }
            .take(5)

        if (categoriesWithSavings.isEmpty()) return null

        val topSaving = categoriesWithSavings.first()
        val savingsData = categoriesWithSavings.joinToString("|") { "${it.first.first}:↓${it.first.second}%:${rupee(it.second)}" }

        return SmartInsight(
            id = "savings_win_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.SAVINGS_WIN,
            title = "Your biggest savings",
            primaryValue = "↓ ${topSaving.first.second}%",
            secondaryText = "${topSaving.first.first} — saved ${rupee(topSaving.second)} — great job!",
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
        val categoriesData = topCategories.joinToString("|") { "${it.first}:${rupee(it.second)}" }

        return SmartInsight(
            id = "top_categories_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.TOP_CATEGORIES,
            title = "Your top categories this period",
            primaryValue = rupeeHeadline(topCategory.second),
            secondaryText = "${topCategory.first} at $topCategoryPercent% of ${rupee(totalSpent)}",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "category" to topCategory.first,
                "topItems" to categoriesData
            )
        )
    }

    // ─── NEW INSIGHTS ──────────────────────────────────────────────────────────

    private fun generateLargestExpenseInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.size < 3) return null

        val largest = thisMonthExpenses.maxByOrNull { it.amount } ?: return null
        val avg = thisMonthExpenses.sumOf { it.amount }
            .divide(BigDecimal(thisMonthExpenses.size), 2, RoundingMode.HALF_UP)

        if (largest.amount < avg.multiply(BigDecimal(2))) return null

        val timesAvg = largest.amount.divide(avg, 1, RoundingMode.HALF_UP)

        return SmartInsight(
            id = "largest_expense_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.LARGEST_EXPENSE,
            title = "Biggest single expense",
            primaryValue = rupeeHeadline(largest.amount),
            secondaryText = "at ${largest.merchantName} — ${timesAvg}× your usual spend",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "merchant" to largest.merchantName,
                "category" to largest.category
            )
        )
    }

    private fun generateWeekendSpendInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.size < 6) return null

        val weekendExpenses = thisMonthExpenses.filter {
            it.dateTime.dayOfWeek == DayOfWeek.SATURDAY || it.dateTime.dayOfWeek == DayOfWeek.SUNDAY
        }
        val weekdayExpenses = thisMonthExpenses.filter {
            it.dateTime.dayOfWeek != DayOfWeek.SATURDAY && it.dateTime.dayOfWeek != DayOfWeek.SUNDAY
        }
        if (weekendExpenses.isEmpty() || weekdayExpenses.isEmpty()) return null

        var weekendDays = 0L; var weekdayDays = 0L
        var cur = dateRange.first
        while (!cur.isAfter(dateRange.second)) {
            if (cur.dayOfWeek == DayOfWeek.SATURDAY || cur.dayOfWeek == DayOfWeek.SUNDAY) weekendDays++
            else weekdayDays++
            cur = cur.plusDays(1)
        }

        val weekendDailyAvg = weekendExpenses.sumOf { it.amount }
            .divide(BigDecimal(weekendDays.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)
        val weekdayDailyAvg = weekdayExpenses.sumOf { it.amount }
            .divide(BigDecimal(weekdayDays.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)

        if (weekdayDailyAvg == BigDecimal.ZERO) return null

        val diff = ((weekendDailyAvg - weekdayDailyAvg) / weekdayDailyAvg * BigDecimal(100)).toInt()
        val absDiff = Math.abs(diff)
        if (absDiff < 10) return null

        val direction = if (diff > 0) "higher on weekends" else "lower on weekends"
        val dayItems = "Weekends:${rupee(weekendDailyAvg)}|Weekdays:${rupee(weekdayDailyAvg)}"

        return SmartInsight(
            id = "weekend_spend_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.WEEKEND_SPEND,
            title = "Weekend vs weekday spending",
            primaryValue = "$absDiff% $direction",
            secondaryText = "Weekend ${rupee(weekendDailyAvg)}/day vs ${rupee(weekdayDailyAvg)}/day weekdays",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to dayItems)
        )
    }

    private fun generatePeakSpendDayInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.size < 7) return null

        val byDayOfWeek = thisMonthExpenses
            .groupingBy { it.dateTime.dayOfWeek }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }

        if (byDayOfWeek.isEmpty()) return null

        val peakDay = byDayOfWeek.first()
        val lowestDay = byDayOfWeek.last()

        val dayNames = byDayOfWeek.joinToString("|") {
            val name = it.first.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)
            "$name:${rupee(it.second)}"
        }
        val peakDayName = peakDay.first.name.lowercase().replaceFirstChar { it.uppercase() }
        val lowestDayName = lowestDay.first.name.lowercase().replaceFirstChar { it.uppercase() }

        return SmartInsight(
            id = "peak_spend_day_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.PEAK_SPEND_DAY,
            title = "Peak spending day of week",
            primaryValue = peakDayName,
            secondaryText = "${rupee(peakDay.second)} total — lowest on $lowestDayName",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to dayNames)
        )
    }

    private fun generateZeroSpendDaysInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.isEmpty()) return null

        val today = LocalDate.now()
        val effectiveEnd = if (today.isBefore(dateRange.second)) today else dateRange.second
        val totalDays = ChronoUnit.DAYS.between(dateRange.first, effectiveEnd) + 1
        if (totalDays < 7) return null

        val daysWithSpend = thisMonthExpenses.map { it.dateTime.toLocalDate() }.toSet().size
        val zeroSpendDays = (totalDays - daysWithSpend).toInt()
        if (zeroSpendDays == 0) return null

        val zeroSpendPercent = (zeroSpendDays.toFloat() / totalDays * 100).toInt()

        return SmartInsight(
            id = "zero_spend_days_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.ZERO_SPEND_DAYS,
            title = "No-spend days this period",
            primaryValue = "$zeroSpendDays days",
            secondaryText = "$zeroSpendPercent% of the period — $daysWithSpend days with spending",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange)
        )
    }

    private fun generateNewMerchantsInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        val lastMonthExpenses = periodExpenses(expenses, previousDateRange)
        if (thisMonthExpenses.isEmpty()) return null

        val previousMerchants = lastMonthExpenses.map { it.merchantName }.toSet()
        val newMerchants = thisMonthExpenses
            .filter { it.merchantName !in previousMerchants }
            .groupingBy { it.merchantName }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }

        if (newMerchants.isEmpty()) return null

        val topNew = newMerchants.take(5)
        val merchantData = topNew.joinToString("|") { "${it.first}:${rupee(it.second)}" }
        val totalNewSpend = newMerchants.sumOf { it.second }

        return SmartInsight(
            id = "new_merchants_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.NEW_MERCHANTS,
            title = "New merchants discovered",
            primaryValue = "${newMerchants.size} new",
            secondaryText = "${rupee(totalNewSpend)} at places not visited last period",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to merchantData)
        )
    }

    private fun generateMerchantLoyaltyInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.size < 5) return null

        val merchantCounts = thisMonthExpenses
            .groupingBy { it.merchantName }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val topMerchant = merchantCounts.firstOrNull() ?: return null
        if (topMerchant.second < 3) return null

        val merchantAmounts = thisMonthExpenses
            .filter { it.merchantName == topMerchant.first }
            .sumOf { it.amount }

        val countData = merchantCounts.joinToString("|") { "${it.first}:${it.second}x" }

        return SmartInsight(
            id = "merchant_loyalty_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.MERCHANT_LOYALTY,
            title = "Your most visited merchant",
            primaryValue = "${topMerchant.second} visits",
            secondaryText = "${topMerchant.first} — ${rupee(merchantAmounts)} total this period",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf(
                "merchant" to topMerchant.first,
                "topItems" to countData
            )
        )
    }

    private fun generateTransactionFrequencyInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        val lastMonthExpenses = periodExpenses(expenses, previousDateRange)
        if (thisMonthExpenses.size < 5) return null

        val thisDays = ChronoUnit.DAYS.between(dateRange.first, dateRange.second) + 1
        val lastDays = ChronoUnit.DAYS.between(previousDateRange.first, previousDateRange.second) + 1

        val thisFreq = thisMonthExpenses.size.toFloat() / thisDays
        val lastFreq = if (lastMonthExpenses.isNotEmpty()) lastMonthExpenses.size.toFloat() / lastDays else 0f

        val change = if (lastFreq > 0) ((thisFreq - lastFreq) / lastFreq * 100).toInt() else 0
        val freqStr = String.format("%.1f", thisFreq)
        val absChange = Math.abs(change)
        val direction = if (change > 0) "↑" else "↓"

        return SmartInsight(
            id = "txn_frequency_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.TRANSACTION_FREQUENCY,
            title = "Transaction frequency",
            primaryValue = "$freqStr txns/day",
            secondaryText = if (absChange > 5 && lastFreq > 0)
                "$direction$absChange% vs last period • ${thisMonthExpenses.size} total transactions"
            else
                "${thisMonthExpenses.size} total transactions this period",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange)
        )
    }

    private fun generateSpendSplitInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        if (thisMonthExpenses.size < 8) return null

        val midDay = dateRange.first.plusDays(ChronoUnit.DAYS.between(dateRange.first, dateRange.second) / 2)
        val firstHalf = thisMonthExpenses.filter { !it.dateTime.toLocalDate().isAfter(midDay) }
        val secondHalf = thisMonthExpenses.filter { it.dateTime.toLocalDate().isAfter(midDay) }

        if (firstHalf.isEmpty() || secondHalf.isEmpty()) return null

        val firstHalfTotal = firstHalf.sumOf { it.amount }
        val secondHalfTotal = secondHalf.sumOf { it.amount }
        val totalSpend = firstHalfTotal + secondHalfTotal
        if (totalSpend == BigDecimal.ZERO) return null

        val firstPercent = (firstHalfTotal / totalSpend * BigDecimal(100)).toInt()
        val secondPercent = 100 - firstPercent
        val bigger = if (firstPercent > secondPercent) "first half" else "second half"
        val biggerPct = maxOf(firstPercent, secondPercent)

        val splitData = "First half:$firstPercent%:${rupee(firstHalfTotal)}|Second half:$secondPercent%:${rupee(secondHalfTotal)}"

        return SmartInsight(
            id = "spend_split_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.SPEND_SPLIT,
            title = "Spending distribution",
            primaryValue = "$biggerPct% in $bigger",
            secondaryText = "${rupee(firstHalfTotal)} first half vs ${rupee(secondHalfTotal)} second half",
            confidence = InsightConfidence.MEDIUM,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to splitData)
        )
    }

    private fun generateMonthlyComparisonInsight(
        expenses: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
        previousDateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val thisMonthExpenses = periodExpenses(expenses, dateRange)
        val lastMonthExpenses = periodExpenses(expenses, previousDateRange)
        if (thisMonthExpenses.isEmpty() || lastMonthExpenses.isEmpty()) return null

        val thisTotal = thisMonthExpenses.sumOf { it.amount }
        val lastTotal = lastMonthExpenses.sumOf { it.amount }
        if (lastTotal == BigDecimal.ZERO) return null

        val change = ((thisTotal - lastTotal) / lastTotal * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        val isIncrease = thisTotal > lastTotal
        val changeStr = "${if (isIncrease) "↑" else "↓"}${change.abs()}%"

        // Identify which category drove the change, so the narrative can call out a specific
        // reason rather than just restating the two totals.
        val thisByCategory = thisMonthExpenses.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
        val lastByCategory = lastMonthExpenses.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
        val driverCategory = thisByCategory.keys.union(lastByCategory.keys)
            .maxByOrNull { cat -> (thisByCategory[cat] ?: BigDecimal.ZERO).minus(lastByCategory[cat] ?: BigDecimal.ZERO).let { if (isIncrease) it else it.negate() } }
        val driverDelta = driverCategory?.let { cat ->
            ((thisByCategory[cat] ?: BigDecimal.ZERO) - (lastByCategory[cat] ?: BigDecimal.ZERO)).abs().toInt()
        }

        val metadata = periodMetadata(dateRange) + mapOfNotNull(
            "driverCategory" to driverCategory,
            "driverDelta" to driverDelta?.toString(),
            "isIncrease" to isIncrease.toString(),
            "thisTotal" to thisTotal.toInt().toString(),
            "lastTotal" to lastTotal.toInt().toString(),
        )

        return SmartInsight(
            id = "monthly_comparison_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.MONTHLY_COMPARISON,
            title = "Month-over-month total",
            primaryValue = changeStr,
            secondaryText = "${rupee(thisTotal)} this period vs ${rupee(lastTotal)} last period",
            confidence = InsightConfidence.HIGH,
            metadata = metadata
        )
    }

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
        pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

    private fun generateIncomeVsExpenseInsight(
        transactions: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val start = dateRange.first.atStartOfDay()
        val endExclusive = dateRange.second.plusDays(1).atStartOfDay()
        val periodTxns = transactions.filter { !it.dateTime.isBefore(start) && it.dateTime.isBefore(endExclusive) }

        val income = periodTxns.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amount }
        val expenseTotal = periodTxns.filter {
            it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT
        }.sumOf { it.amount }

        if (income == BigDecimal.ZERO) return null

        val savings = income - expenseTotal
        val savingsPercent = (savings / income * BigDecimal(100)).toInt()

        val breakdown = "Income:${rupee(income)}|Expenses:${rupee(expenseTotal)}|Saved:${rupee(savings.coerceAtLeast(BigDecimal.ZERO))}"

        return SmartInsight(
            id = "income_vs_expense_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.INCOME_VS_EXPENSE,
            title = if (savings >= BigDecimal.ZERO) "You're saving this period" else "Spending exceeds income",
            primaryValue = if (savings >= BigDecimal.ZERO) "Saved ${rupeeHeadline(savings)}" else "Over by ${rupeeHeadline(-savings)}",
            secondaryText = if (savingsPercent >= 0) "$savingsPercent% of income saved" else "${rupee(income)} income, ${rupee(expenseTotal)} spent",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to breakdown)
        )
    }

    private fun generateInvestmentRatioInsight(
        transactions: List<TransactionEntity>,
        anchorMonth: YearMonth,
        dateRange: Pair<LocalDate, LocalDate>,
    ): SmartInsight? {
        val start = dateRange.first.atStartOfDay()
        val endExclusive = dateRange.second.plusDays(1).atStartOfDay()
        val periodTxns = transactions.filter { !it.dateTime.isBefore(start) && it.dateTime.isBefore(endExclusive) }

        val investments = periodTxns.filter { it.transactionType == TransactionType.INVESTMENT }
        if (investments.isEmpty()) return null

        val investmentTotal = investments.sumOf { it.amount }
        val outflows = periodTxns.filter {
            it.transactionType == TransactionType.EXPENSE ||
            it.transactionType == TransactionType.CREDIT ||
            it.transactionType == TransactionType.INVESTMENT
        }.sumOf { it.amount }

        if (outflows == BigDecimal.ZERO) return null

        val investPercent = (investmentTotal / outflows * BigDecimal(100)).toInt()
        val uniqueFunds = investments.distinctBy { it.merchantName }.size

        val topInvestments = investments
            .groupingBy { it.merchantName }
            .fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val investData = topInvestments.joinToString("|") { "${it.first}:${rupee(it.second)}" }

        return SmartInsight(
            id = "investment_ratio_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.INVESTMENT_RATIO,
            title = "Investment allocation",
            primaryValue = "$investPercent% invested",
            secondaryText = "${rupee(investmentTotal)} across $uniqueFunds funds/instruments",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to investData)
        )
    }
}
