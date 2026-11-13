package com.spendly.tracker.presentation.common

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AnalyticsFilterTest {

    private fun tx(
        type: TransactionType,
        loanId: Long? = null,
    ) = TransactionEntity(
        amount = BigDecimal.TEN,
        merchantName = "Test",
        category = "Others",
        transactionType = type,
        dateTime = LocalDateTime.now(),
        transactionHash = "hash-$type-$loanId",
        loanId = loanId,
    )

    @Test
    fun incomeFilter_matchesPlainIncome() {
        assertTrue(tx(TransactionType.INCOME).matchesAnalyticsIncomeFilter())
    }

    @Test
    fun incomeFilter_ignoresLoanLinkedRepayment() {
        assertFalse(tx(TransactionType.INCOME, loanId = 1L).matchesAnalyticsIncomeFilter())
    }

    @Test
    fun incomeFilter_ignoresOtherTypes() {
        assertFalse(tx(TransactionType.EXPENSE).matchesAnalyticsIncomeFilter())
        assertFalse(tx(TransactionType.CREDIT).matchesAnalyticsIncomeFilter())
        assertFalse(tx(TransactionType.TRANSFER).matchesAnalyticsIncomeFilter())
        assertFalse(tx(TransactionType.INVESTMENT).matchesAnalyticsIncomeFilter())
    }

    @Test
    fun spendingFilter_matchesDebitAndCardSpend() {
        assertTrue(tx(TransactionType.EXPENSE).matchesAnalyticsSpendingFilter())
        assertTrue(tx(TransactionType.CREDIT).matchesAnalyticsSpendingFilter())
    }

    @Test
    fun spendingFilter_ignoresLoanLinkedRows() {
        assertFalse(tx(TransactionType.EXPENSE, loanId = 1L).matchesAnalyticsSpendingFilter())
        assertFalse(tx(TransactionType.CREDIT, loanId = 1L).matchesAnalyticsSpendingFilter())
    }
}
