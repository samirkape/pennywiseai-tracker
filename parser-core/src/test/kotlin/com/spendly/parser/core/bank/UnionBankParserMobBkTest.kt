package com.spendly.parser.core.bank

import com.spendly.parser.core.TransactionType
import com.spendly.parser.core.test.ExpectedTransaction
import com.spendly.parser.core.test.ParserTestCase
import com.spendly.parser.core.test.ParserTestUtils
import com.spendly.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class UnionBankParserMobBkTest {
    private val debitBody =
        "A/c *2465 Debited for Rs: 97.12 on 26-05-2026 14:04:18 by Mob Bk ref no 947770950255 Fvg sRide Avl Bal Rs: 3040.80. Not you? Call 18002333 -Union Bank of India"

    @TestFactory
    fun `union bank mob bk debit with fvg`(): List<DynamicTest> {
        val parser = UnionBankParser()
        return ParserTestUtils.runTestSuite(
            parser,
            listOf(
                ParserTestCase(
                    name = "Debit with Fvg beneficiary",
                    message = debitBody,
                    sender = "VM-UNIONB-S",
                    expected = ExpectedTransaction(
                        amount = BigDecimal("97.12"),
                        currency = "INR",
                        type = TransactionType.EXPENSE,
                        merchant = "sRide",
                        accountLast4 = "2465",
                        reference = "947770950255",
                        balance = BigDecimal("3040.80"),
                    ),
                ),
            ),
        )
    }

    @TestFactory
    fun `factory resolves union bank via body signature`(): List<DynamicTest> {
        return ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "Union Bank of India",
                    sender = "59039465",
                    currency = "INR",
                    message = debitBody,
                    expected = ExpectedTransaction(
                        amount = BigDecimal("97.12"),
                        currency = "INR",
                        type = TransactionType.EXPENSE,
                        merchant = "sRide",
                    ),
                ),
            ),
            "Union Bank body fallback",
        )
    }
}
