package com.spendly.tracker.connector.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantPrivacyTest {

    @Test
    fun plainBrandNamesPassThrough() {
        assertEquals("Amazon", MerchantPrivacy.safeMerchantName("Amazon"))
        assertEquals("Blue Tokai Coffee", MerchantPrivacy.safeMerchantName("Blue Tokai Coffee"))
    }

    @Test
    fun upiHandlesAreMasked() {
        assertEquals(
            MerchantPrivacy.MASKED_MERCHANT,
            MerchantPrivacy.safeMerchantName("9876543210@paytm")
        )
        assertEquals(
            MerchantPrivacy.MASKED_MERCHANT,
            MerchantPrivacy.safeMerchantName("samir.kape@okhdfcbank")
        )
    }

    @Test
    fun barePhoneNumbersAreMasked() {
        assertEquals(
            MerchantPrivacy.MASKED_MERCHANT,
            MerchantPrivacy.safeMerchantName("9876543210")
        )
        assertEquals(
            MerchantPrivacy.MASKED_MERCHANT,
            MerchantPrivacy.safeMerchantName("+919876543210")
        )
    }

    @Test
    fun emailAddressesAreMasked() {
        assertEquals(
            MerchantPrivacy.MASKED_MERCHANT,
            MerchantPrivacy.safeMerchantName("someone@example.com")
        )
    }
}
