package com.spendly.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantNameNormalizerTest {

    @Test
    fun `bank SMS shouty names are title cased`() {
        assertEquals(
            "The New India Assurance Company",
            MerchantNameNormalizer.normalize("THE NEW INDIA Assurance Company")
        )
        // "CRED" contains a single vowel -> still acronym, matches its brand styling
        assertEquals(
            "CRED Payment Partner India",
            MerchantNameNormalizer.normalize("CRED PAYMENT PARTNER india")
        )
    }

    @Test
    fun `acronyms stay uppercased`() {
        assertEquals(
            "HDFC Bank Card Payment",
            MerchantNameNormalizer.normalize("HDFC Bank Card Payment".uppercase())
        )
        assertEquals(
            "NSE Clearing Ltd",
            MerchantNameNormalizer.normalize("NSE CLEARING LTD")
        )
        assertEquals(
            "NEFT CR Amazon Retail India",
            MerchantNameNormalizer.normalize("NEFT CR AMAZON RETAIL INDIA")
        )
        assertEquals(
            "UPI Transfer To Merchant",
            MerchantNameNormalizer.normalize("UPI TRANSFER TO MERCHANT")
        )
    }

    @Test
    fun `shouty person names from UPI SMS are title cased`() {
        assertEquals(
            "Nitin Deoram Kape",
            MerchantNameNormalizer.normalize("NITIN Deoram KAPE")
        )
        assertEquals(
            "Samir Nitin Kape",
            MerchantNameNormalizer.normalize("SAMIR NITIN KAPE")
        )
        assertEquals(
            "Honey Gifts Shopee",
            MerchantNameNormalizer.normalize("HONEY GIFTS Shopee")
        )
    }

    @Test
    fun `lowercase words are title cased, not left alone - partial normalization bug`() {
        assertEquals(
            "The New India Assurance",
            MerchantNameNormalizer.normalize("The New India assurance")
        )
        assertEquals(
            "Swiggy Limited",
            MerchantNameNormalizer.normalize("Swiggy limited")
        )
    }

    @Test
    fun `single vowel acronyms are kept while multi vowel words title case`() {
        assertEquals(
            "CRED Payment Partner India",
            MerchantNameNormalizer.normalize("CRED PAYMENT PARTNER india")
        )
        // AXIS (2 vowels) title-cases to the brand's own lowercase-styled form
        assertEquals(
            "Axis Bank Transfer",
            MerchantNameNormalizer.normalize("AXIS BANK TRANSFER")
        )
    }

    @Test
    fun `trailing punctuation is preserved and does not block title casing`() {
        assertEquals(
            "The New India Assurance Company.",
            MerchantNameNormalizer.normalize("THE NEW INDIA ASSURANCE COMPANY.")
        )
        assertEquals(
            "Swiggy Limited, Income",
            MerchantNameNormalizer.normalize("SWIGGY Limited, Income")
        )
        assertEquals(
            "A/C NPCI Transfer",
            MerchantNameNormalizer.normalize("A/C NPCI TRANSFER")
        )
    }

    @Test
    fun `already human cased words are kept`() {
        assertEquals("Amazon", MerchantNameNormalizer.normalize("Amazon"))
        assertEquals("Amazon", MerchantNameNormalizer.normalize("AMAZON"))
        assertEquals("Amazon Pay", MerchantNameNormalizer.normalize("Amazon PAY"))
    }

    @Test
    fun `mixed shouty and lowercase input`() {
        assertEquals(
            "Flipkart Credit Income",
            MerchantNameNormalizer.normalize("Flipkart CREDIT income")
        )
        // Idempotency: normalizing an already normalized name changes nothing
        val once = MerchantNameNormalizer.normalize("THE NEW INDIA Assurance Company")
        assertEquals(once, MerchantNameNormalizer.normalize(once))
    }
}
