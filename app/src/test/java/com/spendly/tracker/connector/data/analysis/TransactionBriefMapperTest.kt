package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.FinancialType
import com.spendly.tracker.connector.domain.PaymentMethod
import com.spendly.tracker.connector.domain.RefundKind
import com.spendly.tracker.connector.domain.RefundMatchStatus
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        excluded: Boolean = false,
        refundOf: Long? = null,
        tags: String = "",
        accountNumber: String? = "1234"
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
        accountNumber = accountNumber,
        reference = "UPI/1234567890",
        loanId = loanId,
        transferKind = transferKind,
        isExcludedFromTracking = excluded,
        refundOfTransactionId = refundOf,
        tags = tags
    )

    @Test
    fun everyRowOfThePeriodIsListed() {
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

        assertEquals(8, briefs.size)
        assertEquals(
            setOf(
                FinancialType.EXPENSE,
                FinancialType.INCOME,
                FinancialType.TRANSFER,
                FinancialType.INVESTMENT,
                FinancialType.LOAN_DISBURSEMENT,
                FinancialType.LOAN_PAYMENT
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
        assertEquals(FinancialType.EXPENSE, briefs.first().type)
        assertEquals(PaymentMethod.CREDIT_CARD, briefs.first().paymentMethod)
        assertEquals("Cafe", briefs.first().merchantOriginal)
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

        assertEquals("UPI contact", brief.merchantOriginal)
    }

    @Test
    fun zeroAmountRowsAreSkipped() {
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

        assertEquals("Unknown", brief.merchantOriginal)
        assertEquals("Uncategorised", brief.category)
        assertNull(brief.subcategory)
    }

    @Test
    fun refundsLinkToTheirOriginal() {
        val briefs = TransactionBriefMapper.from(
            listOf(
                tx(1, "500.00", merchant = "Apollo Hospital", category = "Healthcare"),
                tx(
                    2,
                    "200.00",
                    type = TransactionType.INCOME,
                    merchant = "Apollo Hospital",
                    category = "Income",
                    refundOf = 1L
                )
            )
        )

        val refund = briefs.first { it.type == FinancialType.REFUND }
        assertEquals("txn_000001", refund.linkedTransactionId)
        assertEquals(RefundMatchStatus.MATCHED, refund.refundMatchStatus)
        assertEquals(RefundKind.HEALTHCARE_INSURANCE_REIMBURSEMENT, refund.refundKind)
    }

    @Test
    fun refundsWithoutAnOriginalAreMarkedUnmatched() {
        val refund = TransactionBriefMapper.from(
            listOf(
                tx(
                    2,
                    "200.00",
                    type = TransactionType.INCOME,
                    merchant = "Store Refund",
                    category = "Income",
                    refundOf = 99L
                )
            )
        ).single()

        assertEquals(RefundMatchStatus.UNMATCHED, refund.refundMatchStatus)
        assertEquals("txn_000099", refund.linkedTransactionId)
    }

    @Test
    fun loanRowsUseTheLoanDirection() {
        val briefs = TransactionBriefMapper.from(
            listOf(
                tx(1, "50000.00", type = TransactionType.INCOME, loanId = 3),
                tx(2, "10000.00", type = TransactionType.EXPENSE, loanId = 3)
            ),
            loanDirections = mapOf(3L to LoanDirection.BORROWED)
        )

        assertEquals(FinancialType.LOAN_DISBURSEMENT, briefs.first { it.amount == BigDecimal("50000.00") }.type)
        assertEquals(FinancialType.LOAN_PAYMENT, briefs.first { it.amount == BigDecimal("10000.00") }.type)
    }

    @Test
    fun accountAliasHidesTheRealAccountNumber() {
        val brief = TransactionBriefMapper.from(
            listOf(tx(1, "100.00", accountNumber = "9876543210"))
        ).single()

        assertEquals("Account 1", brief.accountAlias)
        assertFalse(brief.toString().contains("9876543210"))
    }

    @Test
    fun transactionIdsAreStableAndUnique() {
        val briefs = TransactionBriefMapper.from(
            listOf(tx(1, "100.00"), tx(2, "200.00"), tx(3, "300.00"))
        )

        assertEquals(
            setOf("txn_000001", "txn_000002", "txn_000003"),
            briefs.map { it.transactionId }.toSet()
        )
    }

    @Test
    fun moneyMovedRowsCarryAPurposeLabel() {
        val brief = TransactionBriefMapper.from(
            listOf(
                tx(
                    1,
                    "500.00",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKind.CC_BILL_PAYMENT
                )
            )
        ).single()

        assertEquals("credit_card_bill_payment", brief.notes)
    }

    @Test
    fun tagsAreJoinedWithASemicolon() {
        val brief = TransactionBriefMapper.from(
            listOf(tx(1, "100.00", tags = "work, travel"))
        ).single()

        assertEquals("work;travel", brief.tag)
    }

    @Test
    fun excludedRowsAreNeverListed() {
        val briefs = TransactionBriefMapper.from(
            listOf(tx(1, "100.00", excluded = true))
        )

        assertTrue(briefs.isEmpty())
    }
}
