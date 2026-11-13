package com.spendly.tracker.utils

/**
 * Normalizes merchant names to a consistent title-cased format.
 *
 * Bank SMS texts come with arbitrary casing, e.g. "THE NEW INDIA Assurance Company"
 * where the first words are SHOUTING but the later words are human-cased. This
 * normalizer works word-by-word:
 *
 * - Words already human-cased (mixed upper/lower, e.g. "Assurance", "McDonald's")
 *   are kept as-is.
 * - Short all-caps tokens are treated as acronyms and kept uppercased
 *   (HDFC, NSE, ICCL), except common English dictionary words, which are almost
 *   never acronyms in a merchant name and are title-cased instead
 *   (THE -> The, NEW -> New, INDIA -> India).
 * - Lowercase and long all-caps words are title-cased (COMPANY -> Company).
 * - Tokens containing digits or symbols are identifiers and kept as-is
 *   (e.g. "4R", "A/C").
 */
object MerchantNameNormalizer {

    /**
     * Short all-caps tokens that are common English words rather than acronyms.
     * Real acronyms like HDFC, NSE, ICCL don't appear here and stay uppercased.
     */
    private val ALL_CAPS_DICTIONARY_WORDS = setOf(
        "THE", "AND", "OF", "FOR", "NEW", "INDIA", "LTD", "PVT", "LTD.", "PVT.",
        "NO", "LIFE", "TRUST", "BANK", "IND", "CO", "CO.", "COMPANY",
        "CARD", "LOAN", "PAY", "GIFT", "GIFTS", "TO", "AT", "ON", "IN"
    )

    /**
     * Acronyms that contain TWO OR MORE vowels — the multi-vowel test (see
     * [normalizeWord]) would otherwise title-case them. Keep this list small
     * and verified; single-vowel acronyms (NSE, NPCI, UPI, IMPS…) pass the
     * vowel-count rule without an exception.
     */
    private val MULTI_VOWEL_ACRONYMS = setOf(
        "UPI", "UPI.", "ICICI", "IDBI", "ISIN", "AAI"
    )

    fun normalize(name: String): String {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        return trimmed.split(" ").joinToString(" ") { word -> normalizeWord(word) }
    }

    /**
     * Splits trailing punctuation (e.g. "COMPANY.", "INDIA,") off the word first,
     * so banks that shout words ending in a comma/period still get title-cased.
     */
    private fun normalizeWord(word: String): String {
        if (word.isEmpty()) return word
        val lastLetter = word.indexOfLast { it.isLetter() }
        if (lastLetter == -1) return word // pure symbol/number token

        val core = word.substring(0, lastLetter + 1)
        val suffix = word.substring(lastLetter + 1)

        return when {
            // Digits/symbols inside the token — an identifier (e.g. "4R", "A/C"), keep as-is
            !core.all { it.isLetter() } -> word
            // Already mixed case (e.g. "Assurance") — keep as-is
            core.any { it.isLowerCase() } && core.any { it.isUpperCase() } -> core
            // Short ALL-CAPS token: keep it as an acronym only when it doesn't look
            // like a name or a plain word. Real acronyms contain at most ONE vowel
            // (NSE, NPCI, UPI, HDFC, IMPS, RTGS); names and ordinary words contain
            // two or more (NITIN -> Nitin, KAPE -> Kape, AXIS -> Axis). Confirmed
            // multi-vowel acronyms (ICICI…) are in [MULTI_VOWEL_ACRONYMS].
            core.length <= 5 && core == core.uppercase() &&
                core.uppercase() !in ALL_CAPS_DICTIONARY_WORDS &&
                (core.uppercase().count { it in "AEIOU" } <= 1 ||
                    core.uppercase() in MULTI_VOWEL_ACRONYMS) -> core
            // Long all-caps / all-lowercase / shouty words — title-case them
            else -> core.lowercase().replaceFirstChar { it.uppercase() }
        } + suffix
    }
}
