package com.spendly.tracker.connector.domain

/**
 * Strips identifying tokens from merchant strings before they leave the device.
 *
 * Merchant names are usually plain brand names, but UPI rows can store a VPA
 * (`9876543210@paytm`), a bare mobile number or an email address. Those identify a
 * person, so they are replaced with a neutral label instead of being shared.
 */
object MerchantPrivacy {

    const val MASKED_MERCHANT = "UPI contact"

    private val VPA_OR_EMAIL = Regex("""[A-Za-z0-9._%+-]{2,}@[A-Za-z0-9.-]{2,}""")
    private val PHONE = Regex("""(?<!\d)(?:\+?91)?\d{10}(?!\d)""")

    fun safeMerchantName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (VPA_OR_EMAIL.containsMatchIn(trimmed)) return MASKED_MERCHANT
        if (PHONE.containsMatchIn(trimmed)) return MASKED_MERCHANT
        return trimmed
    }
}
