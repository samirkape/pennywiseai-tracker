package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.CategoryMonthSpend
import com.spendly.tracker.connector.domain.CategorySpend
import com.spendly.tracker.connector.domain.Essentiality
import com.spendly.tracker.connector.domain.ExpenseBehaviour
import com.spendly.tracker.connector.domain.FinancialHealth
import com.spendly.tracker.connector.domain.FinancialType
import com.spendly.tracker.connector.domain.FlowSummary
import com.spendly.tracker.connector.domain.MerchantPrivacy
import com.spendly.tracker.connector.domain.MerchantSpend
import com.spendly.tracker.connector.domain.MoneyDestination
import com.spendly.tracker.connector.domain.MoneyMovedSummary
import com.spendly.tracker.connector.domain.MonthBreakdown
import com.spendly.tracker.connector.domain.MonthMoneyMoved
import com.spendly.tracker.connector.domain.MonthSpend
import com.spendly.tracker.connector.domain.NamedExpense
import com.spendly.tracker.connector.domain.PaymentMethod
import com.spendly.tracker.connector.domain.RecurringSpend
import com.spendly.tracker.connector.domain.RefundKind
import com.spendly.tracker.connector.domain.RefundKindSpend
import com.spendly.tracker.connector.domain.RefundMatchStatus
import com.spendly.tracker.connector.domain.RefundSummary
import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.SpendingSpike
import com.spendly.tracker.connector.domain.TagSpend
import com.spendly.tracker.connector.domain.UnmatchedRefund
import com.spendly.tracker.connector.domain.WeekdaySpend
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import com.spendly.tracker.domain.analytics.SpendingFilters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure aggregation of transactions into a [SpendAnalysis].
 *
 * Kept free of Android and database types so the numbers can be unit tested.
 *
 * The rules the numbers follow:
 * - A credit card purchase is an expense; the card bill payment is a transfer.
 * - A refund is never income. It reduces the category of the expense it reverses.
 * - Transfers, investments and loan-linked rows are never spending and never income.
 * - A refund that cannot be tied to an original is reported as unmatched instead
 *   of being pushed into a category or into income.
 */
object SpendAnalysisBuilder {

    fun build(
        transactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList(),
        window: AnalysisWindow,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        categorySplits: Map<Long, Map<String, BigDecimal>> = emptyMap(),
        loanDirections: Map<Long, LoanDirection> = emptyMap()
    ): SpendAnalysis {
        val aliasRegistry = AccountAliasRegistry()
        val included = transactions.filter { !it.isExcludedFromTracking && it.amount.signum() > 0 }
        val excludedCount = transactions.count { it.isExcludedFromTracking }
        val zeroAmountCount = transactions.count {
            !it.isExcludedFromTracking && it.amount.signum() == 0
        }

        val merchantOccurrences = included
            .groupingBy { MerchantNormalizer.normalizeOrFallback(privacyMerchant(it)) }
            .eachCount()
        val rows = classify(included, loanDirections, aliasRegistry, merchantOccurrences)

        // A refund is matched when the original it reverses sits inside the period.
        val originalsById = rows.associateBy { it.entity.id }
        val refunds = rows.filter { it.type == FinancialType.REFUND }
        val refundsByOriginal = refunds
            .mapNotNull { row -> row.entity.refundOfTransactionId?.let { it to row } }
            .groupBy({ it.first }, { it.second })

        val netByRow = mutableMapOf<Long, BigDecimal>()
        val refundedByOriginal = mutableMapOf<Long, BigDecimal>()
        refundsByOriginal.forEach { (originalId, refundRows) ->
            val total = refundRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }
            refundedByOriginal[originalId] = total
            val original = originalsById[originalId]
            if (original != null) {
                netByRow[originalId] = money((original.entity.amount - total).max(BigDecimal.ZERO))
            }
        }
        rows.forEach { row ->
            if (!netByRow.containsKey(row.entity.id)) {
                netByRow[row.entity.id] = row.entity.amount
            }
        }

        val expenses = rows.filter { it.type == FinancialType.EXPENSE }
        val incomes = rows.filter { it.type == FinancialType.INCOME }
        val reversalRows = rows.filter { it.type == FinancialType.REVERSAL }
        val moneyMovedRows = rows.filter { it.type in MONEY_MOVED_TYPES }

