package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.Essentiality
import com.spendly.tracker.connector.domain.ExpenseBehaviour
import com.spendly.tracker.connector.domain.FinancialType
import com.spendly.tracker.connector.domain.PaymentMethod
import com.spendly.tracker.connector.domain.RefundKind
import com.spendly.tracker.connector.domain.RefundMatchStatus
import com.spendly.tracker.connector.domain.WindowUnit
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class SpendAnalysisBuilderTest {

    private val today = LocalDate.of(2026, 3, 15)
    private val window = AnalysisWindow(WindowUnit.DAYS, 30)

    private fun tx(
        id: Long,
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String = "Store",
        category: String = "Shopping",
        date: LocalDateTime = LocalDateTime.of(2026, 3, 10, 12, 0),
        tags: String = "",
        loanId: Long? = null,
        transferKind: String? = null,
        excluded: Boolean = false,
        refundOf: Long? = null,
        description: String? = null,
        accountNumber: String? = null,
        bankName: String? = null
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = date,
        transactionHash = "h$id",
        tags = tags,
        loanId = loanId,
        transferKind = transferKind,
        isExcludedFromTracking = excluded,
        refundOfTransactionId = refundOf,
        description = description,
        accountNumber = accountNumber,
        bankName = bankName
    )

    private fun build(
        transactions: List<TransactionEntity>,
        previous: List<TransactionEntity> = emptyList(),
        splits: Map<Long, Map<String, BigDecimal>> = emptyMap(),
        loanDirections: Map<Long, LoanDirection> = emptyMap(),
        startDate: LocalDate = window.startDate(today),
        endDate: LocalDate = today
    ) = SpendAnalysisBuilder.build(
        transactions = transactions,
        previousTransactions = previous,
        window = window,
        startDate = startDate,
        endDate = endDate,
        currency = "INR",
        categorySplits = splits,
        loanDirections = loanDirections
    )

    // ---- Basics -------------------------------------------------------------

    @Test
    fun totalsIgnoreTransfersAndInvestments() {
        val analysis = build(
            listOf(
                tx(1, "100.00"),
                tx(2, "50.50", type = TransactionType.CREDIT),
                tx(3, "900.00", type = TransactionType.TRANSFER),
                tx(4, "700.00", type = TransactionType.INVESTMENT),
                tx(5, "1000.00", type = TransactionType.INCOME)
            )
        )

        assertEquals(BigDecimal("150.50"), analysis.grossExpense)
        assertEquals(BigDecimal("150.50"), analysis.netExpense)
        assertEquals(BigDecimal("1000.00"), analysis.totalIncome)
        assertEquals(2, analysis.expenseCount)
        assertEquals(1, analysis.incomeCount)
        assertEquals(BigDecimal("849.50"), analysis.netCashFlow)
    }

    @Test
    fun loanLinkedRowsAreNeverSpendingOrIncome() {
        val analysis = build(
            listOf(
                tx(1, "100.00"),
                tx(2, "5000.00", loanId = 7),
                tx(3, "2000.00", type = TransactionType.INCOME, loanId = 7),
                tx(4, "1200.00", type = TransactionType.INCOME)
            )
        )

        assertEquals(BigDecimal("100.00"), analysis.grossExpense)
        assertEquals(1, analysis.expenseCount)
        assertEquals(BigDecimal("1200.00"), analysis.totalIncome)
        assertEquals(1, analysis.incomeCount)
        // The window covers two calendar months; the spend sits in the March one.
        assertEquals(2, analysis.monthlyTrend.size)
        assertEquals(BigDecimal("100.00"), analysis.monthlyTrend.last().expense)
        assertEquals(BigDecimal("1200.00"), analysis.monthlyTrend.last().income)
    }

    @Test
    fun excludedRowsDoNotCount() {
        val analysis = build(
            listOf(
                tx(1, "100.00"),
                tx(2, "900.00", excluded = true)
            )
        )

        assertEquals(BigDecimal("100.00"), analysis.grossExpense)
        assertEquals(1, analysis.expenseCount)
        assertEquals(1, analysis.dataQuality.excludedTransactions)
        assertEquals(2, analysis.dataQuality.sourceTransactions)
        assertEquals(1, analysis.dataQuality.includedTransactions)
    }

    @Test
    fun moneyMovedIsReportedSeparately() {
        val analysis = build(
            listOf(
                tx(1, "100.00"),
                tx(
                    2,
                    "4000.00",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKind.CC_BILL_PAYMENT
                ),
                tx(
                    3,
                    "500.00",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKind.SELF_TRANSFER
                ),
                tx(
                    4,
                    "300.00",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKind.OTHERS_TRANSFER
                ),
                tx(5, "2000.00", type = TransactionType.INVESTMENT),
                tx(6, "1500.00", loanId = 9)
            )
        )

        val moved = analysis.moneyMoved!!
        assertEquals(BigDecimal("4000.00"), moved.creditCardBillPayments.amount)
        assertEquals(1, moved.creditCardBillPayments.transactionCount)
        assertEquals(BigDecimal("500.00"), moved.selfTransfers.amount)
        assertEquals(BigDecimal("300.00"), moved.transfersToOthers.amount)
        assertEquals(BigDecimal("2000.00"), moved.investments.amount)
        assertEquals(BigDecimal("1500.00"), moved.loanLinkedOutflow.amount)
        assertEquals(BigDecimal("8300.00"), moved.outflowTotal)
        assertTrue(analysis.renderPromptPayload().contains("MONEY_MOVED_NOT_COUNTED_AS_SPENDING"))
    }

    @Test
    fun splitCategoriesAreUsedForTheBreakdown() {
        val analysis = build(
            transactions = listOf(
                tx(1, "1000.00", category = "Shopping"),
                tx(2, "400.00", category = "Food")
            ),
            splits = mapOf(
                1L to mapOf("Electronics" to BigDecimal("600.00"), "Clothing" to BigDecimal("400.00"))
            )
        )

        val electronics = analysis.categories.first { it.category == "Electronics" }
        val clothing = analysis.categories.first { it.category == "Clothing" }
        assertEquals(BigDecimal("600.00"), electronics.grossExpense)
        assertEquals(BigDecimal("600.00"), electronics.netExpense)
        assertEquals(BigDecimal("400.00"), clothing.grossExpense)
        assertEquals(1, electronics.transactionCount)
    }

    @Test
    fun categorySharesAddUpToOneHundredPercent() {
        val analysis = build(
            listOf(
                tx(1, "300.00", category = "Food"),
                tx(2, "100.00", category = "Food"),
                tx(3, "600.00", category = "Transport")
            )
        )

        val food = analysis.categories.first { it.category == "Food" }
        assertEquals(BigDecimal("400.00"), food.netExpense)
        assertEquals(2, food.transactionCount)
        assertEquals(40.0, food.shareOfNetSpendingPercent, 0.01)

        val transport = analysis.categories.first { it.category == "Transport" }
        assertEquals(60.0, transport.shareOfNetSpendingPercent, 0.01)
    }

    @Test
    fun merchantAggregatesMaskUpiHandles() {
        val analysis = build(
            listOf(tx(1, "100.00", merchant = "9876543210@paytm"))
        )

        assertEquals("UPI contact", analysis.merchants.single().merchantName)
        assertEquals("UPI contact", analysis.largestExpenses.single().name)
    }

    @Test
    fun recurringMerchantsNeedThreeOccurrences() {
        val analysis = build(
            listOf(
                tx(1, "100.00", merchant = "Netflix"),
                tx(2, "100.00", merchant = "Netflix"),
                tx(3, "150.00", merchant = "Netflix"),
                tx(4, "20.00", merchant = "Cafe")
            )
        )

        val recurring = analysis.recurring.single()
        assertEquals("Netflix", recurring.merchantName)
        assertEquals(3, recurring.occurrences)
        assertEquals(BigDecimal("350.00"), recurring.amount)
        assertEquals(BigDecimal("116.67"), recurring.typicalAmount)
    }

    @Test
    fun weekdayTotalsAreGroupedByDayOfWeek() {
        // 2026-03-10 is a Tuesday, 2026-03-14 a Saturday.
        val analysis = build(
            listOf(
                tx(1, "100.00", date = LocalDateTime.of(2026, 3, 10, 9, 0)),
                tx(2, "200.00", date = LocalDateTime.of(2026, 3, 10, 18, 0)),
                tx(3, "50.00", date = LocalDateTime.of(2026, 3, 14, 11, 0))
            )
        )

        val tuesday = analysis.weekdaySpend.first { it.dayOfWeek == DayOfWeek.TUESDAY }
        val saturday = analysis.weekdaySpend.first { it.dayOfWeek == DayOfWeek.SATURDAY }
        assertEquals(BigDecimal("300.00"), tuesday.amount)
        assertEquals(2, tuesday.transactionCount)
        assertEquals(BigDecimal("50.00"), saturday.amount)
    }

    @Test
    fun changeAgainstPreviousPeriodIsReported() {
        val analysis = build(
            transactions = listOf(tx(1, "150.00")),
            previous = listOf(tx(2, "100.00"))
        )

        assertEquals(BigDecimal("100.00"), analysis.previousExpense)
        assertEquals(50.0, analysis.previousExpenseChangePercent!!, 0.01)
    }

    @Test
    fun changeIsNullWhenThereIsNoPreviousSpend() {
        val analysis = build(transactions = listOf(tx(1, "150.00")))
        assertNull(analysis.previousExpense)
        assertNull(analysis.previousExpenseChangePercent)
    }

    @Test
    fun tagsAreSplitAndSummed() {
        val analysis = build(
            listOf(
                tx(1, "100.00", tags = "work, travel"),
                tx(2, "50.00", tags = "work")
            )
        )

        val work = analysis.tags.first { it.tag == "work" }
        assertEquals(BigDecimal("150.00"), work.amount)
        assertEquals(2, work.transactionCount)
        assertTrue(analysis.tags.any { it.tag == "travel" })
    }

    @Test
    fun monthlyTrendSplitsThePeriodByMonth() {
        val analysis = build(
            listOf(
                tx(1, "100.00", date = LocalDateTime.of(2026, 2, 20, 9, 0)),
                tx(2, "200.00", date = LocalDateTime.of(2026, 3, 5, 9, 0))
            ),
            startDate = LocalDate.of(2026, 2, 20),
            endDate = LocalDate.of(2026, 3, 15)
        )

        assertEquals(2, analysis.monthlyTrend.size)
        assertEquals(BigDecimal("100.00"), analysis.monthlyTrend.first().expense)
        assertEquals(BigDecimal("200.00"), analysis.monthlyTrend.last().expense)
    }

    @Test
    fun promptPayloadContainsTheAggregates() {
        val analysis = build(listOf(tx(1, "300.00", category = "Food")))
        val payload = analysis.renderPromptPayload()

        assertTrue(payload.contains("gross_expense: 300.00"))
        assertTrue(payload.contains("net_expense: 300.00"))
        assertTrue(payload.contains("BY_CATEGORY"))
        assertTrue(payload.contains("Food"))
        assertTrue(payload.contains("currency: INR"))
    }

    @Test
    fun promptPayloadNeverContainsRawTransactionFields() {
        val analysis = build(
            listOf(
                tx(
                    1,
                    "300.00",
                    merchant = "Cafe",
                    category = "Food",
                    tags = "work"
                )
            )
        )
        val payload = analysis.renderPromptPayload()

        // Only aggregates are shared: no descriptions, SMS bodies or account numbers.
        assertFalse(payload.contains("sms_body"))
        assertFalse(payload.contains("account_number"))
        assertFalse(payload.contains("bank_name"))
    }

    // ---- Credit card settlement --------------------------------------------

    @Test
    fun creditCardPurchaseIsSpendingButTheBillPaymentIsNot() {
        val analysis = build(
            listOf(
                tx(1, "5000.00", type = TransactionType.CREDIT, merchant = "Amazon", category = "Shopping"),
                tx(
                    2,
                    "5000.00",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKind.CC_BILL_PAYMENT,
                    merchant = "Card Payment"
                )
            )
        )

        assertEquals(BigDecimal("5000.00"), analysis.grossExpense)
        assertEquals(BigDecimal("5000.00"), analysis.netExpense)
        assertEquals(1, analysis.expenseCount)
        assertEquals(BigDecimal("5000.00"), analysis.moneyMoved!!.creditCardBillPayments.amount)
    }

    @Test
    fun creditCardPurchaseIsStillAnExpenseNotASeparateType() {
        val brief = briefOf(tx(1, "5000.00", type = TransactionType.CREDIT, merchant = "Amazon"))

        assertEquals(FinancialType.EXPENSE, brief.type)
        assertEquals(PaymentMethod.CREDIT_CARD, brief.paymentMethod)
    }

    // ---- Refunds ------------------------------------------------------------

    @Test
    fun fullRefundReducesTheCategoryToZero() {
        val analysis = build(
            listOf(
                tx(1, "5000.00", merchant = "Apollo Hospital", category = "Healthcare"),
                tx(
                    2,
                    "5000.00",
                    type = TransactionType.INCOME,
                    merchant = "Apollo Hospital",
                    category = "Income",
                    refundOf = 1L
                )
            )
        )

        val healthcare = analysis.categories.first { it.category == "Healthcare" }
        assertEquals(BigDecimal("5000.00"), healthcare.grossExpense)
        assertEquals(BigDecimal("5000.00"), healthcare.refundsReceived)
        assertEquals(BigDecimal("0.00"), healthcare.netExpense)
        assertEquals(BigDecimal("5000.00"), analysis.grossExpense)
        assertEquals(BigDecimal("5000.00"), analysis.refundsReceived)
        assertEquals(BigDecimal("0.00"), analysis.netExpense)
        assertEquals(BigDecimal("0.00"), analysis.totalIncome)
    }

    @Test
    fun partialRefundOnlyReducesTheCategoryPartly() {
        val analysis = build(
            listOf(
                tx(1, "10000.00", merchant = "Apollo Hospital", category = "Healthcare"),
                tx(
                    2,
                    "4000.00",
                    type = TransactionType.INCOME,
                    merchant = "Apollo Hospital",
                    category = "Income",
                    refundOf = 1L
                )
            )
        )

        val healthcare = analysis.categories.first { it.category == "Healthcare" }
        assertEquals(BigDecimal("10000.00"), healthcare.grossExpense)
        assertEquals(BigDecimal("4000.00"), healthcare.refundsReceived)
        assertEquals(BigDecimal("6000.00"), healthcare.netExpense)
        assertEquals(BigDecimal("6000.00"), analysis.netExpense)
    }

    @Test
    fun insuranceReimbursementIsReportedSeparatelyAndNotAsIncome() {
        val analysis = build(
            listOf(
                tx(1, "110787.00", merchant = "Apollo Hospital", category = "Healthcare"),
                tx(
                    2,
                    "63899.00",
                    type = TransactionType.INCOME,
                    merchant = "Star Health Insurance",
                    category = "Income",
                    refundOf = 1L
                ),
                tx(3, "197235.00", type = TransactionType.INCOME, merchant = "Employer", category = "Salary")
            )
        )

        assertEquals(BigDecimal("110787.00"), analysis.grossExpense)
        assertEquals(BigDecimal("63899.00"), analysis.refundsReceived)
        assertEquals(BigDecimal("46888.00"), analysis.netExpense)
        // The reimbursement is not salary: income only counts the real pay.
        assertEquals(BigDecimal("197235.00"), analysis.totalIncome)
        assertEquals(1, analysis.incomeCount)

        val healthcare = analysis.categories.first { it.category == "Healthcare" }
        assertEquals(BigDecimal("110787.00"), healthcare.grossExpense)
        assertEquals(BigDecimal("63899.00"), healthcare.refundsReceived)
        assertEquals(BigDecimal("46888.00"), healthcare.netExpense)

        val insurance = analysis.refunds.byKind.first {
            it.kind == RefundKind.HEALTHCARE_INSURANCE_REIMBURSEMENT
        }
        assertEquals(BigDecimal("63899.00"), insurance.amount)
        assertEquals(RefundMatchStatus.MATCHED, insurance.matchStatus)
    }

    @Test
    fun unmatchedRefundIsReportedAndNotPushedIntoACategory() {
        val analysis = build(
            listOf(
                tx(1, "1000.00", merchant = "Store", category = "Shopping"),
                tx(
                    2,
                    "300.00",
                    type = TransactionType.INCOME,
                    merchant = "Corp Insurance Reimbursement",
                    category = "Income"
                )
            )
        )

        assertEquals(1, analysis.refunds.unmatched.transactionCount)
        assertEquals(BigDecimal("300.00"), analysis.refunds.unmatched.amount)
        assertEquals(BigDecimal("300.00"), analysis.refundsReceived)
        assertEquals(BigDecimal("700.00"), analysis.netExpense)
        // The unmatched refund is never income.
        assertEquals(BigDecimal("0.00"), analysis.totalIncome)
        assertEquals(1, analysis.dataQuality.unmatchedRefundCount)
        assertEquals(BigDecimal("300.00"), analysis.dataQuality.unmatchedRefundAmount)
        // Category totals therefore differ from the headline by the unmatched refund.
        assertEquals(
            BigDecimal("300.00"),
            analysis.dataQuality.categoryToSummaryDifference
        )
        assertTrue(analysis.refunds.unmatchedDetails.single().merchant.contains("Corp Insurance"))
    }

    @Test
    fun refundReceivedForAnExpenseOutsideThePeriodIsUnmatched() {
        val analysis = build(
            listOf(
                tx(
                    2,
                    "300.00",
                    type = TransactionType.INCOME,
                    merchant = "Store Refund",
                    category = "Income",
                    refundOf = 99L
                )
            )
        )

        assertEquals(1, analysis.refunds.unmatched.transactionCount)
        assertEquals(0, analysis.refunds.matched.transactionCount)
    }

    @Test
    fun refundsAreSplitPerKind() {
        val analysis = build(
            listOf(
                tx(1, "1000.00", merchant = "Store", category = "Shopping"),
                tx(
                    2,
                    "200.00",
                    type = TransactionType.INCOME,
                    merchant = "Store",
                    category = "Income",
                    refundOf = 1L
                ),
                tx(
                    3,
                    "50.00",
                    type = TransactionType.INCOME,
                    merchant = "Card Cashback",
                    category = "Income"
                )
            )
        )

        val kinds = analysis.refunds.byKind.associate { it.kind to it.amount }
        assertEquals(BigDecimal("200.00"), kinds[RefundKind.MERCHANT_REFUND])
        assertEquals(BigDecimal("50.00"), kinds[RefundKind.CREDIT_CARD_CASHBACK])
    }

    // ---- Loans --------------------------------------------------------------

    @Test
    fun borrowedMoneyIsADisbursementAndTheRepaymentIsALoanPayment() {
        val analysis = build(
            listOf(
                tx(1, "50000.00", type = TransactionType.INCOME, loanId = 7),
                tx(2, "10000.00", type = TransactionType.EXPENSE, loanId = 7)
            ),
            loanDirections = mapOf(7L to LoanDirection.BORROWED)
        )

        assertEquals(BigDecimal("0.00"), analysis.grossExpense)
        assertEquals(BigDecimal("0.00"), analysis.totalIncome)
        val moved = analysis.moneyMoved!!
        assertEquals(BigDecimal("10000.00"), moved.loanLinkedOutflow.amount)
        assertEquals(BigDecimal("50000.00"), moved.loanLinkedInflow.amount)
    }

    @Test
    fun moneyLentOutIsADisbursementAndTheRepaymentIsALoanPayment() {
        val analysis = build(
            listOf(
                tx(1, "50000.00", type = TransactionType.EXPENSE, loanId = 7),
                tx(2, "10000.00", type = TransactionType.INCOME, loanId = 7)
            ),
            loanDirections = mapOf(7L to LoanDirection.LENT)
        )

        val moved = analysis.moneyMoved!!
        assertEquals(BigDecimal("50000.00"), moved.loanLinkedOutflow.amount)
        assertEquals(BigDecimal("10000.00"), moved.loanLinkedInflow.amount)
    }

    // ---- Months -------------------------------------------------------------

    @Test
    fun everyMonthOfThePeriodIsPresentEvenWithoutTransactions() {
        val analysis = build(
            listOf(tx(1, "100.00", date = LocalDateTime.of(2026, 1, 10, 9, 0))),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 3, 15)
        )

        assertEquals(3, analysis.months.size)
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), analysis.months.map { it.label })
        assertEquals(BigDecimal("0.00"), analysis.months[1].netExpense)
        assertEquals(BigDecimal("100.00"), analysis.months[0].netExpense)
    }

    @Test
    fun partialMonthsAreFlagged() {
        val analysis = build(
            listOf(tx(1, "100.00", date = LocalDateTime.of(2026, 1, 10, 9, 0))),
            startDate = LocalDate.of(2026, 1, 20),
            endDate = LocalDate.of(2026, 2, 10)
        )

        assertTrue(analysis.months[0].isPartial)
        assertTrue(analysis.months[1].isPartial)
        assertEquals(12, analysis.months[0].daysInPeriod)
        assertEquals(10, analysis.months[1].daysInPeriod)
        assertEquals(2, analysis.dataQuality.partialMonths)
    }

    @Test
    fun aCompleteMonthIsNotFlaggedAsPartial() {
        val analysis = build(
            listOf(tx(1, "100.00", date = LocalDateTime.of(2026, 1, 10, 9, 0))),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31)
        )

        assertFalse(analysis.months.single().isPartial)
        assertEquals(31, analysis.months.single().daysInPeriod)
        assertEquals(0, analysis.dataQuality.partialMonths)
    }

    @Test
    fun monthlyRowsCarryIncomeSpendAndSavings() {
        val analysis = build(
            listOf(
                tx(
                    1,
                    "10000.00",
                    type = TransactionType.INCOME,
                    merchant = "Employer",
                    category = "Salary",
                    date = LocalDateTime.of(2026, 1, 5, 9, 0)
                ),
                tx(
                    2,
                    "4000.00",
                    merchant = "Rent",
                    category = "Rent",
                    date = LocalDateTime.of(2026, 1, 6, 9, 0)
                ),
                tx(
                    3,
                    "1000.00",
                    merchant = "Cafe",
                    category = "Food & Dining",
                    date = LocalDateTime.of(2026, 1, 7, 9, 0)
                )
            ),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31)
        )

        val month = analysis.months.single()
        assertEquals(BigDecimal("10000.00"), month.income)
        assertEquals(BigDecimal("5000.00"), month.grossExpense)
        assertEquals(BigDecimal("5000.00"), month.netExpense)
        assertEquals(BigDecimal("5000.00"), month.netCashFlow)
        assertEquals(50.0, month.savingsRatePercent!!, 0.01)
        assertEquals(2, month.categories.size)
    }

    @Test
    fun monthlyCategoriesReconcileWithTheMonthTotal() {
        val analysis = build(
            listOf(
                tx(1, "4000.00", merchant = "Rent", category = "Rent"),
                tx(2, "1000.00", merchant = "Cafe", category = "Food & Dining"),
                tx(3, "500.00", merchant = "Store", category = "Shopping")
            ),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31)
        )

        val month = analysis.months.single()
        val categoryTotal = month.categories.fold(BigDecimal.ZERO) { acc, item -> acc + item.netExpense }
        assertEquals(0, month.netExpense.compareTo(categoryTotal))
    }

    // ---- Health -------------------------------------------------------------

    @Test
    fun healthSeparatesCashSavingsFromInvestments() {
        val analysis = build(
            listOf(
                tx(1, "100000.00", type = TransactionType.INCOME, merchant = "Employer", category = "Salary"),
                tx(2, "40000.00", merchant = "Rent", category = "Rent"),
                tx(3, "10000.00", type = TransactionType.INVESTMENT, merchant = "Mutual Fund")
            ),
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31)
        )

        val health = analysis.health!!
        assertEquals(BigDecimal("100000.00"), health.totalIncome)
        assertEquals(BigDecimal("40000.00"), health.totalNetSpending)
        assertEquals(BigDecimal("60000.00"), health.cashSavings)
        assertEquals(BigDecimal("10000.00"), health.totalInvestments)
        assertEquals(10.0, health.investmentRatePercent!!, 0.01)
        assertEquals(60.0, health.savingsRatePercent!!, 0.01)
        // Without asset and liability data, net worth change is unknown.
        assertNull(health.netWorthAccumulation)
        assertNull(health.providentFundContribution)
    }

    @Test
    fun healthRatiosUseNetSpending() {
        val analysis = build(
            listOf(
                tx(1, "6000.00", merchant = "Store", category = "Groceries"),
                tx(2, "4000.00", merchant = "Cafe", category = "Food & Dining")
            )
        )

        val health = analysis.health!!
        assertEquals(60.0, health.essentialRatioPercent!!, 0.01)
        assertEquals(40.0, health.discretionaryRatioPercent!!, 0.01)
    }

    @Test
    fun recurringMerchantsBecomeFixedExpenses() {
        val analysis = build(
            listOf(
                tx(1, "649.00", merchant = "Netflix", category = "Entertainment"),
                tx(2, "649.00", merchant = "Netflix", category = "Entertainment"),
                tx(3, "649.00", merchant = "Netflix", category = "Entertainment")
            )
        )

        val entertainment = analysis.categories.first { it.category == "Entertainment" }
        assertEquals(ExpenseBehaviour.FIXED, entertainment.behaviour)
        assertEquals(Essentiality.DISCRETIONARY, entertainment.essentiality)
        assertEquals(BigDecimal("1947.00"), analysis.health!!.recurringExpenseTotal)
    }

    @Test
    fun categoryClassificationFallsBackToUnknown() {
        val analysis = build(listOf(tx(1, "100.00", category = "")))

        val category = analysis.categories.single()
        assertEquals("Uncategorised", category.category)
        assertEquals(Essentiality.UNKNOWN, category.essentiality)
        assertEquals(ExpenseBehaviour.UNKNOWN, category.behaviour)
    }

    // ---- Data quality -------------------------------------------------------

    @Test
    fun duplicateTransactionsAreCounted() {
        val analysis = build(
            listOf(
                tx(1, "500.00", merchant = "Cafe", category = "Food"),
                tx(2, "500.00", merchant = "Cafe", category = "Food")
            )
        )

        assertEquals(1, analysis.dataQuality.duplicateTransactions)
    }

    @Test
    fun summaryReconcilesWithTheTransactionRows() {
        val analysis = build(
            listOf(
                tx(1, "5000.00", merchant = "Apollo Hospital", category = "Healthcare"),
                tx(
                    2,
                    "2000.00",
                    type = TransactionType.INCOME,
                    merchant = "Apollo Hospital",
                    category = "Income",
                    refundOf = 1L
                ),
                tx(3, "10000.00", type = TransactionType.INCOME, merchant = "Employer", category = "Salary"),
                tx(4, "500.00", type = TransactionType.TRANSFER, transferKind = TransferKind.SELF_TRANSFER),
                tx(5, "1000.00", type = TransactionType.INVESTMENT, merchant = "MF")
            )
        )

        assertEquals(
            BigDecimal("0.00"),
            analysis.dataQuality.summaryToTransactionsDifference
        )
        assertEquals(BigDecimal("3000.00"), analysis.netExpense)
        assertEquals(BigDecimal("10000.00"), analysis.totalIncome)
    }

    @Test
    fun uncategorizedAndUnknownPaymentRowsAreReported() {
        val analysis = build(
            listOf(
                tx(1, "100.00", category = ""),
                tx(2, "100.00", merchant = "Mystery Shop")
            )
        )

        assertEquals(1, analysis.dataQuality.uncategorizedTransactions)
        assertEquals(2, analysis.dataQuality.unknownPaymentMethodCount)
    }

    // ---- Helpers ------------------------------------------------------------

    private fun briefOf(transaction: TransactionEntity) =
        TransactionBriefMapper.from(listOf(transaction)).single()
}
