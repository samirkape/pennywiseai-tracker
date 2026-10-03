package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class RefundAdjustmentsTest {

    private fun tx(id: Long, amount: String, type: TransactionType, refundOf: Long? = null) =
        TransactionEntity(
            id = id,
            amount = BigDecimal(amount),
            merchantName = "Acme Store",
            category = "Shopping",
            transactionType = type,
            dateTime = LocalDateTime.of(2026, 5, 1, 10, 0),
            transactionHash = "h$id",
            refundOfTransactionId = refundOf
        )

    @Test
    fun refundedByOriginalSumsLinkedIncomeRows() {
        val map = RefundAdjustments.refundedByOriginal(
            listOf(
                tx(10, "100", TransactionType.INCOME, refundOf = 1),
                tx(11, "50", TransactionType.INCOME, refundOf = 1),
                tx(12, "20", TransactionType.INCOME, refundOf = 2)
            )
        )
        assertEquals(BigDecimal("150"), map[1L])
        assertEquals(BigDecimal("20"), map[2L])
    }

    @Test
    fun netDropsLinkedRefundsAndReducesOriginal() {
        val original = tx(1, "500", TransactionType.EXPENSE)
        val refund = tx(10, "200", TransactionType.INCOME, refundOf = 1)
        val plainIncome = tx(11, "900", TransactionType.INCOME)
        val result = RefundAdjustments.net(
            listOf(original, refund, plainIncome),
            mapOf(1L to BigDecimal("200"))
        )
        assertEquals(listOf(1L, 11L), result.map { it.id })
        assertEquals(BigDecimal("300"), result.first { it.id == 1L }.amount)
    }

    @Test
    fun refundInLaterMonthStillReducesOriginalWhenRefundRowNotInList() {
        val original = tx(1, "500", TransactionType.EXPENSE)
        val result = RefundAdjustments.net(listOf(original), mapOf(1L to BigDecimal("500")))
        assertEquals(BigDecimal.ZERO, result.single().amount)
    }

    @Test
    fun effectiveAmountIsFlooredAtZeroAndFractionScales() {
        val original = tx(1, "500", TransactionType.EXPENSE)
        assertEquals(BigDecimal.ZERO, RefundAdjustments.effectiveAmount(original, mapOf(1L to BigDecimal("900"))))
        val fraction = RefundAdjustments.remainingFraction(original, mapOf(1L to BigDecimal("125")))
        assertEquals(0, fraction.compareTo(BigDecimal("0.75")))
    }

    @Test
    fun noRefundsReturnsSameList() {
        val list = listOf(tx(1, "500", TransactionType.EXPENSE))
        assertTrue(RefundAdjustments.net(list, emptyMap()) === list)
    }
}
