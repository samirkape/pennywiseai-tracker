package com.spendly.tracker.connector.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNormalizerTest {

    @Test
    fun caseAndPunctuationAreIgnored() {
        assertEquals("blue tokai", MerchantNormalizer.normalize("BLUE TOKAI"))
        assertEquals("blue tokai", MerchantNormalizer.normalize("Blue  Tokai"))
        assertEquals("blue tokai", MerchantNormalizer.normalize("*Blue Tokai*"))
    }

    @Test
    fun paymentProcessorNoiseIsRemoved() {
        assertEquals("amazon", MerchantNormalizer.normalize("Amazon Pay UPI"))
        assertEquals("swiggy instamart", MerchantNormalizer.normalize("SWIGGY INSTAMART POS"))
        assertEquals("flipkart", MerchantNormalizer.normalize("Flipkart Debit Card Txn"))
    }

    @Test
    fun differentBrandsOfTheSameGroupStayApart() {
        assertTrue(
            MerchantNormalizer.normalize("Swiggy Instamart") != MerchantNormalizer.normalize("Swiggy")
        )
    }

    @Test
    fun companySuffixesAreRemoved() {
        assertEquals(
            "amazon seller",
            MerchantNormalizer.normalize("AMAZON SELLER SERVICES PVT LTD")
        )
    }

    @Test
    fun trailingReferenceNumbersAreRemoved() {
        assertEquals("amazon", MerchantNormalizer.normalize("Amazon 4471"))
        assertEquals("amazon", MerchantNormalizer.normalize("Amazon Order 889122"))
    }

    @Test
    fun differentMerchantsStayApart() {
        assertEquals("amazon", MerchantNormalizer.normalize("Amazon"))
        assertTrue(MerchantNormalizer.normalize("Amazon") != MerchantNormalizer.normalize("Flipkart"))
    }

    @Test
    fun emptyNamesFallBackToTheOriginal() {
        assertEquals("", MerchantNormalizer.normalize("   "))
        assertEquals("upi", MerchantNormalizer.normalizeOrFallback("UPI"))
    }
}
