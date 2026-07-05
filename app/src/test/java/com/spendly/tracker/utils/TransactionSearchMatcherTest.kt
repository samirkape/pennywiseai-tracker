package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionSearchMatcherTest {

    private fun transaction(
        merchantName: String = "Store",
        description: String? = null,
        tags: String = "",
        category: String = "Shopping",
    ) = TransactionEntity(
        amount = BigDecimal("100"),
        merchantName = merchantName,
        category = category,
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.now(),
        transactionHash = "hash",
        description = description,
        tags = tags,
    )

    @Test
    fun matches_merchantName() {
        assertTrue(TransactionSearchMatcher.matches(transaction(merchantName = "Amazon Pay"), "amazon"))
    }

    @Test
    fun matches_description() {
        assertTrue(
            TransactionSearchMatcher.matches(
                transaction(description = "Team lunch reimbursement"),
                "lunch",
            )
        )
    }

    @Test
    fun matches_tags() {
        assertTrue(
            TransactionSearchMatcher.matches(
                transaction(tags = "work, reimbursable"),
                "reimbursable",
            )
        )
    }

    @Test
    fun blankQuery_matchesEverything() {
        assertTrue(TransactionSearchMatcher.matches(transaction(), "   "))
    }

    @Test
    fun noMatch_returnsFalse() {
        assertFalse(
            TransactionSearchMatcher.matches(
                transaction(description = "Coffee"),
                "flight",
            )
        )
    }
}
