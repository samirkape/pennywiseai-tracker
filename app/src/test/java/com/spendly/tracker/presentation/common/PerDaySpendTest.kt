package com.spendly.tracker.presentation.common

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class PerDaySpendTest {

    private fun tx(date: LocalDate) = TransactionEntity(
        amount = BigDecimal.TEN,
        merchantName = "Test",
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = date.atTime(12, 0),
        transactionHash = "hash-$date",
    )

    @Test
    fun perDayAverage_spreadsTotalOverWindow() {
        assertEquals(
            BigDecimal("33.33"),
            perDayAverage(BigDecimal("100"), 3)
        )
    }

    @Test
    fun perDayAverage_isNullForUnknownWindow() {
        assertNull(perDayAverage(BigDecimal("100"), 0))
        assertNull(perDayAverage(BigDecimal("100"), -1))
    }

    @Test
    fun perDayAverage_isNullWithoutSpend() {
        assertNull(perDayAverage(BigDecimal.ZERO, 10))
    }

    @Test
    fun spanDays_countsInclusiveFirstAndLastDay() {
        val txs = listOf(
            tx(LocalDate.of(2026, 1, 1)),
            tx(LocalDate.of(2026, 1, 5)),
        )
        assertEquals(5, spanDays(txs))
    }

    @Test
    fun spanDays_isOneForSingleDay() {
        assertEquals(1, spanDays(listOf(tx(LocalDate.of(2026, 1, 1)))))
    }

    @Test
    fun spanDays_isZeroWithoutTransactions() {
        assertEquals(0, spanDays(emptyList()))
    }
}
