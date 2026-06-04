package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity

/**
 * Derives extra merchant-alias *sources* from the raw SMS body so future transactions
 * that parse a different substring of the same message still map to the user's display name.
 * Ingest only applies [MerchantAliasRepository.resolveDisplayNameForIngest] on **exact**
 * parsed-merchant matches, so each hint must be a plausible standalone parser output.
 */
object SmsMerchantAliasHints {

    private val tokenSplit = Regex("[^A-Za-z0-9*]+")

    private const val MIN_TOKEN_LEN = 4
    private const val MAX_TOKEN_LEN = 48
    private const val MAX_EXTRAS = 4
    private const val SNIPPET_MAX = 160

    /** Single-line preview of the SMS for the future-parsing dialog (no logic on content). */
    fun snippetForUi(smsBody: String?): String? {
        val s = smsBody?.trim()?.replace('\n', ' ')?.replace('\r', ' ') ?: return null
        if (s.isEmpty()) return null
        val clipped = s.take(SNIPPET_MAX)
        return if (s.length > SNIPPET_MAX) "$clipped…" else clipped
    }

    /**
     * Tokens from [smsBody] that are similar to [rawMerchant] and pass alias safety checks
     * for [displayMerchant], excluding [rawMerchant] itself.
     */
    fun deriveExtraAliasSources(
        smsBody: String?,
        rawMerchant: String,
        displayMerchant: String,
    ): List<String> {
        val raw = rawMerchant.trim()
        val display = displayMerchant.trim()
        if (smsBody.isNullOrBlank() || raw.isEmpty() || display.isEmpty()) return emptyList()
        if (raw.equals(display, ignoreCase = true)) return emptyList()

        val rawNorm = normalizeAlnum(raw)
        if (rawNorm.length < 2) return emptyList()

        val seen = mutableSetOf<String>()
        fun addKey(s: String) {
            val t = s.trim()
            if (t.length in MIN_TOKEN_LEN..MAX_TOKEN_LEN) {
                seen.add(t)
            }
        }

        for (part in tokenSplit.split(smsBody)) {
            val token = part.trim().trim('*')
            if (token.length !in MIN_TOKEN_LEN..MAX_TOKEN_LEN) continue
            if (token.any { it.isLetter() }.not()) continue
            if (token.equals(raw, ignoreCase = true)) continue
            if (token.equals(display, ignoreCase = true)) continue

            val tokNorm = normalizeAlnum(token)
            if (tokNorm.isEmpty() || tokNorm == rawNorm) continue

            val sim = MerchantNameMatcher.weightedSimilarity(token, raw)
            val contained = tokNorm.contains(rawNorm) || rawNorm.contains(tokNorm)
            val goodSim = sim >= MerchantNameMatcher.SUGGESTION_THRESHOLD
            val goodOverlap = contained && tokNorm.length <= rawNorm.length + 12

            if (!goodSim && !goodOverlap) continue

            val entity = MerchantAliasEntity(sourceMerchant = token, displayName = display)
            val audit = MerchantAliasAuditor.audit(entity)
            if (audit.risk == MerchantAliasAuditor.RiskLevel.HIGH) continue

            addKey(token)
        }

        return seen
            .map { it to MerchantNameMatcher.weightedSimilarity(it, raw) }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.lowercase() }
            .take(MAX_EXTRAS)
    }

    private fun normalizeAlnum(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }
}
