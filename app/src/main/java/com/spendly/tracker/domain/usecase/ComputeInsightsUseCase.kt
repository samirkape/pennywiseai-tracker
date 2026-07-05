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
            primaryValue = "₹${projectedTotal.toInt()}",
            secondaryText = "Based on ₹${dailyRate.toInt()}/day avg",
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

        val thisMonthByCategory = thisMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
        val lastMonthByCategory = lastMonthExpenses.groupingBy { it.category }.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }

        val topGrowingCategories = thisMonthByCategory
            .mapNotNull { (category, thisMonthTotal) ->
                val lastMonthTotal = lastMonthByCategory[category] ?: BigDecimal.ZERO
                val growth = if (lastMonthTotal > BigDecimal.ZERO) {
                    ((thisMonthTotal - lastMonthTotal) / lastMonthTotal * BigDecimal(100)).toInt()
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
            secondaryText = "₹${recurringAmount.toInt()} of ₹${totalAmount.toInt()} from ${recurringMerchants.size} merchants",
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
            type = InsightType.TOP_CATEGORIES,
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
            primaryValue = "₹${largest.amount.toInt()}",
            secondaryText = "at ${largest.merchantName} — ${timesAvg}x your avg transaction",
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
        val dayItems = "Weekends:₹${weekendDailyAvg.toInt()}|Weekdays:₹${weekdayDailyAvg.toInt()}"

        return SmartInsight(
            id = "weekend_spend_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.WEEKEND_SPEND,
            title = "Weekend vs weekday spending",
            primaryValue = "$absDiff% $direction",
            secondaryText = "Weekend ₹${weekendDailyAvg.toInt()}/day vs ₹${weekdayDailyAvg.toInt()}/day weekdays",
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
            "$name:₹${it.second.toInt()}"
        }
        val peakDayName = peakDay.first.name.lowercase().replaceFirstChar { it.uppercase() }
        val lowestDayName = lowestDay.first.name.lowercase().replaceFirstChar { it.uppercase() }

        return SmartInsight(
            id = "peak_spend_day_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.PEAK_SPEND_DAY,
            title = "Peak spending day of week",
            primaryValue = peakDayName,
            secondaryText = "₹${peakDay.second.toInt()} total — lowest on $lowestDayName",
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
        val merchantData = topNew.joinToString("|") { "${it.first}:₹${it.second.toInt()}" }
        val totalNewSpend = newMerchants.sumOf { it.second }

        return SmartInsight(
            id = "new_merchants_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.NEW_MERCHANTS,
            title = "New merchants discovered",
            primaryValue = "${newMerchants.size} new",
            secondaryText = "₹${totalNewSpend.toInt()} at places not visited last period",
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
            secondaryText = "${topMerchant.first} — ₹${merchantAmounts.toInt()} total this period",
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

        val splitData = "First half:$firstPercent%:₹${firstHalfTotal.toInt()}|Second half:$secondPercent%:₹${secondHalfTotal.toInt()}"

        return SmartInsight(
            id = "spend_split_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.SPEND_SPLIT,
            title = "Spending distribution",
            primaryValue = "$biggerPct% in $bigger",
            secondaryText = "₹${firstHalfTotal.toInt()} first half vs ₹${secondHalfTotal.toInt()} second half",
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

        val change = ((thisTotal - lastTotal) / lastTotal * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
        val isIncrease = thisTotal > lastTotal
        val changeStr = "${if (isIncrease) "↑" else "↓"}${change.abs()}%"

        return SmartInsight(
            id = "monthly_comparison_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.MONTHLY_COMPARISON,
            title = "Month-over-month total",
            primaryValue = changeStr,
            secondaryText = "₹${thisTotal.toInt()} this period vs ₹${lastTotal.toInt()} last period",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange)
        )
    }

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

        val breakdown = "Income:₹${income.toInt()}|Expenses:₹${expenseTotal.toInt()}|Saved:₹${savings.toInt().coerceAtLeast(0)}"

        return SmartInsight(
            id = "income_vs_expense_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.INCOME_VS_EXPENSE,
            title = if (savings >= BigDecimal.ZERO) "You're saving this period" else "Spending exceeds income",
            primaryValue = if (savings >= BigDecimal.ZERO) "Saved ₹${savings.toInt()}" else "Over by ₹${(-savings).toInt()}",
            secondaryText = if (savingsPercent >= 0) "$savingsPercent% of income saved" else "₹${income.toInt()} income, ₹${expenseTotal.toInt()} spent",
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

        val investData = topInvestments.joinToString("|") { "${it.first}:₹${it.second.toInt()}" }

        return SmartInsight(
            id = "investment_ratio_${anchorMonth.year}_${anchorMonth.monthValue}",
            type = InsightType.INVESTMENT_RATIO,
            title = "Investment allocation",
            primaryValue = "$investPercent% invested",
            secondaryText = "₹${investmentTotal.toInt()} across $uniqueFunds funds/instruments",
            confidence = InsightConfidence.HIGH,
            metadata = periodMetadata(dateRange) + mapOf("topItems" to investData)
        )
    }
}
