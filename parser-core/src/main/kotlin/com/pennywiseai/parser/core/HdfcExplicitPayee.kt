package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.CompiledPatterns

/**
 * Detects payee names embedded in HDFC SMS layouts (Sent UPI / IMPS credit).
 * Used to avoid quick-keyword rules overwriting a parser-extracted payee via
 * incidental matches (e.g. shared account last-4 in the SMS body).
 */
object HdfcExplicitPayee {

    private val SENT_TO_PAYEE = Regex(
        """To\s+([^\n]+?)\s*\n?\s*On\s+\d""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(sms: String): String? {
        CompiledPatterns.HDFC.IMPS_CREDIT_MERCHANT.find(sms)?.let { match ->
            val name = normalizePayee(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }
        SENT_TO_PAYEE.find(sms)?.let { match ->
            val name = normalizePayee(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }
        return null
    }

    /**
     * Returns true when [parsedMerchant] came from an explicit payee line in [sms]
     * and should not be replaced by a keyword rule label.
     */
    fun shouldPreserveMerchant(sms: String?, parsedMerchant: String): Boolean {
        val merchant = parsedMerchant.trim()
        if (merchant.isEmpty() || merchant.equals("Unknown Merchant", ignoreCase = true)) {
            return false
        }
        val payee = sms?.let(::extract) ?: return false
        return namesAlign(payee, merchant)
    }

    internal fun normalizePayee(raw: String): String {
        var cleaned = raw.trim().trim('-', '*', ' ')
        cleaned = cleaned.replace(CompiledPatterns.Cleaning.PVT_LTD, "")
        cleaned = cleaned.replace(CompiledPatterns.Cleaning.LTD, "")
        cleaned = cleaned.replace(CompiledPatterns.Cleaning.TRAILING_DASH, "")
        return cleaned.trim()
    }

    internal fun namesAlign(explicitPayee: String, parsedMerchant: String): Boolean {
        val left = explicitPayee.trim()
        val right = parsedMerchant.trim()
        if (left.equals(right, ignoreCase = true)) return true

        val leftNorm = normalizeToken(left)
        val rightNorm = normalizeToken(right)
        if (leftNorm == rightNorm) return true
        if (leftNorm.length >= 4 && rightNorm.length >= 4) {
            return leftNorm.contains(rightNorm) || rightNorm.contains(leftNorm)
        }
        return false
    }

    private fun normalizeToken(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}
