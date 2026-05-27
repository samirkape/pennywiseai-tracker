package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class HDFCBankParserTest {
    @TestFactory
    fun `test HDFC Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = HDFCBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "HDFC Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Bill Alert - should NOT parse as transaction
            ParserTestCase(
                name = "Bill Alert Notification - Should Not Parse",
                message = """New Bill Alert:
Your XUBA00000TST1A Bill 1234567890 of Rs.1500.00 is due on 15-Jan-2026. To pay, login to HDFC Bank Net/Mobile Banking>BillPay
T&C. Ignore if paid""",
                sender = "CP-HDFCBK-S",
                shouldParse = false
            ),

            // NACH Mandate processing notification - should NOT parse as transaction
            ParserTestCase(
                name = "NACH Mandate Received for Processing - Should Not Parse",
                message = "Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:HDFC7031703262015557 To:NationalSecuritiesClearin Freq ADHO received today for processing.",
                sender = "VM-HDFCBK-S",
                shouldParse = false
            ),

            // Actual transaction examples that SHOULD parse
            ParserTestCase(
                name = "UPI Debit Transaction",
                message = "Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)",
                sender = "CP-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    reference = "123456789012"
                )
            ),

            ParserTestCase(
                name = "Sent from A/C with asterisk mask",
                message = "Sent Rs.15000.00 From HDFC Bank A/C *1234 To TEST MERCHANT PVT LTD On 01/01/26 Ref 567890567890 Not You? Call 18005556789/SMS BLOCK UPI to 7305556789",
                sender = "HDFCBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    merchant = "TEST MERCHANT"
                )
            ),

            // ACH payroll salary (ACH substring must not classify as investment)
            ParserTestCase(
                name = "ACH C-SAL salary credit",
                message = "Update! INR 1,97,317.00 deposited in HDFC Bank A/c XX2518 on 24-DEC-25 for ACH C-SAL-THWORKSTECHINDPV-SalaryDec254.Avl bal INR 1,97,317.00. Cheque deposits in A/C are subject to clearing",
                sender = "VD-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("197317.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    accountLast4 = "2518",
                    merchant = "THWORKSTECHINDPV",
                ),
            ),

            ParserTestCase(
                name = "ACH C- SAL salary credit (HDFC space variant)",
                message = "Update! INR 1,87,883.00 deposited in HDFC Bank A/c XX2518 on 25-MAY-26 for ACH C- SAL-THWORKSTECHINDPV-SALMAY41255.Avl bal INR 1,88,679.22. Cheque deposits in A/C are subject to clearing",
                sender = "VM-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("187883.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    accountLast4 = "2518",
                    merchant = "THWORKSTECHINDPV",
                ),
            ),

            // Credit card autopay / without OTP/PIN (e.g. insurance mandate on card)
            ParserTestCase(
                name = "HDFC Card charge without OTP/PIN at merchant",
                message = "Rs.3151 without OTP/PIN HDFC Bank Card x1655 At TALIC On 2026-05-23:09:37:05.Not U? Block&Reissue:Call 18002586161 / SMS BLOCK CC 1655 to 7308080808",
                sender = "VM-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3151"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "TALIC",
                ),
            ),

            // OTP delivery must still be rejected (even if it mentions card/amount)
            ParserTestCase(
                name = "OTP login SMS must not parse as transaction",
                message = "HDFC Bank: OTP is 847291 for Rs.3151 transaction on your Card x1655. Do not share OTP with anyone.",
                sender = "VM-HDFCBK-S",
                shouldParse = false,
            ),

            ParserTestCase(
                name = "Sent UPI multiline - To payee on next lines",
                message = """Sent Rs.25.00
From HDFC Bank A/C *2518
To Prajwal Kirana
On 24/05/26
Ref 919580778477
Not You?
Call 18002586161/SMS BLOCK UPI to 7308080808""",
                sender = "VM-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "2518",
                    merchant = "Prajwal Kirana",
                    reference = "919580778477",
                ),
            ),

            // IMPS credit (refund) — "Received!" format
            ParserTestCase(
                name = "IMPS credit Received format - Myntra refund",
                message = """Received!
INR 229.00 in HDFC Bank A/c xx2518
On 24-05-26
For IMPS -**MYNTRA DESIGNS PRIVATE LIMITE-**
614415473838
Avl bal INR 1,276.99""",
                sender = "VM-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("229.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    accountLast4 = "2518",
                    merchant = "MYNTRA DESIGNS",
                    balance = BigDecimal("1276.99"),
                ),
            ),

            // NEFT deposit (salary/income)
            ParserTestCase(
                name = "NEFT Credit Deposit - Income",
                message = "Update! INR 1.00 deposited in HDFC Bank A/c XX9999 on 30-MAR-26 for NEFT Cr-CITI0100000-ACME TECHNOLOGIES-PERSON NAME-CITIN99999999999.Avl bal INR 8.00. Cheque deposits in A/C are subject to clearing",
                sender = "CP-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    accountLast4 = "9999",
                    merchant = "ACME TECHNOLOGIES",
                    balance = BigDecimal("8.00")
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "CP-HDFCBK-S" to true,
            "AX-HDFCBK-S" to true,
            "JM-HDFCBK-S" to true,
            "HDFCBANK" to true,
            "SBI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "HDFC Bank Parser Tests")

    }
}
