package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity

/**
 * Flags merchant aliases that are safe as exact rename targets but risky when
 * used for fuzzy matching at SMS ingest (can relabel unrelated merchants).
 */
object MerchantAliasAuditor {

    enum class RiskLevel { OK, REVIEW, HIGH }

    data class AuditResult(
        val alias: MerchantAliasEntity,
        val risk: RiskLevel,
        val sourceToDisplayScore: Double,
        val reasons: List<String>,
    ) {
        val isSuspicious: Boolean get() = risk != RiskLevel.OK
    }

    /** Placeholder merchants that must never be fuzzy-matched via unrelated aliases. */
    val INGEST_PLACEHOLDER_MERCHANTS = setOf(
        "unknown merchant",
        "unknown",
    )

    private const val UNRELATED_SOURCE_DISPLAY_SCORE = 0.45
    private const val WEAK_SOURCE_DISPLAY_SCORE = 0.55

    fun audit(alias: MerchantAliasEntity): AuditResult {
        val source = alias.sourceMerchant.trim()
        val display = alias.displayName.trim()
        val reasons = mutableListOf<String>()
        var risk = RiskLevel.OK

        if (source.equals(display, ignoreCase = true)) {
            return AuditResult(alias, RiskLevel.OK, 1.0, emptyList())
        }

        val score = MerchantNameMatcher.weightedSimilarity(source, display)
        when {
            score < UNRELATED_SOURCE_DISPLAY_SCORE -> {
                reasons.add("SMS label and display name look unrelated")
                risk = RiskLevel.HIGH
            }
            score < WEAK_SOURCE_DISPLAY_SCORE -> {
                reasons.add("SMS label only weakly matches the display name")
                risk = RiskLevel.REVIEW
            }
        }

        if (looksLikeOpaqueSmsCode(source)) {
            reasons.add("Source looks like a card or terminal code, not a shop name")
            risk = RiskLevel.HIGH
        }

        for (placeholder in INGEST_PLACEHOLDER_MERCHANTS) {
            val crossScore = MerchantNameMatcher.weightedSimilarity(placeholder, source)
            if (crossScore >= MerchantNameMatcher.MATCH_THRESHOLD) {
                reasons.add("Could wrongly rename \"$placeholder\" transactions to \"$display\"")
                risk = RiskLevel.HIGH
            }
        }

        return AuditResult(
            alias = alias,
            risk = risk,
            sourceToDisplayScore = score,
            reasons = reasons.distinct(),
        )
    }

    fun auditAll(aliases: List<MerchantAliasEntity>): List<AuditResult> =
        aliases
            .map { audit(it) }
            .sortedWith(
                compareByDescending<AuditResult> { it.risk.ordinal }
                    .thenBy { it.alias.sourceMerchant.lowercase() },
            )

    fun suspiciousAliases(aliases: List<MerchantAliasEntity>): List<AuditResult> =
        auditAll(aliases).filter { it.isSuspicious }

    /**
     * True when the string is mostly digits/punctuation (typical bank SMS noise).
     */
    fun looksLikeOpaqueSmsCode(source: String): Boolean {
        val trimmed = source.trim()
        if (trimmed.length < 4) return false
        val letters = trimmed.count { it.isLetter() }
        val digits = trimmed.count { it.isDigit() }
        if (digits >= 3 && letters <= 4) return true
        val digitRatio = digits.toDouble() / trimmed.length
        return digitRatio >= 0.25 && !trimmed.contains(' ', ignoreCase = true) &&
            letters < trimmed.length / 2
    }
}
