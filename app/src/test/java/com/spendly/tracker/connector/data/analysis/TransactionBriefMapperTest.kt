package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.TransactionBrief
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionBriefMapperTest {

    private fun tx(
        id: Long,
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String = "Store",
        category: String = "Shopping",
        date: LocalDateTime = LocalDateTime.of(2026, 3, 10, 12, 0),
        loanId: Long? = null,
        transferKind: String? = null,
        excluded: Boolean = false
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = date,
        transactionHash = "h$id",
        description = "should never leave the device",
        smsBody = "Rs 500 debited from A/c XX1234",
        bankName = "Secret Bank",
        accountNumber = "1234",
        reference = "UPI/1234567890",
        loanId = loanId,
        transferKind = transferKind,
        isExcludedFromTracking = excluded
    )

    @Test
    fun keepsOnlySpendingAndIncomeRows() {
        val briefs = TransactionBriefMapper.from(
            listOf(
                tx(1, "100.00"),
                tx(2, "200.00", type = TransactionType.CREDIT),
                tx(3, "300.00", type = TransactionType.INCOME),
                tx(4, "400.00", type = TransactionType.TRANSFER),
                tx(
                    5,
                    "500.00",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKind.CC_BILL_PAYMENT
                ),
                tx(6, "600.00", type = TransactionType.INVESTMENT),
                tx(7, "700.00", loanId = 3),
                tx(8, "800.00", type = TransactionType.INCOME, loanId = 3),
                tx(9, "900.00", excluded = true)
            )
        )

        assertEquals(3, briefs.size)
        assertEquals(
            setOf(
                TransactionBrief.Type.EXPENSE,
                TransactionBrief.Type.CARD,
                TransactionBrief.Type.INCOME
            ),
            briefs.map { it.type }.toSet()
        )
    }

    @Test
    fun rowsAreNewestFirstAndCarryOnlySafeFields() {
        val briefs = TransactionBriefMapper.from(
            listOf(
                tx(1, "100.00", date = LocalDateTime.of(2026, 3, 1, 9, 0)),
                tx(
                    2,
                    "250.50",
                    type = TransactionType.CREDIT,
                    merchant = "Cafe",
                    category = "Food",
                    date = LocalDateTime.of(2026, 3, 12, 18, 30)
                )
            )
        )

        assertEquals(12, briefs.first().date.dayOfMonth)
        assertEquals(BigDecimal("250.50"), briefs.first().amount)
        assertEquals(TransactionBrief.Type.CARD, briefs.first().type)
        assertEquals("Cafe", briefs.first().merchant)
        assertEquals("Food", briefs.first().category)
        assertFalse(briefs.first().toString().contains("Secret Bank"))
        assertFalse(briefs.first().toString().contains("XX1234"))
        assertFalse(briefs.first().toString().contains("UPI/"))
        assertFalse(briefs.first().toString().contains("should never leave"))
    }

    @Test
    fun upiHandlesInMerchantNamesAreMasked() {
        val brief = TransactionBriefMapper.from(
            listOf(tx(1, "10.00", merchant = "9876543210@paytm"))
        ).single()

        assertEquals("UPI contact", brief.merchant)
    }

    @Test
    fun fullyRefundedRowsAreSkipped() {
        val briefs = TransactionBriefMapper.from(
            listOf(
                tx(1, "0.00"),
                tx(2, "100.00")
            )
        )

        assertEquals(1, briefs.size)
        assertEquals(BigDecimal("100.00"), briefs.single().amount)
    }

    @Test
    fun blankMerchantAndCategoryGetSafePlaceholders() {
        val brief = TransactionBriefMapper.from(
            listOf(tx(1, "10.00", merchant = "  ", category = ""))
        ).single()

        assertEquals("Unknown", brief.merchant)
        assertEquals("Uncategorised", brief.category)
    }
}
