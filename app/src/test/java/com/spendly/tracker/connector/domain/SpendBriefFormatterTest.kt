package com.spendly.tracker.connector.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class SpendBriefFormatterTest {

    private val generatedAt = LocalDateTime.of(2026, 11, 14, 9, 30)

    private val analysis = SpendAnalysis(
        window = AnalysisWindow.LAST_3_MONTHS,
        startDate = LocalDate.of(2026, 8, 15),
        endDate = LocalDate.of(2026, 11, 14),
        currency = "INR",
        totalExpense = BigDecimal("40733.00"),
        totalIncome = BigDecimal("197235.00"),
        expenseCount = 25,
        incomeCount = 2,
        dailyAverage = BigDecimal("442.00"),
        categories = listOf(CategorySpend("Groceries", BigDecimal("1200.00"), 12.5, 4)),
        merchants = listOf(MerchantSpend("Corner Store", BigDecimal("1200.00"), 4)),
        monthlyTrend = emptyList(),
        recurring = emptyList(),
        largestExpenses = emptyList(),
        weekdaySpend = emptyList(),
        previousExpense = BigDecimal("30000.00"),
        previousExpenseChangePercent = 35.8,
        tags = emptyList()
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
        assertTrue(brief.contains("- Groceries | 1200.00 | 12.5 | 4"))
    }

    @Test
    fun transactionListIsRenderedWhenRequested() {
        val brief = SpendBriefFormatter.format(
            analysis = analysis,
            transactions = listOf(
                TransactionBrief(
                    date = LocalDate.of(2026, 11, 12),
                    type = TransactionBrief.Type.EXPENSE,
                    amount = BigDecimal("250.00"),
                    category = "Food",
                    merchant = "Blue Tokai"
                ),
                TransactionBrief(
                    date = LocalDate.of(2026, 11, 10),
                    type = TransactionBrief.Type.CARD,
                    amount = BigDecimal("1499.00"),
                    category = "Shopping",
                    merchant = "Amazon"
                )
            ),
            generatedAt = generatedAt
        )

        assertTrue(brief.contains("TRANSACTIONS (date | type | amount | category | merchant)"))
        assertTrue(brief.contains("- 2026-11-12 | expense | 250.00 | Food | Blue Tokai"))
        assertTrue(brief.contains("- 2026-11-10 | card | 1499.00 | Shopping | Amazon"))
        assertTrue(brief.contains("covers spending and income rows only"))
    }

    @Test
    fun transactionSectionIsOmittedWhenEmpty() {
        val brief = SpendBriefFormatter.format(analysis, generatedAt = generatedAt)
        assertFalse(brief.contains("TRANSACTIONS ("))
        assertFalse(brief.contains("covers spending and income rows only"))
    }
}
