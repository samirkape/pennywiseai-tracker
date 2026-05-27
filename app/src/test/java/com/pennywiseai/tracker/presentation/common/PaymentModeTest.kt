package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentModeTest {

    private fun tx(
        type: TransactionType,
        bankName: String? = "HDFC Bank",
    ) = TransactionEntity(
        amount = BigDecimal.TEN,
        merchantName = "Test",
        category = "Others",
        transactionType = type,
        dateTime = LocalDateTime.now(),
        transactionHash = "hash",
        bankName = bankName,
    )

    @Test
    fun paymentMode_creditCard() {
        assertEquals(PaymentMode.CREDIT_CARD, tx(TransactionType.CREDIT).paymentMode())
    }

    @Test
    fun paymentMode_bankAccount() {
        assertEquals(PaymentMode.BANK_ACCOUNT, tx(TransactionType.EXPENSE, "HDFC Bank").paymentMode())
    }

    @Test
    fun paymentMode_cash() {
        assertEquals(PaymentMode.CASH, tx(TransactionType.EXPENSE, MANUAL_ENTRY_BANK_NAME).paymentMode())
    }

    @Test
    fun paymentMode_nullForIncome() {
        assertNull(tx(TransactionType.INCOME).paymentMode())
    }

    @Test
    fun matchesPaymentModeGroup_cardAndBank() {
        assertTrue(tx(TransactionType.CREDIT).matchesPaymentModeGroup(PaymentModeGroup.CARD_AND_BANK))
        assertTrue(tx(TransactionType.EXPENSE, "HDFC Bank").matchesPaymentModeGroup(PaymentModeGroup.CARD_AND_BANK))
        assertFalse(tx(TransactionType.EXPENSE, MANUAL_ENTRY_BANK_NAME).matchesPaymentModeGroup(PaymentModeGroup.CARD_AND_BANK))
    }
}
