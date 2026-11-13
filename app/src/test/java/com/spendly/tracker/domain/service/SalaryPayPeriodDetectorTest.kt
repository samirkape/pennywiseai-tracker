package com.spendly.tracker.domain.service

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.utils.DateRangeUtils
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

    @Test
    fun findSuggestion_ignoresLoanRepaymentWithSalaryText() {
        // A loan repayment whose SMS happens to mention salary must not be treated as payroll
        val repayment = salaryTxn(day = 20).copy(
            id = 99L,
            category = "Income",
            merchantName = "Friend",
            smsBody = "salary repayment",
            loanId = 7L,
        )
        assertNull(find(listOf(repayment)))
        assertEquals(0, SalaryPayPeriodDetector.scoreCandidate(repayment))
    }

    @Test
    fun findSuggestion_silentWhenSalaryMatchesWeekendRule() {
        // Weekend payday rule: Oct 25 2026 is a Sunday → expected payday Oct 23 (Fri).
        // A credit on Oct 23 is the normal, rule-adjusted payday — never prompts.
        val oct23 = LocalDate.of(2026, 10, 23)
        val txn = salaryTxn(day = 23).copy(
           dateTime = LocalDateTime.of(2026, 10, 23, 10, 0),
            transactionHash = "hash-oct2026",
        )
        assertNull(
            SalaryPayPeriodDetector.findSuggestion(
                transactions = listOf(txn),
                today = oct23,
                useFinancialMonth = true,
                useFixedBudgetPeriodEnd = false,
                defaultStartDay = 25,
                overrides = emptyMap(),
                dismissedTokens = emptySet(),
                adjustment = DateRangeUtils.SalaryDayAdjustment.PRECEDING_WEEKDAY,
            ),
        )
    }

    @Test
    fun findSuggestion_promptsWhenSalaryDeviatesFromRule_andCarriesRanges() {
        // Salary arrived Oct 21 instead of the rule-adjusted Oct 23 → worthwhile prompt
        val today = LocalDate.of(2026, 10, 23)
        val txn = salaryTxn(day = 21).copy(
            dateTime = LocalDateTime.of(2026, 10, 21, 10, 0),
            transactionHash = "hash-oct2026",
        )
        val suggestion = SalaryPayPeriodDetector.findSuggestion(
            transactions = listOf(txn),
            today = today,
            useFinancialMonth = true,
            useFixedBudgetPeriodEnd = false,
            defaultStartDay = 25,
            overrides = emptyMap(),
            dismissedTokens = emptySet(),
            adjustment = DateRangeUtils.SalaryDayAdjustment.PRECEDING_WEEKDAY,
        )
        assertNotNull(suggestion)
        assertEquals(21, suggestion!!.suggestedDay)
        // With the rule on, Oct 23 is already rule-payday: current window is Oct 23 – Nov 24;
        // the deviation suggestion re-anchors the window to the actual Oct 21 arrival
        assertEquals(LocalDate.of(2026, 10, 23), suggestion.currentRange.first)
        assertEquals(LocalDate.of(2026, 11, 24), suggestion.currentRange.second)
        assertEquals(LocalDate.of(2026, 10, 21), suggestion.newRange.first)
        assertEquals(LocalDate.of(2026, 11, 24), suggestion.newRange.second)
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
