package com.spendly.parser.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdfcExplicitPayeeTest {

    private val sentMultiline = """Sent Rs.25.00
From HDFC Bank A/C *2518
To Prajwal Kirana
On 24/05/26
Ref 919580778477
Not You?
Call 18002586161/SMS BLOCK UPI to 7308080808"""

    private val impsCredit = """Received!
INR 229.00 in HDFC Bank A/c xx2518
On 24-05-26
For IMPS -**MYNTRA DESIGNS PRIVATE LIMITE-**
614415473838
Avl bal INR 1,276.99"""

    @Test
    fun extract_sentUpiMultilinePayee() {
        assertEquals("MYNTRA DESIGNS", HdfcExplicitPayee.normalizePayee("MYNTRA DESIGNS PRIVATE LIMITE"))
        assertEquals("Prajwal Kirana", HdfcExplicitPayee.extract(sentMultiline))
    }

    @Test
    fun extract_impsCreditPayee() {
        assertEquals("MYNTRA DESIGNS", HdfcExplicitPayee.extract(impsCredit))
    }

    @Test
    fun shouldPreserveMerchant_whenParsedPayeeMatchesExplicitLine() {
        assertTrue(
            HdfcExplicitPayee.shouldPreserveMerchant(sentMultiline, "Prajwal Kirana"),
        )
        assertTrue(
            HdfcExplicitPayee.shouldPreserveMerchant(impsCredit, "Myntra Designs"),
        )
    }

    @Test
    fun shouldNotPreserve_whenMerchantUnknownOrMissingPayeeLine() {
        assertFalse(
            HdfcExplicitPayee.shouldPreserveMerchant(sentMultiline, "Unknown Merchant"),
        )
        assertFalse(
            HdfcExplicitPayee.shouldPreserveMerchant(
                "Update! INR 1,97,317.00 deposited for ACH C-SAL-THWORKSTECHINDPV-SalaryDec254",
                "THWORKSTECHINDPV",
            ),
        )
    }
}
