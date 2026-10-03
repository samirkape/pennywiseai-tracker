package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Test

class RefundMatcherTest {

    private val matcher = RefundMatcher()
    private val now = LocalDateTime.of(2026, 5, 20, 12, 0)

    private fun tx(
        id: Long,
        amount: String,
        merchant: String,
        type: TransactionType,
        daysAgo: Long = 0,
        account: String? = null,
        currency: String = "INR",
        sms: String? = null
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = "Shopping",
        transactionType = type,
        dateTime = now.minusDays(daysAgo),
        smsBody = sms,
        accountNumber = account,
        transactionHash = "h$id",
        currency = currency
    )

    @Test
    fun fullRefundSameMerchantAutoLinks() {
        val refund = tx(1, "500", "Acme Store", TransactionType.INCOME, sms = "Refund credited")
        val original = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 5)
        val ranked = matcher.rank(refund, listOf(original), emptyMap())
        assertEquals(2L, matcher.pickAutoMatch(ranked)?.original?.id)
    }

    @Test
    fun partialRefundNeedsMerchantMatch() {
        val refund = tx(1, "200", "Zed Shop", TransactionType.INCOME)
        val original = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 5)
        assertTrue(matcher.rank(refund, listOf(original), emptyMap()).isEmpty())
    }

    @Test
    fun partialRefundSameMerchantIsRankedWithRemaining() {
        val refund = tx(1, "200", "Acme Store", TransactionType.INCOME)
        val original = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 3)
        val ranked = matcher.rank(refund, listOf(original), emptyMap())
        assertEquals(1, ranked.size)
        assertEquals(BigDecimal("500"), ranked[0].remainingRefundable)
    }

    @Test
    fun outOfWindowCurrencyAndFutureAreRejected() {
        val refund = tx(1, "500", "Acme Store", TransactionType.INCOME)
        val tooOld = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 120)
        val otherCurrency = tx(3, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 2, currency = "USD")
        val later = tx(4, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = -2)
        assertTrue(matcher.rank(refund, listOf(tooOld, otherCurrency, later), emptyMap()).isEmpty())
    }

    @Test
    fun alreadyRefundedAmountReducesRemaining() {
        val refund = tx(1, "300", "Acme Store", TransactionType.INCOME)
        val original = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 3)
        val ranked = matcher.rank(refund, listOf(original), mapOf(2L to BigDecimal("300")))
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun ambiguousCandidatesDoNotAutoLink() {
        val refund = tx(1, "500", "Acme Store", TransactionType.INCOME)
        val a = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 3)
        val b = tx(3, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 4)
        val ranked = matcher.rank(refund, listOf(a, b), emptyMap())
        assertEquals(2, ranked.size)
        assertNull(matcher.pickAutoMatch(ranked))
    }

    @Test
    fun amountOnlyMatchAutoLinksOnlyWithSupportingSignals() {
        val refund = tx(1, "777", "Unknown Sender", TransactionType.INCOME, account = "1234", sms = "refund")
        val sameCard = tx(2, "777", "Acme Store", TransactionType.EXPENSE, daysAgo = 2, account = "1234")
        assertNotNull(matcher.pickAutoMatch(matcher.rank(refund, listOf(sameCard), emptyMap())))

        val otherCard = tx(3, "777", "Acme Store", TransactionType.EXPENSE, daysAgo = 2, account = "9999")
        assertNull(matcher.pickAutoMatch(matcher.rank(refund, listOf(otherCard), emptyMap())))
    }

    @Test
    fun nonIncomeRefundIsIgnored() {
        val notRefund = tx(1, "500", "Acme Store", TransactionType.EXPENSE)
        val original = tx(2, "500", "Acme Store", TransactionType.EXPENSE, daysAgo = 1)
        assertTrue(matcher.rank(notRefund, listOf(original), emptyMap()).isEmpty())
    }
}
