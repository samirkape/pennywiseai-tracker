package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.MerchantAliasEntity

/**
 * Resolves SMS-style merchant strings to user-chosen display names using saved aliases.
 */
object MerchantAliasResolver {

    /** Aliases shorter than this only apply on exact source match, not fuzzy match. */
    const val MIN_FUZZY_ALIAS_SOURCE_LENGTH = 4

    /**
     * Used when saving SMS transactions: exact alias match only so fuzzy pairs cannot
     * relabel unrelated parsed merchants (e.g. Unknown Merchant → Amazon).
     */
    fun resolveExact(sourceMerchant: String, aliases: List<MerchantAliasEntity>): String {
        val source = sourceMerchant.trim()
        if (source.isEmpty() || aliases.isEmpty()) return source
        return aliases
            .firstOrNull { it.sourceMerchant.equals(source, ignoreCase = true) }
            ?.displayName
            ?: source
    }

    /**
     * Full resolution for UI suggestions: exact match, then fuzzy (>= 75%).
     */
    fun resolve(sourceMerchant: String, aliases: List<MerchantAliasEntity>): String {
        val source = sourceMerchant.trim()
        if (source.isEmpty() || aliases.isEmpty()) return source

        resolveExact(source, aliases).let { exact ->
            if (!exact.equals(source, ignoreCase = true)) return exact
        }

        if (source.lowercase() in MerchantAliasAuditor.INGEST_PLACEHOLDER_MERCHANTS) {
            return source
        }

        val candidates = aliases
            .filter { it.sourceMerchant.trim().length >= MIN_FUZZY_ALIAS_SOURCE_LENGTH }
            .filter { !MerchantAliasAuditor.audit(it).isSuspicious }
            .map { it.sourceMerchant to it.displayName }
        return MerchantNameMatcher.findBestDisplayName(source, candidates) ?: source
    }
}
