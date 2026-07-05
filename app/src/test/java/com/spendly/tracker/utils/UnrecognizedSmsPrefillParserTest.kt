package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.UnrecognizedSmsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class UnrecognizedSmsPrefillParserTest {

    @Test
    fun parse_talicInsuranceSms() {
        val sms =
            "Rs.3151 without OTP/PIN HDFC Bank Card x1655 At TALIC On 2026-05-23:09:37:05.Not U? Block&Reissue:Call 18002586161 / SMS BLOCK CC 1655 to 7308080808"
        val message = UnrecognizedSmsEntity(
            sender = "VM-HDFCBK-S",
            smsBody = sms,
            receivedAt = LocalDateTime.of(2026, 5, 23, 9, 37, 5),
        )
        val prefill = UnrecognizedSmsPrefillParser.parse(message)
        assertEquals(BigDecimal("3151"), prefill.amount)
        assertEquals("TALIC", prefill.merchant)
        assertEquals(TransactionType.CREDIT, prefill.transactionType)
        assertEquals("HDFC Bank", prefill.bankNameHint)
        assertNotNull(prefill.dateTime)
    }

    @Test
    fun extractAmount_handlesCommas() {
        assertEquals(
            BigDecimal("197317.00"),
            UnrecognizedSmsPrefillParser.extractAmount("INR 1,97,317.00 deposited"),
        )
    }
}
