package com.spendly.tracker.connector.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class SpendBriefFormatterTest {

    private val generatedAt = LocalDateTime.of(2026, 11, 14, 9, 30)

    private val analysis = SpendAnalysis(
        window = AnalysisWindow.LAST_3_MONTHS,
        startDate = LocalDate.of(2026, 8, 15),
        endDate = LocalDate.of(2026, 11, 14),
        currency = "INR",
        grossExpense = BigDecimal("40733.00"),
        refundsReceived = BigDecimal("1500.00"),
        netExpense = BigDecimal("39233.00"),
        totalIncome = BigDecimal("197235.00"),
        expenseCount = 25,
        incomeCount = 2,
        refundCount = 3,
        dailyAverage = BigDecimal("442.00"),
        categories = listOf(
            CategorySpend(
                category = "Groceries",
                grossExpense = BigDecimal("1200.00"),
                refundsReceived = BigDecimal("0.00"),
                netExpense = BigDecimal("1200.00"),
                shareOfNetSpendingPercent = 12.5,
                transactionCount = 4,
                essentiality = Essentiality.ESSENTIAL,
                behaviour = ExpenseBehaviour.VARIABLE
            )
        ),
        merchants = listOf(MerchantSpend("Corner Store", BigDecimal("1200.00"), 4)),
        monthlyTrend = emptyList(),
        months = emptyList(),
        recurring = emptyList(),
        largestExpenses = emptyList(),
        weekdaySpend = emptyList(),
        previousExpense = BigDecimal("30000.00"),
        previousExpenseChangePercent = 35.8,
        tags = emptyList(),
        refunds = RefundSummary(
            total = BigDecimal("1500.00"),
            matched = FlowSummary(BigDecimal("1200.00"), 2),
            unmatched = FlowSummary(BigDecimal("300.00"), 1),
            byKind = listOf(
                RefundKindSpend(
                    RefundKind.MERCHANT_REFUND,
                    BigDecimal("1200.00"),
                    2,
                    RefundMatchStatus.MATCHED
                )
            ),
            unmatchedDetails = listOf(
                UnmatchedRefund(LocalDate.of(2026, 10, 2), BigDecimal("300.00"), "Corp Insurance")
            )
        ),
        dataQuality = DataQualityReport(
            periodStart = LocalDate.of(2026, 8, 15),
            periodEnd = LocalDate.of(2026, 11, 14),
            periodDays = 92,
            periodMonths = 4,
            partialMonths = 2,
            sourceTransactions = 40,
            includedTransactions = 38,
            excludedTransactions = 2,
            duplicateTransactions = 1,
            uncategorizedTransactions = 2,
            unmatchedRefundCount = 1,
            unmatchedRefundAmount = BigDecimal("300.00"),
            unknownPaymentMethodCount = 12,
            missingMerchantCount = 1,
            summaryToTransactionsDifference = BigDecimal("0.00"),
            categoryToSummaryDifference = BigDecimal("300.00"),
            categoriesOmitted = 0,
            merchantsOmitted = 0
        )
    )

    @Test
    fun briefContainsTheSamePayloadTheConnectorSends() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertTrue(brief.contains(analysis.renderPromptPayload().trimEnd()))
    }

    @Test
    fun briefSetsPrivacyExpectationsAndTimestamp() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertTrue(brief.startsWith("SPENDLY SPENDING BRIEF"))
        assertTrue(brief.contains("Generated: 14 Nov 2026, 09:30"))
        assertTrue(brief.contains("no messages"))
        assertTrue(brief.contains("account numbers"))
    }

    @Test
    fun briefExplainsHowTheNumbersAreBuilt() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertTrue(brief.contains("HOW THE NUMBERS ARE BUILT"))
        assertTrue(brief.contains("gross_expense is every spending row"))
        assertTrue(brief.contains("Reimbursements are never income"))
        assertTrue(brief.contains("the card bill payment that"))
    }

    @Test
    fun blankQuestionIsOmitted() {
        val brief = SpendBriefFormatter.format(analysis, question = "   ", generatedAt = generatedAt)
        assertFalse(brief.contains("QUESTION"))
    }

    @Test
    fun questionIsIncludedWhenProvided() {
        val brief = SpendBriefFormatter.format(
            analysis = analysis,
            question = "  Where is my money going?  ",
            generatedAt = generatedAt
        )
        assertTrue(brief.contains("QUESTION\nWhere is my money going?"))
    }

    @Test
    fun payloadKeepsPlainDecimalAmounts() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertTrue(brief.contains("currency: INR"))
        assertTrue(brief.contains("gross_expense: 40733.00"))
        assertTrue(brief.contains("refunds_received: 1500.00"))
        assertTrue(brief.contains("net_expense: 39233.00"))
        assertTrue(brief.contains("- Groceries | 1200.00 | 0.00 | 1200.00 | 12.5 | 4 | essential | variable"))
    }

    @Test
    fun refundsAndDataQualityAreReported() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertTrue(brief.contains("REFUNDS_AND_REIMBURSEMENTS"))
        assertTrue(brief.contains("unmatched_refunds: 300.00 (1)"))
        assertTrue(brief.contains("- 2026-10-02 | 300.00 | Corp Insurance"))
        assertTrue(brief.contains("DATA_QUALITY"))
        assertTrue(brief.contains("summary_to_transactions_difference: 0.00"))
        assertTrue(brief.contains("duplicate_transactions: 1"))
    }

    @Test
    fun transactionListIsRenderedWhenRequested() {
        val brief = SpendBriefFormatter.format(
            analysis = analysis,
            transactions = listOf(
                TransactionBrief(
                    transactionId = "txn_000012",
                    date = LocalDate.of(2026, 11, 12),
                    type = FinancialType.EXPENSE,
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    category = "Food",
                    subcategory = null,
                    merchantOriginal = "Blue Tokai",
                    merchantNormalized = "blue tokai",
                    paymentMethod = PaymentMethod.UPI,
                    accountAlias = "Account 1",
                    tag = "work",
                    linkedTransactionId = null,
                    notes = null,
                    essentiality = Essentiality.DISCRETIONARY,
                    behaviour = ExpenseBehaviour.VARIABLE,
                    refundKind = null,
                    refundMatchStatus = null
                ),
                TransactionBrief(
                    transactionId = "txn_000011",
                    date = LocalDate.of(2026, 11, 10),
                    type = FinancialType.EXPENSE,
                    amount = BigDecimal("1499.00"),
                    currency = "INR",
                    category = "Shopping",
                    subcategory = null,
                    merchantOriginal = "Amazon",
                    merchantNormalized = "amazon",
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    accountAlias = null,
                    tag = null,
                    linkedTransactionId = null,
                    notes = null,
                    essentiality = Essentiality.DISCRETIONARY,
                    behaviour = ExpenseBehaviour.VARIABLE,
                    refundKind = null,
                    refundMatchStatus = null
                )
            ),
            generatedAt = generatedAt
        )

        assertTrue(
            brief.contains(
                "TRANSACTIONS (transaction_id | date | type | amount | currency | category | " +
                    "subcategory | merchant_original | merchant_normalized | payment_method | " +
                    "account_alias | tag | linked_transaction_id | notes | essentiality | " +
                    "behaviour | refund_kind | refund_status)"
            )
        )
        assertTrue(
            brief.contains(
                "- txn_000012 | 2026-11-12 | expense | 250.00 | INR | Food | - | Blue Tokai | " +
                    "blue tokai | UPI | Account 1 | work | - | - | discretionary | variable | - | -"
            )
        )
        assertTrue(brief.contains("| credit_card | - | - | - | - |"))
    }

    @Test
    fun transactionSectionIsOmittedWhenEmpty() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertFalse(brief.contains("TRANSACTIONS ("))
        assertFalse(brief.contains("The transaction list below covers every row"))
    }

    @Test
    fun monthlySectionShowsPartialMonthsAndCashFlow() {
        val withMonths = analysis.copy(
            months = listOf(
                MonthBreakdown(
                    month = YearMonth.of(2026, 11),
                    label = "2026-11",
                    isPartial = true,
                    daysInPeriod = 14,
                    income = BigDecimal("50000.00"),
                    grossExpense = BigDecimal("12000.00"),
                    refunds = BigDecimal("0.00"),
                    netExpense = BigDecimal("12000.00"),
                    netCashFlow = BigDecimal("38000.00"),
                    savingsRatePercent = 76.0,
                    investments = BigDecimal("5000.00"),
                    transfersOut = BigDecimal("2000.00"),
                    transfersIn = BigDecimal("0.00"),
                    loanInflow = BigDecimal("0.00"),
                    loanOutflow = BigDecimal("0.00"),
                    categories = listOf(
                        CategoryMonthSpend(
                            category = "Groceries",
                            grossExpense = BigDecimal("12000.00"),
                            refundsReceived = BigDecimal("0.00"),
                            netExpense = BigDecimal("12000.00"),
                            transactionCount = 4
                        )
                    )
                )
            )
        )

        val brief = SpendBriefFormatter.format(withMonths, generatedAt = generatedAt)
        assertTrue(brief.contains("MONTHLY_BREAKDOWN"))
        assertTrue(
            brief.contains(
                "- 2026-11 | partial | 14 | 50000.00 | 12000.00 | 0.00 | 12000.00 | 38000.00 | " +
                    "76.0 | 5000.00 | 2000.00 | 0.00 | 0.00 | 0.00"
            )
        )
        assertTrue(
            brief.contains(
                "- 2026-11 | Groceries | 12000.00 | 0.00 | 12000.00 | 4"
            )
        )
    }

    @Test
    fun moneyMovedKeepsItsOriginalSection() {
        val withMoved = analysis.copy(
            moneyMoved = MoneyMovedSummary(
                creditCardBillPayments = FlowSummary(BigDecimal("4000.00"), 1),
                selfTransfers = FlowSummary(BigDecimal("500.00"), 1),
                transfersToOthers = FlowSummary(BigDecimal("300.00"), 1),
                investments = FlowSummary(BigDecimal("2000.00"), 1),
                loanLinkedOutflow = FlowSummary(BigDecimal("1500.00"), 1),
                loanLinkedInflow = FlowSummary(BigDecimal("0.00"), 0),
                monthly = listOf(
                    MonthMoneyMoved(
                        label = "2026-11",
                        creditCardBillPayments = BigDecimal("4000.00"),
                        selfTransfers = BigDecimal("500.00"),
                        transfersToOthers = BigDecimal("0.00"),
                        investments = BigDecimal("2000.00"),
                        loanLinkedOutflow = BigDecimal("0.00"),
                        loanLinkedInflow = BigDecimal("0.00")
                    )
                )
            )
        )

        val brief = SpendBriefFormatter.format(withMoved, generatedAt = generatedAt)
        assertTrue(brief.contains("MONEY_MOVED_NOT_COUNTED_AS_SPENDING"))
        assertTrue(brief.contains("credit_card_bill_payments: 4000.00 (1)"))
        assertTrue(brief.contains("transfers_to_own_accounts: 500.00 (1)"))
        assertTrue(brief.contains("MONEY_MOVED_BY_MONTH"))
        assertTrue(brief.contains("- 2026-11 | 4000.00 | 500.00 | 0.00 | 2000.00 | 0.00 | 0.00"))
    }

    @Test
    fun financialHealthStatesItsNumbers() {
        val withHealth = analysis.copy(
            health = FinancialHealth(
                months = 4,
                totalIncome = BigDecimal("197235.00"),
                averageMonthlyIncome = BigDecimal("49308.75"),
                totalNetSpending = BigDecimal("39233.00"),
                averageMonthlySpending = BigDecimal("9808.25"),
                medianMonthlySpending = BigDecimal("9000.00"),
                averageMonthlySavings = BigDecimal("39500.25"),
                savingsRatePercent = 80.1,
                essentialRatioPercent = 55.0,
                discretionaryRatioPercent = 35.0,
                fixedRatioPercent = 22.0,
                investmentRatePercent = 12.0,
                totalInvestments = BigDecimal("23668.20"),
                cashSavings = BigDecimal("158002.00"),
                recurringExpenseTotal = BigDecimal("3000.00"),
                topCategories = emptyList(),
                spikes = listOf(
                    SpendingSpike("2026-10", BigDecimal("20000.00"), 2.2)
                ),
                lastMonthChangePercent = 12.5,
                providentFundContribution = null,
                netWorthAccumulation = null
            )
        )

        val brief = SpendBriefFormatter.format(withHealth, generatedAt = generatedAt)
        assertTrue(brief.contains("FINANCIAL_HEALTH"))
        assertTrue(brief.contains("median_monthly_spending: 9000.00"))
        assertTrue(brief.contains("essential_spending_ratio_percent: 55.0"))
        assertTrue(brief.contains("provident_fund_contribution: unavailable"))
        assertTrue(brief.contains("net_worth_accumulation: unavailable"))
        assertTrue(brief.contains("SPENDING_SPIKES (month | amount | times_median)"))
        assertTrue(brief.contains("- 2026-10 | 20000.00 | 2.2"))
    }
}
