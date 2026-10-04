package com.spendly.tracker.ui.screens.analytics

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsRateModelsTest {

    private val jan = YearMonth.of(2026, 1)
    private val feb = YearMonth.of(2026, 2)
    private val ranges = listOf(
        jan to (LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 1, 31)),
        feb to (LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28)),
    )

    private fun tx(
        type: TransactionType,
        amount: Int,
        date: LocalDate,
        excluded: Boolean = false,
        loanId: Long? = null,
        currency: String = "INR",
    ) = TransactionEntity(
        amount = BigDecimal(amount),
        merchantName = "m",
        category = "c",
        transactionType = type,
        dateTime = date.atTime(12, 0),
        transactionHash = "h-$type-$amount-$date-$excluded-$loanId-$currency",
        isExcludedFromTracking = excluded,
        loanId = loanId,
        currency = currency,
    )

    private fun compute(txs: List<TransactionEntity>) = runBlocking {
        computeMonthlySavingsRates(txs, ranges, feb) { it.amount }
    }

    @Test
    fun ratePercent_isSavedOverIncome() {
        val result = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.EXPENSE, 400, LocalDate.of(2026, 1, 6)),
            ),
        )
        assertEquals(60f, result[0].ratePercent!!, 0.01f)
    }

    @Test
    fun overspending_givesNegativeRate() {
        val result = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.EXPENSE, 1500, LocalDate.of(2026, 1, 6)),
            ),
        )
        assertEquals(-50f, result[0].ratePercent!!, 0.01f)
    }

    @Test
    fun noIncome_hasNoRate() {
        val result = compute(listOf(tx(TransactionType.EXPENSE, 300, LocalDate.of(2026, 1, 6))))
        assertNull(result[0].ratePercent)
    }

    @Test
    fun investments_doNotReduceSavings() {
        val result = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.INVESTMENT, 300, LocalDate.of(2026, 1, 6)),
            ),
        )
        assertEquals(BigDecimal.ZERO, result[0].spent)
        assertEquals(100f, result[0].ratePercent!!, 0.01f)
    }

    @Test
    fun creditCardSpend_countsAsSpending() {
        val result = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.CREDIT, 250, LocalDate.of(2026, 1, 6)),
            ),
        )
        assertEquals(BigDecimal(250), result[0].spent)
    }

    @Test
    fun excludedAndLoanRows_areIgnored() {
        val result = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.INCOME, 9999, LocalDate.of(2026, 1, 5), excluded = true),
                tx(TransactionType.INCOME, 5000, LocalDate.of(2026, 1, 5), loanId = 1L),
                tx(TransactionType.EXPENSE, 700, LocalDate.of(2026, 1, 6), excluded = true),
                tx(TransactionType.EXPENSE, 700, LocalDate.of(2026, 1, 6), loanId = 1L),
            ),
        )
        assertEquals(BigDecimal(1000), result[0].income)
        assertEquals(BigDecimal.ZERO, result[0].spent)
    }

    @Test
    fun transactions_landInTheirOwnMonthIncludingBoundaryDays() {
        val result = compute(
            listOf(
                tx(TransactionType.INCOME, 100, LocalDate.of(2026, 1, 31)),
                tx(TransactionType.INCOME, 200, LocalDate.of(2026, 2, 1)),
                tx(TransactionType.INCOME, 900, LocalDate.of(2026, 3, 1)),
            ),
        )
        assertEquals(BigDecimal(100), result[0].income)
        assertEquals(BigDecimal(200), result[1].income)
    }

    @Test
    fun currentMonth_isFlagged() {
        val result = compute(emptyList())
        assertFalse(result[0].isCurrent)
        assertTrue(result[1].isCurrent)
    }

    @Test
    fun average_isTotalSavedOverTotalIncome_notMeanOfPercentages() {
        // Jan: income 1000, saved 900 (90%). Feb: income 100, saved 0 (0%). Mean of % = 45, weighted = 900/1100.
        val months = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.EXPENSE, 100, LocalDate.of(2026, 1, 6)),
                tx(TransactionType.INCOME, 100, LocalDate.of(2026, 2, 5)),
                tx(TransactionType.EXPENSE, 100, LocalDate.of(2026, 2, 6)),
            ),
        )
        val summary = SavingsRateSummary(months = months, windowMonths = 2)
        assertEquals(81.82f, summary.averagePercent!!, 0.05f)
    }

    @Test
    fun average_skipsMonthsWithoutIncome() {
        val months = compute(
            listOf(
                tx(TransactionType.INCOME, 1000, LocalDate.of(2026, 1, 5)),
                tx(TransactionType.EXPENSE, 500, LocalDate.of(2026, 1, 6)),
                tx(TransactionType.EXPENSE, 400, LocalDate.of(2026, 2, 6)),
            ),
        )
        val summary = SavingsRateSummary(months = months, windowMonths = 2)
        assertEquals(50f, summary.averagePercent!!, 0.01f)
        assertEquals(1, summary.ratedMonths.size)
    }

    @Test
    fun summaryRange_coversAllMonths() {
        val summary = SavingsRateSummary(months = compute(emptyList()), windowMonths = 2)
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 2, 28), summary.range)
    }
}
