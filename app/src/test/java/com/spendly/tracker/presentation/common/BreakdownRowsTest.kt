package com.spendly.tracker.presentation.common

import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Loan-linked rows (money lent out, or its repayment coming back) move money between
 * parties. They must never reach a spending breakdown or drill-down.
 */
class BreakdownRowsTest {

    private fun tx(
        type: TransactionType,
        loanId: Long? = null,
        excluded: Boolean = false,
    ) = TransactionEntity(
        amount = BigDecimal.TEN,
        merchantName = "Test",
        category = "Food",
        transactionType = type,
        dateTime = LocalDateTime.now(),
        transactionHash = "hash-$type-$loanId-$excluded",
        loanId = loanId,
        isExcludedFromTracking = excluded,
    )

    @Test
    fun anyType_keepsDebitAndCardSpend() {
        val rows = listOf(
            tx(TransactionType.EXPENSE),
            tx(TransactionType.CREDIT),
            tx(TransactionType.INCOME),
            tx(TransactionType.TRANSFER),
        ).breakdownRowsFor(TransactionTypeFilter.ALL)

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT })
    }

    @Test
    fun anyType_dropsLentRow() {
        val lent = tx(TransactionType.EXPENSE, loanId = 1L)
        val rows = listOf(lent, tx(TransactionType.EXPENSE)).breakdownRowsFor(TransactionTypeFilter.ALL)

        assertEquals(1, rows.size)
        assertFalse(rows.contains(lent))
    }

    @Test
    fun anyType_dropsLentRepaymentRow() {
        val repayment = tx(TransactionType.INCOME, loanId = 1L)
        val rows = listOf(repayment, tx(TransactionType.EXPENSE)).breakdownRowsFor(TransactionTypeFilter.ALL)

        assertEquals(1, rows.size)
        assertFalse(rows.contains(repayment))
    }

    @Test
    fun spendingFilter_dropsLentRow() {
        val lent = tx(TransactionType.EXPENSE, loanId = 1L)
        val rows = listOf(lent, tx(TransactionType.EXPENSE)).breakdownRowsFor(TransactionTypeFilter.EXPENSE)

        assertEquals(1, rows.size)
        assertFalse(rows.contains(lent))
    }

    @Test
    fun incomeFilter_dropsLoanRepayment() {
        val repayment = tx(TransactionType.INCOME, loanId = 1L)
        val rows = listOf(repayment, tx(TransactionType.INCOME)).breakdownRowsFor(TransactionTypeFilter.INCOME)

        assertEquals(1, rows.size)
        assertFalse(rows.contains(repayment))
    }

    @Test
    fun excludedFilter_keepsOnlyExcludedRows() {
        val excluded = tx(TransactionType.EXPENSE, excluded = true)
        val rows = listOf(excluded, tx(TransactionType.EXPENSE)).breakdownRowsFor(TransactionTypeFilter.EXCLUDED)

        assertEquals(1, rows.size)
        assertTrue(rows.contains(excluded))
    }
}

class LoanLabelTest {

    private fun loan(direction: LoanDirection) = LoanEntity(
        personName = "Asha",
        direction = direction,
        originalAmount = BigDecimal.TEN,
        remainingAmount = BigDecimal.TEN,
    )

    private fun tx(type: TransactionType) = TransactionEntity(
        amount = BigDecimal.TEN,
        merchantName = "Asha",
        category = "Others",
        transactionType = type,
        dateTime = LocalDateTime.now(),
        transactionHash = "hash-$type",
    )

    @Test
    fun lentExpense_isLabelledAsLent() {
        assertEquals(
            "Lent to Asha",
            loanLabelFor(tx(TransactionType.EXPENSE), loan(LoanDirection.LENT))
        )
    }

    @Test
    fun lentIncome_isLabelledAsRepayment() {
        assertEquals(
            "Repayment from Asha",
            loanLabelFor(tx(TransactionType.INCOME), loan(LoanDirection.LENT))
        )
    }

    @Test
    fun borrowedIncome_isLabelledAsBorrowed() {
        assertEquals(
            "Borrowed from Asha",
            loanLabelFor(tx(TransactionType.INCOME), loan(LoanDirection.BORROWED))
        )
    }

    @Test
    fun borrowedExpense_isLabelledAsRepaid() {
        assertEquals(
            "Repaid to Asha",
            loanLabelFor(tx(TransactionType.EXPENSE), loan(LoanDirection.BORROWED))
        )
    }
}