        val grossExpense = money(expenses.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })
        val refundsReceived = money(refunds.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })
        val netExpense = money(grossExpense.subtract(refundsReceived))
        val totalIncome = money(incomes.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })

        val dayCount = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)
        val dailyAverage = netExpense.divide(BigDecimal(dayCount), 2, RoundingMode.HALF_UP)

        // ---- Categories -----------------------------------------------------
        val categoryGross = mutableMapOf<String, BigDecimal>()
        val categoryRefunds = mutableMapOf<String, BigDecimal>()
        val categoryCounts = mutableMapOf<String, Int>()
        val categoryEssentiality = mutableMapOf<String, MutableMap<Essentiality, Int>>()
        val categoryBehaviour = mutableMapOf<String, MutableMap<ExpenseBehaviour, Int>>()

        expenses.forEach { row ->
            val grossByCategory = categoryAmounts(row.entity, categorySplits)
            val netAmount = netByRow[row.entity.id] ?: row.entity.amount
            val netByCategory = spreadNet(grossByCategory, row.entity.amount, netAmount)
            grossByCategory.forEach { (category, amount) ->
                categoryGross[category] = (categoryGross[category] ?: BigDecimal.ZERO) + amount
                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            }
            netByCategory.forEach { (category, amount) ->
                val gross = grossByCategory[category] ?: BigDecimal.ZERO
                categoryRefunds[category] =
                    (categoryRefunds[category] ?: BigDecimal.ZERO) + (gross - amount)
            }
            categoryEssentiality.getOrPut(categoryOf(row.entity)) { mutableMapOf() }
                .merge(row.essentiality, 1, Int::plus)
            categoryBehaviour.getOrPut(categoryOf(row.entity)) { mutableMapOf() }
                .merge(row.behaviour, 1, Int::plus)        }

        val allCategories = categoryGross.keys.sorted()
        val categoryTotals = allCategories.map { category ->
            val gross = money(categoryGross[category] ?: BigDecimal.ZERO)
            val refunded = money(categoryRefunds[category] ?: BigDecimal.ZERO)
            val net = money(gross.subtract(refunded))
            CategorySpend(
                category = category,
                grossExpense = gross,
                refundsReceived = refunded,
                netExpense = net,
                shareOfNetSpendingPercent = percentOf(net, netExpense),
                transactionCount = categoryCounts[category] ?: 0,
                essentiality = dominantEssentiality(categoryEssentiality[category]),
                behaviour = dominantBehaviour(categoryBehaviour[category])
            )
        }.sortedWith(
            compareByDescending<CategorySpend> { it.netExpense }.thenBy { it.category }
        )
        val categories = categoryTotals.take(MAX_CATEGORIES)

        // ---- Merchants, recurring, largest ----------------------------------
        val merchants = buildMerchants(expenses, netByRow)
        val recurring = buildRecurring(expenses, netByRow)
        val largestExpenses = buildLargestExpenses(expenses, netByRow, refundedByOriginal)
        val weekdaySpend = buildWeekdaySpend(expenses, netByRow)
        val tags = buildTags(expenses, netByRow)

        // ---- Refunds --------------------------------------------------------
        val refundSummary = buildRefundSummary(refunds, originalsById)
        val unmatchedRefundAmount = refundSummary.unmatched.amount

        // ---- Months ---------------------------------------------------------
        val monthList = monthsBetween(startDate, endDate)
        val monthlyBuild = buildMonths(
            months = monthList,
            startDate = startDate,
            endDate = endDate,
            rows = rows,
            netByRow = netByRow,
            categorySplits = categorySplits
        )
        val months = monthlyBuild.months
        val monthlyTrend = months.map { month ->
            MonthSpend(
                month = month.month,
                expense = month.netExpense,
                income = month.income
            )
        }

        // ---- Money moved ----------------------------------------------------
        val moneyMoved = buildMoneyMoved(moneyMovedRows, monthList, aliasRegistry)

        // ---- Previous period ------------------------------------------------
        val previousRows = classify(
            previousTransactions.filter { !it.isExcludedFromTracking && it.amount.signum() > 0 },
            loanDirections,
            AccountAliasRegistry()
        )
        val previousRefundsByOriginal = previousRows
            .filter { it.type == FinancialType.REFUND }
            .mapNotNull { row -> row.entity.refundOfTransactionId?.let { it to row.entity.amount } }
            .groupBy({ it.first }, { it.second })
        val previousExpense = previousRows
            .filter { it.type == FinancialType.EXPENSE }
            .fold(BigDecimal.ZERO) { acc, row ->
                val refunded = previousRefundsByOriginal[row.entity.id]
                    ?.fold(BigDecimal.ZERO) { sum, value -> sum + value }
                    ?: BigDecimal.ZERO
                acc + (row.entity.amount - refunded).max(BigDecimal.ZERO)
            }.let { money(it) }
        val changePercent = if (previousExpense.signum() > 0) {
            netExpense.subtract(previousExpense)
                .multiply(BigDecimal(100))
                .divide(previousExpense, 1, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            null
        }

        // ---- Health ---------------------------------------------------------
        val health = buildHealth(
            months = months,
            totalIncome = totalIncome,
            netExpense = netExpense,
            totalInvestments = moneyMoved?.investments?.amount ?: BigDecimal.ZERO,
            recurring = recurring,
            categories = categoryTotals
        )

        // ---- Data quality ---------------------------------------------------
        val categoryNetTotal = money(
            categoryTotals.fold(BigDecimal.ZERO) { acc, item -> acc + item.netExpense }
        )
        val dataQuality = buildDataQuality(
            transactions = transactions,
            included = included,
            rows = rows,
            excludedCount = excludedCount,
            zeroAmountCount = zeroAmountCount,
            startDate = startDate,
            endDate = endDate,
            months = monthList,
            grossExpense = grossExpense,
            refundsReceived = refundsReceived,
            netExpense = netExpense,
            categoryNetTotal = categoryNetTotal,
            unmatchedRefundCount = refundSummary.unmatched.transactionCount,
            unmatchedRefundAmount = money(unmatchedRefundAmount),
            categoriesOmitted = (allCategories.size - categories.size).coerceAtLeast(0),
            merchantsOmitted = (merchantsTotal(expenses) - merchants.size).coerceAtLeast(0),
            monthlyCategoryRowsOmitted = monthlyBuild.omittedCategoryRows,
            zeroAmountTransactions = zeroAmountCount,
            reversalRows = reversalRows
        )

        return SpendAnalysis(
            window = window,
            startDate = startDate,
            endDate = endDate,
            currency = currency,
            grossExpense = grossExpense,
            refundsReceived = refundsReceived,
            netExpense = netExpense,
            totalIncome = totalIncome,
            expenseCount = expenses.size,
            incomeCount = incomes.size,
            refundCount = refunds.size,
            dailyAverage = dailyAverage,
            categories = categories,
            merchants = merchants,
            monthlyTrend = monthlyTrend,
            months = months,
            recurring = recurring,
            largestExpenses = largestExpenses,
            weekdaySpend = weekdaySpend,
            previousExpense = previousExpense.takeIf { it.signum() > 0 },
            previousExpenseChangePercent = changePercent,
            tags = tags,
            refunds = refundSummary,
            moneyMoved = moneyMoved,
            health = health,
            dataQuality = dataQuality
        )
    }

    // ---- Classification -----------------------------------------------------

    private fun classify(
        transactions: List<TransactionEntity>,
        loanDirections: Map<Long, LoanDirection>,
        aliasRegistry: AccountAliasRegistry,
        merchantOccurrences: Map<String, Int> = emptyMap()
    ): List<Row> = transactions.map { entity ->
        val type = TransactionClassifier.financialType(entity, entity.loanId?.let { loanDirections[it] })
        val merchantOriginal = privacyMerchant(entity)
        val merchantNormalized = MerchantNormalizer.normalizeOrFallback(merchantOriginal)
        Row(
            entity = entity,
            type = type,
            paymentMethod = TransactionClassifier.paymentMethod(entity),
            essentiality = TransactionClassifier.essentialityOf(entity, type),
            behaviour = TransactionClassifier.behaviourOf(
                entity,
                type,
                merchantOccurrences[merchantNormalized] ?: 1
            ),
            merchantOriginal = merchantOriginal,
            merchantNormalized = merchantNormalized,
            accountAlias = aliasRegistry.aliasFor(entity),
            category = categoryOf(entity)
        )
    }

    /** The merchant as the user sees it, with identifying handles masked. */
    private fun privacyMerchant(entity: TransactionEntity): String =
        MerchantPrivacy.safeMerchantName(entity.merchantName).takeIf { it.isNotBlank() }
            ?: UNKNOWN_MERCHANT

    /** One classified row plus the privacy-safe fields the brief shows. */
    private data class Row(
        val entity: TransactionEntity,
        val type: FinancialType,
        val paymentMethod: PaymentMethod,
        val essentiality: Essentiality,
        val behaviour: ExpenseBehaviour,
        val merchantOriginal: String,
        val merchantNormalized: String,
        val accountAlias: String?,
        val category: String
    )

    // ---- Breakdowns ---------------------------------------------------------

    private fun buildMerchants(
        expenses: List<Row>,
        netByRow: Map<Long, BigDecimal>
    ): List<MerchantSpend> = expenses
        .groupBy { it.merchantNormalized }
        .map { (normalized, rows) ->
            val display = rows.groupingBy { it.merchantOriginal }.eachCount()
                .maxByOrNull { it.value }?.key ?: normalized
            MerchantSpend(
                merchantName = display,
                amount = money(rows.fold(BigDecimal.ZERO) { acc, row -> acc + (netByRow[row.entity.id] ?: row.entity.amount) }),
                transactionCount = rows.size
            )
        }
        .sortedByDescending { it.amount }
        .take(MAX_MERCHANTS)

    private fun merchantsTotal(expenses: List<Row>): Int = expenses.map { it.merchantNormalized }.distinct().size

    private fun buildRecurring(
        expenses: List<Row>,
        netByRow: Map<Long, BigDecimal>
    ): List<RecurringSpend> = expenses
        .groupBy { it.merchantNormalized }
        .filterValues { it.size >= MIN_RECURRING_OCCURRENCES }
        .map { (normalized, rows) ->
            val display = rows.groupingBy { it.merchantOriginal }.eachCount()
                .maxByOrNull { it.value }?.key ?: normalized
            val amount = money(rows.fold(BigDecimal.ZERO) { acc, row ->
                acc + (netByRow[row.entity.id] ?: row.entity.amount)
            })
            RecurringSpend(
                merchantName = display,
                occurrences = rows.size,
                amount = amount,
                typicalAmount = amount.divide(BigDecimal(rows.size), 2, RoundingMode.HALF_UP)
            )
        }
        .sortedWith(compareByDescending<RecurringSpend> { it.occurrences }.thenByDescending { it.amount })
        .take(MAX_RECURRING)

    private fun buildLargestExpenses(
        expenses: List<Row>,
        netByRow: Map<Long, BigDecimal>,
        refundedByOriginal: Map<Long, BigDecimal>
    ): List<NamedExpense> = expenses
        .sortedByDescending { it.entity.amount }
        .take(MAX_LARGEST)
        .map { row ->
            val net = netByRow[row.entity.id] ?: row.entity.amount
            NamedExpense(
                name = row.merchantOriginal,
                grossAmount = row.entity.amount,
                refundedAmount = refundedByOriginal[row.entity.id] ?: BigDecimal.ZERO,
                netAmount = net,
                category = row.category,
                date = row.entity.dateTime.toLocalDate()
            )
        }

    private fun buildWeekdaySpend(
        expenses: List<Row>,
        netByRow: Map<Long, BigDecimal>
    ): List<WeekdaySpend> = DayOfWeek.entries.map { day ->
        val matching = expenses.filter { it.entity.dateTime.dayOfWeek == day }
        WeekdaySpend(
            dayOfWeek = day,
            amount = money(matching.fold(BigDecimal.ZERO) { acc, row ->
                acc + (netByRow[row.entity.id] ?: row.entity.amount)
            }),
            transactionCount = matching.size
        )
    }

    private fun buildTags(
        expenses: List<Row>,
        netByRow: Map<Long, BigDecimal>
    ): List<TagSpend> = expenses
        .flatMap { row ->
            val net = netByRow[row.entity.id] ?: row.entity.amount
            row.entity.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { it to net }
        }
        .groupBy({ it.first }, { it.second })
        .map { (tag, amounts) ->
            TagSpend(
                tag = tag,
                amount = money(amounts.fold(BigDecimal.ZERO) { acc, value -> acc + value }),
                transactionCount = amounts.size
            )
        }
        .sortedByDescending { it.amount }
        .take(MAX_TAGS)

    private fun buildRefundSummary(
        refunds: List<Row>,
        originalsById: Map<Long, Row>
    ): RefundSummary {
        val byKind = mutableMapOf<Pair<RefundKind, RefundMatchStatus>, Pair<BigDecimal, Int>>()
        val unmatchedDetails = mutableListOf<UnmatchedRefund>()
        var matchedAmount = BigDecimal.ZERO
        var matchedCount = 0
        var unmatchedAmount = BigDecimal.ZERO
        var unmatchedCount = 0

        refunds.forEach { row ->
            val originalId = row.entity.refundOfTransactionId
            val original = originalId?.let { originalsById[it] }
            val matched = original != null
            val kind = if (original != null) {
                TransactionClassifier.kindOfLinkedRefund(row.entity, original.entity)
            } else {
                TransactionClassifier.kindOfUnlinkedRefund(row.entity)
            }
            val status = if (matched) RefundMatchStatus.MATCHED else RefundMatchStatus.UNMATCHED
            val key = kind to status
            val current = byKind[key] ?: (BigDecimal.ZERO to 0)
            byKind[key] = (current.first + row.entity.amount) to (current.second + 1)

            if (matched) {
                matchedAmount = matchedAmount.add(row.entity.amount)
                matchedCount += 1
            } else {
                unmatchedAmount = unmatchedAmount.add(row.entity.amount)
                unmatchedCount += 1
                unmatchedDetails += UnmatchedRefund(
                    date = row.entity.dateTime.toLocalDate(),
                    amount = row.entity.amount,
                    merchant = row.merchantOriginal
                )
            }
        }

        return RefundSummary(
            total = money(refunds.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }),
            matched = FlowSummary(money(matchedAmount), matchedCount),
            unmatched = FlowSummary(money(unmatchedAmount), unmatchedCount),
            byKind = byKind.entries
                .map { (key, value) ->
                    RefundKindSpend(key.first, money(value.first), value.second, key.second)
                }
                .sortedWith(
                    compareByDescending<RefundKindSpend> { it.amount }.thenBy { it.kind.label }
                ),
            unmatchedDetails = unmatchedDetails.sortedByDescending { it.amount }
        )
    }

    // ---- Months -------------------------------------------------------------

    private fun buildMonths(
        months: List<YearMonth>,
        startDate: LocalDate,
        endDate: LocalDate,
        rows: List<Row>,
        netByRow: Map<Long, BigDecimal>,
        categorySplits: Map<Long, Map<String, BigDecimal>>
    ): MonthlyBuild {
        var omittedRows = 0
        val breakdowns = months.map { month ->
        val inMonth = rows.filter { YearMonth.from(it.entity.dateTime.toLocalDate()) == month }
        val expenses = inMonth.filter { it.type == FinancialType.EXPENSE }
        val incomes = inMonth.filter { it.type == FinancialType.INCOME }
        val refundRows = inMonth.filter { it.type == FinancialType.REFUND }
        val moved = inMonth.filter { it.type in MONEY_MOVED_TYPES }

        val grossExpense = money(expenses.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })
        val refunds = money(refundRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })
        val netExpense = money(grossExpense.subtract(refunds))
        val income = money(incomes.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })
        val netCashFlow = money(income.subtract(netExpense))

        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val coveredStart = maxOf(monthStart, startDate)
        val coveredEnd = minOf(monthEnd, endDate)
        val daysInPeriod = (ChronoUnit.DAYS.between(coveredStart, coveredEnd) + 1).toInt()

        val categoryGross = mutableMapOf<String, BigDecimal>()
        val categoryRefunds = mutableMapOf<String, BigDecimal>()
        val categoryCounts = mutableMapOf<String, Int>()
        expenses.forEach { row ->
            val grossByCategory = categoryAmounts(row.entity, categorySplits)
            val net = netByRow[row.entity.id] ?: row.entity.amount
            val netByCategory = spreadNet(grossByCategory, row.entity.amount, net)
            grossByCategory.forEach { (category, amount) ->
                categoryGross[category] = (categoryGross[category] ?: BigDecimal.ZERO) + amount
                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            }
            netByCategory.forEach { (category, amount) ->
                val gross = grossByCategory[category] ?: BigDecimal.ZERO
                categoryRefunds[category] =
                    (categoryRefunds[category] ?: BigDecimal.ZERO) + (gross - amount)
            }
        }
        val categoryRows = categoryGross.keys.sorted()
            .map { category ->
                val gross = money(categoryGross[category] ?: BigDecimal.ZERO)
                val refunded = money(categoryRefunds[category] ?: BigDecimal.ZERO)
                CategoryMonthSpend(
                    category = category,
                    grossExpense = gross,
                    refundsReceived = refunded,
                    netExpense = money(gross.subtract(refunded)),
                    transactionCount = categoryCounts[category] ?: 0
                )
            }
            .sortedByDescending { it.netExpense }
        omittedRows += (categoryRows.size - MAX_CATEGORY_ROWS_PER_MONTH).coerceAtLeast(0)
        val keptCategoryRows = categoryRows.take(MAX_CATEGORY_ROWS_PER_MONTH)

        val transfersOut = money(
            moved.filter { it.type == FinancialType.TRANSFER }
                .fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }
        )
        val investments = money(
            moved.filter { it.type == FinancialType.INVESTMENT }
                .fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }
        )
        val loanOutflow = money(
            moved.filter { it.type in LOAN_TYPES }
                .filter { loanRowIsOutflow(it) }
                .fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }
        )
        val loanInflow = money(
            moved.filter { it.type in LOAN_TYPES }
                .filter { !loanRowIsOutflow(it) }
                .fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }
        )
        val transfersIn = money(
            moved.filter { it.type == FinancialType.TRANSFER }
                .filter { it.entity.transactionType == TransactionType.INCOME }
                .fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }
        )

        MonthBreakdown(
            month = month,
            label = month.toString(),
            isPartial = monthStart < startDate || monthEnd > endDate,
            daysInPeriod = daysInPeriod,
            income = income,
            grossExpense = grossExpense,
            refunds = refunds,
            netExpense = netExpense,
            netCashFlow = netCashFlow,
            savingsRatePercent = if (income.signum() > 0) {
                netCashFlow.multiply(BigDecimal(100)).divide(income, 1, RoundingMode.HALF_UP).toDouble()
            } else {
                null
            },
            investments = investments,
            transfersOut = transfersOut,
            transfersIn = transfersIn,
            loanInflow = loanInflow,
            loanOutflow = loanOutflow,
            categories = keptCategoryRows
        )
        }
        return MonthlyBuild(breakdowns, omittedRows)
    }

    /** Month breakdowns plus the number of category rows that did not fit. */
    private data class MonthlyBuild(
        val months: List<MonthBreakdown>,
        val omittedCategoryRows: Int
    )

    private fun loanRowIsOutflow(row: Row): Boolean =
        when (row.type) {
            FinancialType.LOAN_DISBURSEMENT -> row.entity.transactionType != TransactionType.INCOME
            FinancialType.LOAN_PAYMENT -> row.entity.transactionType == TransactionType.EXPENSE ||
                row.entity.transactionType == TransactionType.CREDIT
            else -> true
        }

    // ---- Money moved --------------------------------------------------------

    private fun buildMoneyMoved(
        rows: List<Row>,
        months: List<YearMonth>,
        aliasRegistry: AccountAliasRegistry
    ): MoneyMovedSummary {
        fun summarize(predicate: (Row) -> Boolean): FlowSummary {
            val matching = rows.filter(predicate)
            return FlowSummary(
                amount = money(matching.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }),
                transactionCount = matching.size
            )
        }

        val isCcBill = { row: Row ->
            row.entity.transactionType == TransactionType.TRANSFER &&
                row.entity.transferKind == TransferKind.CC_BILL_PAYMENT
        }
        val isSelf = { row: Row ->
            row.entity.transactionType == TransactionType.TRANSFER &&
                (row.entity.transferKind == TransferKind.SELF_TRANSFER ||
                    row.entity.transferKind == TransferKind.SELF_TRANSFER_PENDING)
        }
        val isOther = { row: Row ->
            row.entity.transactionType == TransactionType.TRANSFER && !isCcBill(row) && !isSelf(row)
        }
        val isInvestment = { row: Row -> row.type == FinancialType.INVESTMENT }
        val isLoanOutflow = { row: Row -> row.type in LOAN_TYPES && loanRowIsOutflow(row) }
        val isLoanInflow = { row: Row -> row.type in LOAN_TYPES && !loanRowIsOutflow(row) }

        val monthly = months.map { month ->
            val inMonth = rows.filter { YearMonth.from(it.entity.dateTime.toLocalDate()) == month }
            MonthMoneyMoved(
                label = month.toString(),
                creditCardBillPayments = sumOf(inMonth.filter(isCcBill)),
                selfTransfers = sumOf(inMonth.filter(isSelf)),
                transfersToOthers = sumOf(inMonth.filter(isOther)),
                investments = sumOf(inMonth.filter(isInvestment)),
                loanLinkedOutflow = sumOf(inMonth.filter(isLoanOutflow)),
                loanLinkedInflow = sumOf(inMonth.filter(isLoanInflow))
            )
        }

        val destinations = mutableMapOf<Pair<String, String>, Pair<BigDecimal, Int>>()
        rows.forEach { row ->
            val bucket = bucketOf(row, isCcBill, isSelf, isOther, isInvestment, isLoanOutflow)
            if (bucket == null) return@forEach
            val alias = destinationAlias(row, aliasRegistry) ?: return@forEach
            val key = alias to bucket
            val current = destinations[key] ?: (BigDecimal.ZERO to 0)
            destinations[key] = (current.first + row.entity.amount) to (current.second + 1)
        }

        return MoneyMovedSummary(
            creditCardBillPayments = summarize(isCcBill),
            selfTransfers = summarize(isSelf),
            transfersToOthers = summarize(isOther),
            investments = summarize(isInvestment),
            loanLinkedOutflow = summarize(isLoanOutflow),
            loanLinkedInflow = summarize(isLoanInflow),
            monthly = monthly,
            destinations = destinations.entries
                .map { (key, value) ->
                    MoneyDestination(key.first, key.second, money(value.first), value.second)
                }
                .sortedByDescending { it.amount }
                .take(MAX_DESTINATIONS)
        )
    }

    private fun bucketOf(
        row: Row,
        isCcBill: (Row) -> Boolean,
        isSelf: (Row) -> Boolean,
        isOther: (Row) -> Boolean,
        isInvestment: (Row) -> Boolean,
        isLoanOutflow: (Row) -> Boolean
    ): String? = when {
        isCcBill(row) -> "credit_card_bill_payment"
        isSelf(row) -> "self_transfer"
        isOther(row) -> "transfer_to_other"
        isInvestment(row) -> "investment"
        isLoanOutflow(row) -> "loan_disbursement"
        row.type in LOAN_TYPES -> "loan_payment"
        else -> null
    }

    private fun destinationAlias(row: Row, registry: AccountAliasRegistry): String? {
        val raw = row.entity.toAccount?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.entity.fromAccount?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.entity.accountNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return registry.aliasFor(raw, row.entity.bankName)
    }

    private fun sumOf(rows: List<Row>): BigDecimal =
        money(rows.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount })

    // ---- Health -------------------------------------------------------------

    private fun buildHealth(
        months: List<MonthBreakdown>,
        totalIncome: BigDecimal,
        netExpense: BigDecimal,
        totalInvestments: BigDecimal,
        recurring: List<RecurringSpend>,
        categories: List<CategorySpend>
    ): FinancialHealth {
        val monthCount = months.size.coerceAtLeast(1)
        val monthlySpend = months.map { it.netExpense }
        val median = medianOf(monthlySpend)
        val average = netExpense.divide(BigDecimal(monthCount), 2, RoundingMode.HALF_UP)
        val averageIncome = totalIncome.divide(BigDecimal(monthCount), 2, RoundingMode.HALF_UP)
        val cashSavings = money(totalIncome.subtract(netExpense))
        val averageSavings = cashSavings.divide(BigDecimal(monthCount), 2, RoundingMode.HALF_UP)

        val essential = money(categories.filter { it.essentiality == Essentiality.ESSENTIAL }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.netExpense })
        val discretionary = money(categories.filter { it.essentiality == Essentiality.DISCRETIONARY }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.netExpense })
        val fixed = money(categories.filter { it.behaviour == ExpenseBehaviour.FIXED }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.netExpense })

        val completeMonths = months.filterNot { it.isPartial }
        val lastChange = if (completeMonths.size >= 2) {
            val last = completeMonths.last().netExpense
            val previous = completeMonths[completeMonths.lastIndex - 1].netExpense
            if (previous.signum() > 0) {
                last.subtract(previous).multiply(BigDecimal(100))
                    .divide(previous, 1, RoundingMode.HALF_UP).toDouble()
            } else {
                null
            }
        } else {
            null
        }

        val spikes = buildSpikes(months, median)

        return FinancialHealth(
            months = monthCount,
            totalIncome = totalIncome,
            averageMonthlyIncome = averageIncome,
            totalNetSpending = netExpense,
            averageMonthlySpending = average,
            medianMonthlySpending = median,
            averageMonthlySavings = averageSavings,
            savingsRatePercent = if (totalIncome.signum() > 0) {
                cashSavings.multiply(BigDecimal(100))
                    .divide(totalIncome, 1, RoundingMode.HALF_UP).toDouble()
            } else {
                null
            },
            essentialRatioPercent = percentOrNull(essential, netExpense),
            discretionaryRatioPercent = percentOrNull(discretionary, netExpense),
            fixedRatioPercent = percentOrNull(fixed, netExpense),
            investmentRatePercent = percentOrNull(totalInvestments, totalIncome),
            totalInvestments = totalInvestments,
            cashSavings = cashSavings,
            recurringExpenseTotal = money(recurring.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }),
            topCategories = categories.take(MAX_CATEGORIES),
            spikes = spikes,
            lastMonthChangePercent = lastChange,
            providentFundContribution = null,
            netWorthAccumulation = null
        )
    }

    /**
     * A month stands out when it is more than 1.5 times the median month and
     * more than one standard deviation above the mean.
     */
    private fun buildSpikes(months: List<MonthBreakdown>, median: BigDecimal): List<SpendingSpike> {
        if (months.size < 3 || median.signum() <= 0) return emptyList()
        val values = months.map { it.netExpense.toDouble() }
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val deviation = Math.sqrt(variance)
        return months.mapNotNull { month ->
            val value = month.netExpense.toDouble()
            val isSpike = value > median.toDouble() * SPIKE_MULTIPLE && value > mean + deviation
            if (!isSpike) {
                null
            } else {
                SpendingSpike(
                    month = month.label,
                    amount = month.netExpense,
                    timesMedian = month.netExpense.divide(median, 2, RoundingMode.HALF_UP).toDouble()
                )
            }
        }
    }

    private fun medianOf(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sorted = values.sortedBy { it }
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            sorted[middle - 1].add(sorted[middle])
                .divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
        }
    }

    // ---- Data quality -------------------------------------------------------

    private fun buildDataQuality(
        transactions: List<TransactionEntity>,
        included: List<TransactionEntity>,
        rows: List<Row>,
        excludedCount: Int,
        zeroAmountCount: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        months: List<YearMonth>,
        grossExpense: BigDecimal,
        refundsReceived: BigDecimal,
        netExpense: BigDecimal,
        categoryNetTotal: BigDecimal,
        unmatchedRefundCount: Int,
        unmatchedRefundAmount: BigDecimal,
        categoriesOmitted: Int,
        merchantsOmitted: Int,
        monthlyCategoryRowsOmitted: Int,
        zeroAmountTransactions: Int,
        reversalRows: List<Row>
    ): com.spendly.tracker.connector.domain.DataQualityReport {
        val duplicates = countDuplicates(rows)
        val uncategorized = included.count { it.category.isBlank() }
        val unknownPayment = rows.count { it.paymentMethod == PaymentMethod.UNKNOWN }
        val missingMerchant = included.count { it.merchantName.isBlank() }

        return com.spendly.tracker.connector.domain.DataQualityReport(
            periodStart = startDate,
            periodEnd = endDate,
            periodDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt(),
            periodMonths = months.size,
            partialMonths = months.count { month ->
                val monthStart = month.atDay(1)
                val monthEnd = month.atEndOfMonth()
                monthStart < startDate || monthEnd > endDate
            },
            sourceTransactions = transactions.size,
            includedTransactions = included.size,
            excludedTransactions = excludedCount,
            duplicateTransactions = duplicates,
            uncategorizedTransactions = uncategorized,
            unmatchedRefundCount = unmatchedRefundCount,
            unmatchedRefundAmount = money(unmatchedRefundAmount),
            unknownPaymentMethodCount = unknownPayment,
            missingMerchantCount = missingMerchant,
            reversalTransactions = reversalRows.size,
            reversalAmount = money(reversalRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.entity.amount }),
            summaryToTransactionsDifference = money(
                netExpense.subtract(grossExpense.subtract(refundsReceived))
            ),
            categoryToSummaryDifference = money(categoryNetTotal.subtract(netExpense)),
            categoriesOmitted = categoriesOmitted,
            merchantsOmitted = merchantsOmitted,
            monthlyCategoryRowsOmitted = monthlyCategoryRowsOmitted,
            zeroAmountTransactions = zeroAmountTransactions
        )
    }

    /**
     * Rows that repeat the same day, amount, merchant and category.
     *
     * The database blocks identical hashes, so this catches the same purchase
     * arriving through two different bank messages.
     */
    private fun countDuplicates(rows: List<Row>): Int = rows
        .groupBy { row ->
            listOf(
                row.entity.dateTime.toLocalDate().toString(),
                row.entity.amount.toPlainString(),
                row.merchantNormalized,
                row.category
            )
        }
        .values
        .sumOf { group -> (group.size - 1).coerceAtLeast(0) }

    // ---- Helpers ------------------------------------------------------------

    private fun categoryOf(transaction: TransactionEntity): String =
        transaction.category.takeIf { it.isNotBlank() } ?: UNCATEGORISED

    /** Gross amount per category for a row, honouring splits. */
    private fun categoryAmounts(
        transaction: TransactionEntity,
        categorySplits: Map<Long, Map<String, BigDecimal>>
    ): Map<String, BigDecimal> {
        val splits = categorySplits[transaction.id]
            ?.filterKeys { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
        return splits ?: mapOf(categoryOf(transaction) to transaction.amount)
    }

    /**
     * Spreads the net amount of a row across its categories so the parts always
     * add back up to the row's net amount.
     */
    private fun spreadNet(
        grossByCategory: Map<String, BigDecimal>,
        grossTotal: BigDecimal,
        netTotal: BigDecimal
    ): Map<String, BigDecimal> {
        if (grossByCategory.isEmpty()) return emptyMap()
        if (grossTotal.signum() == 0) return grossByCategory.mapValues { netTotal }
        val raw = grossByCategory.mapValues { (_, gross) ->
            gross.multiply(netTotal).divide(grossTotal, 6, RoundingMode.HALF_UP)
        }
        val distributed = raw.values.fold(BigDecimal.ZERO) { acc, value -> acc + value }
        val residual = netTotal.subtract(distributed)
        if (residual.signum() == 0) {
            return raw
        }
        val largest = raw.maxByOrNull { it.value }?.key ?: return raw
        return raw.toMutableMap().apply {
            this[largest] = (this[largest] ?: BigDecimal.ZERO).add(residual)
        }
    }

    private fun dominantEssentiality(counts: Map<Essentiality, Int>?): Essentiality =
        counts?.maxByOrNull { it.value }?.key ?: Essentiality.UNKNOWN

    private fun dominantBehaviour(counts: Map<ExpenseBehaviour, Int>?): ExpenseBehaviour =
        counts?.maxByOrNull { it.value }?.key ?: ExpenseBehaviour.UNKNOWN

    /** All money in the brief carries two decimals. */
    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    private fun percentOf(part: BigDecimal, whole: BigDecimal): Double =
        percentOrNull(part, whole) ?: 0.0

    private fun percentOrNull(part: BigDecimal, whole: BigDecimal): Double? {
        if (whole.signum() <= 0) return null
        return part.multiply(BigDecimal(100)).divide(whole, 1, RoundingMode.HALF_UP).toDouble()
    }

    private fun monthsBetween(startDate: LocalDate, endDate: LocalDate): List<YearMonth> {
        val first = YearMonth.from(startDate)
        val last = YearMonth.from(endDate)
        if (last < first) return listOf(first)
        val months = mutableListOf<YearMonth>()
        var cursor = first
        while (cursor <= last) {
            months += cursor
            cursor = cursor.plusMonths(1)
        }
        return months
    }

    /**
     * Same rule the Analytics screen uses: credit card purchases count as spend,
     * loan-linked rows, transfers and investments do not.
     */
    fun isSpend(transaction: TransactionEntity): Boolean =
        !transaction.isExcludedFromTracking && SpendingFilters.countsAsSpending(transaction)

    fun isIncome(transaction: TransactionEntity): Boolean =
        !transaction.isExcludedFromTracking && SpendingFilters.countsAsIncome(transaction)

    private val MONEY_MOVED_TYPES = setOf(
        FinancialType.TRANSFER,
        FinancialType.INVESTMENT,
        FinancialType.LOAN_PAYMENT,
        FinancialType.LOAN_DISBURSEMENT
    )

    private val LOAN_TYPES = setOf(
        FinancialType.LOAN_PAYMENT,
        FinancialType.LOAN_DISBURSEMENT
    )

    private const val MAX_CATEGORIES = 12
    private const val MAX_MERCHANTS = 12
    private const val MAX_RECURRING = 8
    private const val MAX_LARGEST = 6
    private const val MAX_TAGS = 8
    private const val MAX_DESTINATIONS = 12
    private const val MAX_CATEGORY_ROWS_PER_MONTH = 10
    private const val MIN_RECURRING_OCCURRENCES = 3
    private const val SPIKE_MULTIPLE = 1.5
    private const val UNCATEGORISED = "Uncategorised"
    private const val UNKNOWN_MERCHANT = "Unknown"
}
