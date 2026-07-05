package com.spendly.parser.core.bank

import com.spendly.parser.core.TransactionType
import com.spendly.parser.core.TransferKinds
import com.spendly.parser.core.test.ExpectedTransaction
import com.spendly.parser.core.test.ParserTestCase
import com.spendly.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Tests for the centralised credit-card-bill-payment detection in
 * [BaseIndianBankParser.isCreditCardBillPayment]. These guard the
 * "no double-counting" contract: a CC purchase is CREDIT and counted, while a
 * CC bill payment (debit and credit leg) is TRANSFER + CC_BILL_PAYMENT and
 * excluded from spending.
 */
class CreditCardBillPaymentDetectionTest {

    /**
     * Concrete probe over the abstract helper. We test `isCreditCardBillPayment`
     * directly so the logic doesn't depend on each bank's individual SMS shape.
     */
    private class ProbeParser : BaseIndianBankParser() {
        override fun getBankName() = "Probe"
        override fun canHandle(sender: String) = true
    }

    private val probe = ProbeParser()

    @TestFactory
    fun `isCreditCardBillPayment matches bank-side debit and card-side credit`(): List<DynamicTest> {
        val positives = listOf(
            "Payment of Rs 26,266.00 has been received on your ICICI Bank Credit Card XX9006.",
            "Thank you for the payment of Rs 5,000 towards your HDFC Bank Credit Card ending 1234.",
            "We have received your payment of Rs 1,500 towards Federal Bank Credit Card.",
            "Payment of Rs.10,000 has been successfully credited towards your SBI Credit Card.",
            "Rs 3,000 paid via BBPS towards your Credit Card ending 7777.",
            "Payment of Rs.50000 has been successfully credited towards your ICICI Bank Credit Card - CRED",
            "Sent Rs.5199.65 From HDFC Bank A/C *2518 To CRED Club On 17/05/26 Ref 650304962409",
            // HDFC CC bill payment confirmation: "Online Payment...was credited to your card ending XXXX"
            "HDFC Bank Cardmember, Online Payment of Rs.3102 vide Ref# 1521156481jdzLx was credited to your card ending 8711 On 01/JUN/2026_value Date 01/JUN/2026",
            "HDFC Bank Cardmember, Online Payment of Rs.30000 vide Ref# 152114350TONPHy was credited to your card ending 9908 On 01/JUN/2026_value Date 01/JUN/2026"
        )
        val negatives = listOf(
            // Card purchases must not be treated as bill payments.
            "Rs.499 spent on your HDFC Credit Card XX1234 at AMAZON on 12-May-26",
            "INR 1,200 spent on AMEX card ending 9876 at SWIGGY",
            // Refunds and cashbacks must not be classified as bill payments.
            "Refund of Rs 250 credited to your ICICI Bank Credit Card XX9006",
            "Cashback of Rs 100 credited to your HDFC Bank Credit Card",
            // Plain account debit, not card-related.
            "Rs 1,000 debited from A/c XX5678 to RAJESH on 12-May-26"
        )

        val tests = mutableListOf<DynamicTest>()
        positives.forEachIndexed { i, msg ->
            tests.add(dynamicTest("positive #${i + 1}: ${msg.take(60)}") {
                assertTrue(
                    probe.isCreditCardBillPayment(msg),
                    "Expected CC bill payment for: $msg"
                )
            })
        }
        negatives.forEachIndexed { i, msg ->
            tests.add(dynamicTest("negative #${i + 1}: ${msg.take(60)}") {
                assertFalse(
                    probe.isCreditCardBillPayment(msg),
                    "Did NOT expect CC bill payment for: $msg"
                )
            })
        }
        return tests
    }

    @TestFactory
    fun `parsers re-classify CC bill payments to TRANSFER + CC_BILL_PAYMENT`(): List<DynamicTest> {
        // HDFC bank-side debit (used to land as EXPENSE and double-count).
        val hdfc = HDFCBankParser()
        val hdfcCases = listOf(
            ParserTestCase(
                name = "HDFC bank-side CC payment becomes TRANSFER",
                message = "Sent Rs.5000 From HDFC Bank A/C *1234 Towards Credit Card ending 9876 on 12-05",
                sender = "AX-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKinds.CC_BILL_PAYMENT
                )
            ),
            ParserTestCase(
                name = "HDFC Sent to CRED Club is CC bill payment not expense",
                message = "Sent Rs.5199.65 From HDFC Bank A/C *2518 To CRED Club On 17/05/26 Ref 650304962409 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808",
                sender = "AX-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5199.65"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKinds.CC_BILL_PAYMENT,
                    merchant = "CRED Club"
                )
            )
        )

        // ICICI card-side credit (used to be dropped from the inbox entirely).
        val icici = ICICIBankParser()
        val iciciCases = listOf(
            ParserTestCase(
                name = "ICICI card-side payment received becomes TRANSFER",
                message = "Payment of Rs 26266.00 has been received on your ICICI Bank Credit Card XX9006. Avl Lmt Rs.50,000",
                sender = "AX-ICICIB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("26266.00"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKinds.CC_BILL_PAYMENT
                )
            )
        )

        // CRED is its own parser path but should still set transferKind.
        val cred = CredParser()
        val credCases = listOf(
            ParserTestCase(
                name = "CRED payment carries CC_BILL_PAYMENT transferKind",
                message = "Payment of Rs.50000 has been successfully credited towards your ICICI Bank Credit Card. - CRED",
                sender = "JK-CREDIN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKinds.CC_BILL_PAYMENT
                )
            )
        )

        // HDFC card-side credit confirmation ("credited to your card ending") — previously INCOME.
        val hdfcCardConfirmCases = listOf(
            ParserTestCase(
                name = "HDFC CC payment confirmation becomes TRANSFER not INCOME",
                message = "HDFC Bank Cardmember, Online Payment of Rs.3102 vide Ref# 1521156481jdzLx was credited to your card ending 8711 On 01/JUN/2026_value Date 01/JUN/2026",
                sender = "AX-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3102"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKinds.CC_BILL_PAYMENT
                )
            ),
            ParserTestCase(
                name = "HDFC CC payment confirmation (large amount) becomes TRANSFER not INCOME",
                message = "HDFC Bank Cardmember, Online Payment of Rs.30000 vide Ref# 152114350TONPHy was credited to your card ending 9908 On 01/JUN/2026_value Date 01/JUN/2026",
                sender = "AX-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    transferKind = TransferKinds.CC_BILL_PAYMENT
                )
            )
        )

        return ParserTestUtils.runTestSuite(hdfc, hdfcCases) +
            ParserTestUtils.runTestSuite(hdfc, hdfcCardConfirmCases) +
            ParserTestUtils.runTestSuite(icici, iciciCases) +
            ParserTestUtils.runTestSuite(cred, credCases)
    }
}
