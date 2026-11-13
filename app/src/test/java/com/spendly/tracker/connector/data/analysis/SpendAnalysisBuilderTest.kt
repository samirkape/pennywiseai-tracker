package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.WindowUnit
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        tags: String = ""
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = date,
        transactionHash = "h$id",
        tags = tags
    )

    private fun build(
        transactions: List<TransactionEntity>,
        previous: List<TransactionEntity> = emptyList()
    ) = SpendAnalysisBuilder.build(
        transactions = transactions,
        previousTransactions = previous,
        window = window,
        startDate = window.startDate(today),
        endDate = today,
        currency = "INR",
        today = today
    )

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

        assertEquals(BigDecimal("150.50"), analysis.totalExpense)
        assertEquals(BigDecimal("1000.00"), analysis.totalIncome)
        assertEquals(2, analysis.expenseCount)
        assertEquals(1, analysis.incomeCount)
        assertEquals(BigDecimal("849.50"), analysis.netCashFlow)
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
        assertEquals(BigDecimal("400.00"), food.amount)
        assertEquals(2, food.transactionCount)
        assertEquals(40.0, food.sharePercent, 0.01)

        val transport = analysis.categories.first { it.category == "Transport" }
        assertEquals(60.0, transport.sharePercent, 0.01)
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
            )
        )

        assertEquals(2, analysis.monthlyTrend.size)
        assertEquals(BigDecimal("100.00"), analysis.monthlyTrend.first().expense)
        assertEquals(BigDecimal("200.00"), analysis.monthlyTrend.last().expense)
    }

    @Test
    fun promptPayloadContainsTheAggregates() {
        val analysis = build(listOf(tx(1, "300.00", category = "Food")))
        val payload = analysis.renderPromptPayload()

        assertTrue(payload.contains("total_spend: 300.00"))
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
}
