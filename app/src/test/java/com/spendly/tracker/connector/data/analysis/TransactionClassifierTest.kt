package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.Essentiality
import com.spendly.tracker.connector.domain.ExpenseBehaviour
import com.spendly.tracker.connector.domain.PaymentMethod
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionClassifierTest {

    private fun tx(
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String = "Store",
        category: String = "Shopping",
        description: String? = null,
        smsBody: String? = null,
        reference: String? = null
    ) = TransactionEntity(
        id = 1,
        amount = BigDecimal("100.00"),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 3, 10, 12, 0),
        transactionHash = "h1",
        description = description,
        smsBody = smsBody,
        reference = reference
    )

    // ---- Payment method -----------------------------------------------------

    @Test
    fun creditCardRowsArePaidByCreditCard() {
        assertEquals(
            PaymentMethod.CREDIT_CARD,
            TransactionClassifier.paymentMethod(tx(type = TransactionType.CREDIT))
        )
    }

    @Test
    fun upiIsDetectedFromTheTransactionText() {
        assertEquals(
            PaymentMethod.UPI,
            TransactionClassifier.paymentMethod(tx(smsBody = "Rs 500 debited via UPI"))
        )
        assertEquals(
            PaymentMethod.UPI,
            TransactionClassifier.paymentMethod(tx(merchant = "Paytm"))
        )
    }

    @Test
    fun debitCardAndCashAreDetected() {
        assertEquals(
            PaymentMethod.DEBIT_CARD,
            TransactionClassifier.paymentMethod(tx(smsBody = "Debit Card Txn at Store"))
        )
        assertEquals(
            PaymentMethod.CASH,
            TransactionClassifier.paymentMethod(tx(smsBody = "ATM withdrawal"))
        )
    }

    @Test
    fun unknownIsReportedWhenNothingSays() {
        assertEquals(
            PaymentMethod.UNKNOWN,
            TransactionClassifier.paymentMethod(tx(merchant = "Corner Store"))
        )
    }

    @Test
    fun transfersAreBankTransfers() {
        assertEquals(
            PaymentMethod.BANK_TRANSFER,
            TransactionClassifier.paymentMethod(tx(type = TransactionType.TRANSFER))
        )
    }

    // ---- Refunds ------------------------------------------------------------

    @Test
    fun aLinkedRefundIsRecognised() {
        val refund = tx(type = TransactionType.INCOME).copy(refundOfTransactionId = 7L)
        assertTrue(TransactionClassifier.isLinkedRefund(refund))
    }

    @Test
    fun anUnlinkedRefundIsRecognisedFromItsWording() {
        assertTrue(
            TransactionClassifier.looksLikeRefund(
                tx(type = TransactionType.INCOME, merchant = "Corp Refund Desk")
            )
        )
        assertTrue(
            TransactionClassifier.looksLikeRefund(
                tx(type = TransactionType.INCOME, merchant = "Insurance Reimbursement")
            )
        )
    }

    @Test
    fun ordinaryIncomeIsNotMistakenForARefund() {
        assertTrue(
            !TransactionClassifier.looksLikeRefund(
                tx(type = TransactionType.INCOME, merchant = "Employer Payroll")
            )
        )
    }

    @Test
    fun aReversalIsNotARefund() {
        val reversal = tx(type = TransactionType.INCOME, merchant = "Amount reversed")
        assertTrue(TransactionClassifier.isReversal(reversal))
        assertTrue(!TransactionClassifier.looksLikeRefund(reversal))
    }

    @Test
    fun healthcareRefundsAreRecognisedByTheOriginalCategory() {
        val original = tx(category = "Healthcare")
        val refund = tx(type = TransactionType.INCOME)
        assertEquals(
            com.spendly.tracker.connector.domain.RefundKind.HEALTHCARE_INSURANCE_REIMBURSEMENT,
            TransactionClassifier.kindOfLinkedRefund(refund, original)
        )
    }

    // ---- Essentiality and behaviour -----------------------------------------

    @Test
    fun categoriesCarryTheirEssentiality() {
        assertEquals(
            Essentiality.ESSENTIAL,
            SpendingCategoryClassifier.essentiality("Groceries")
        )
        assertEquals(
            Essentiality.DISCRETIONARY,
            SpendingCategoryClassifier.essentiality("Food & Dining")
        )
        assertEquals(
            Essentiality.FINANCIAL_COMMITMENT,
            SpendingCategoryClassifier.essentiality("Credit Card Payment")
        )
        assertEquals(
            Essentiality.INVESTMENT,
            SpendingCategoryClassifier.essentiality("Investments")
        )
        assertEquals(
            Essentiality.UNKNOWN,
            SpendingCategoryClassifier.essentiality("")
        )
        assertEquals(
            Essentiality.UNKNOWN,
            SpendingCategoryClassifier.essentiality("Zorblax")
        )
    }

    @Test
    fun categoriesCarryTheirBehaviour() {
        assertEquals(
            ExpenseBehaviour.VARIABLE,
            SpendingCategoryClassifier.behaviour("Groceries")
        )
        assertEquals(
            ExpenseBehaviour.FIXED,
            SpendingCategoryClassifier.behaviour("Bills & Utilities")
        )
        assertEquals(
            ExpenseBehaviour.OCCASIONAL,
            SpendingCategoryClassifier.behaviour("Healthcare")
        )
        assertEquals(
            ExpenseBehaviour.UNKNOWN,
            SpendingCategoryClassifier.behaviour("Zorblax")
        )
    }

    @Test
    fun aRepeatingMerchantBecomesAFixedCost() {
        assertEquals(
            ExpenseBehaviour.FIXED,
            SpendingCategoryClassifier.behaviour("Food & Dining", occurrences = 3)
        )
    }
}
