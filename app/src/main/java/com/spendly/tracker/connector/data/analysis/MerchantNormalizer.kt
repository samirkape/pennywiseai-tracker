package com.spendly.tracker.connector.data.analysis

/**
 * Reduces a merchant name to a stable key so the same shop is counted once.
 *
 * Bank SMS are noisy: the same merchant shows up as `AMAZON`, `Amazon Pay UPI` or
 * `AMAZON SELLER SERVICES PVT LTD`. Normalising folds those into one bucket while
 * the original name is still kept on every transaction row.
 *
 * The normaliser never invents a name — it only removes noise.
 */
object MerchantNormalizer {

    /** Payment-processor words that carry no merchant identity. */
    private val NOISE_WORDS = setOf(
        "upi", "pos", "txn", "txns", "transaction", "transactions", "purchase", "purchases",
        "payment", "payments", "paid", "debit", "credit", "card", "cards", "debitcard",
        "creditcard", "neft", "imps", "rtgs", "atm", "online", "internet", "mobile",
        "spend", "money", "tran", "transfer", "ref", "utr", "via", "by", "the",
        "pay", "order", "orders"
    )

    /** Legal suffixes that say nothing about which merchant it was. */
    private val COMPANY_SUFFIXES = setOf(
        "pvt", "pvtltd", "ltd", "limited", "llp", "inc", "llc", "plc", "co", "company",
        "private", "india", "technologies", "services", "retail", "enterprises"
    )

    private val SEPARATORS = Regex("[^a-z0-9]+")
    private val TRAILING_DIGITS = Regex("[0-9]{3,}$")

    /**
     * Normalised key for [raw]. Returns an empty string when there is nothing
     * usable, so callers can decide what to show.
     */
    fun normalize(raw: String): String {
        val lowered = raw.trim().lowercase()
        if (lowered.isEmpty()) return ""

        val words = lowered.split(SEPARATORS).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""

        val withoutTrailingNumbers = words.dropLastWhile { word ->
            word.length >= 3 && word.all { it.isDigit() }
        }.ifEmpty { words }

        val kept = withoutTrailingNumbers
            .filterNot { it in NOISE_WORDS }
            .filterNot { it in COMPANY_SUFFIXES }
            .ifEmpty { withoutTrailingNumbers }

        return kept.joinToString(" ").replace(TRAILING_DIGITS, "").trim()
    }

    /**
     * Normalised key, or the trimmed original when the name reduces to nothing
     * (a one-word merchant made only of noise words, for example).
     */
    fun normalizeOrFallback(raw: String): String =
        normalize(raw).ifEmpty { raw.trim().lowercase() }
}
