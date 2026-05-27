package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class SalaryPayPeriodDetectorTest {

    private val today = LocalDate.of(2026, 5, 22)
    private val yearMonth = YearMonth.from(today)

    @Test
    fun findSuggestion_returnsNullWhenPayPeriodDisabled() {
        assertNull(
            find(
                listOf(salaryTxn(day = 20)),
                useFinancialMonth = false,
            ),
        )
    }

    @Test
    fun findSuggestion_returnsNullWhenMonthAlreadyOverridden() {
        assertNull(
            find(
                listOf(salaryTxn(day = 20)),
                overrides = mapOf(yearMonth.toString() to 20),
            ),
        )
    }

    @Test
    fun findSuggestion_returnsNullWhenMatchesDefaultDay() {
        assertNull(
            find(
                listOf(salaryTxn(day = 1)),
                defaultStartDay = 1,
            ),
        )
    }

    @Test
    fun findSuggestion_returnsNullWhenDismissed() {
        assertNull(
            find(
                listOf(salaryTxn(day = 20)),
                dismissedTokens = setOf("${yearMonth}:20"),
            ),
        )
    }

    @Test
    fun findSuggestion_picksSalaryCreditOnDifferentDay() {
        val suggestion = find(listOf(salaryTxn(day = 20), expenseLikeIncome(day = 15)))
        assertNotNull(suggestion)
        assertEquals(20, suggestion!!.suggestedDay)
        assertEquals(LocalDate.of(2026, 5, 20), suggestion.salaryDate)
    }

    @Test
    fun findSuggestion_ignoresExpense() {
        assertNull(
            find(
                listOf(
                    salaryTxn(day = 20).copy(
                        transactionType = TransactionType.EXPENSE,
                        smsBody = "salary refund",
                    ),
                ),
            ),
        )
    }

    @Test
    fun scoreCandidate_categorySalaryScoresHigh() {
        val score = SalaryPayPeriodDetector.scoreCandidate(
            salaryTxn(day = 10).copy(smsBody = null, merchantName = "NEFT Credit"),
        )
        assertEquals(40, score)
    }

    private fun find(
        transactions: List<TransactionEntity>,
        useFinancialMonth: Boolean = true,
        useFixedBudgetPeriodEnd: Boolean = false,
        defaultStartDay: Int = 1,
        overrides: Map<String, Int> = emptyMap(),
        dismissedTokens: Set<String> = emptySet(),
    ) = SalaryPayPeriodDetector.findSuggestion(
        transactions = transactions,
        today = today,
        useFinancialMonth = useFinancialMonth,
        useFixedBudgetPeriodEnd = useFixedBudgetPeriodEnd,
        defaultStartDay = defaultStartDay,
        overrides = overrides,
        dismissedTokens = dismissedTokens,
    )

    private fun salaryTxn(day: Int, id: Long = 1L) = TransactionEntity(
        id = id,
        amount = BigDecimal("50000"),
        merchantName = "ACME CORP",
        category = "Salary",
        transactionType = TransactionType.INCOME,
        dateTime = LocalDateTime.of(2026, 5, day, 10, 0),
        smsBody = "credited for MAY SALARY",
        transactionHash = "hash-$id",
    )

    private fun expenseLikeIncome(day: Int) = salaryTxn(day = day, id = 2L).copy(
        category = "Other",
        merchantName = "Friend",
        smsBody = "UPI payment received",
        amount = BigDecimal("5000"),
    )
}
