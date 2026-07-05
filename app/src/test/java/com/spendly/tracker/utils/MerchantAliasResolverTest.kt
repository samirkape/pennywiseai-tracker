package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.MerchantAliasEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MerchantAliasResolverTest {

    @Test
    fun resolve_exactAlias() {
        val aliases = listOf(
            alias("fss4firstcry", "Firstcry"),
        )
        assertEquals("Firstcry", MerchantAliasResolver.resolve("fss4firstcry", aliases))
    }

    @Test
    fun resolve_fuzzyAlias() {
        val aliases = listOf(
            alias("fss4FIRSTCRY", "Firstcry"),
        )
        assertEquals("Firstcry", MerchantAliasResolver.resolve("fss4firstcry", aliases))
    }

    @Test
    fun resolve_unknown_returnsOriginal() {
        assertEquals("Amazon", MerchantAliasResolver.resolve("Amazon", emptyList()))
    }

    @Test
    fun resolveExact_unknownMerchant_neverFuzzyMatches() {
        val aliases = listOf(
            alias("561,5612 M.10", "Amazon"),
        )
        assertEquals("Unknown Merchant", MerchantAliasResolver.resolveExact("Unknown Merchant", aliases))
    }

    @Test
    fun resolve_skipsSuspiciousAliasForFuzzy() {
        val aliases = listOf(
            alias("561,5612 M.10", "Amazon"),
        )
        assertEquals("Unknown Merchant", MerchantAliasResolver.resolve("Unknown Merchant", aliases))
    }

    @Test
    fun resolve_shortAliasOnlyOnExactMatch() {
        val aliases = listOf(alias("Ra", "Cable"))
        assertEquals("Cable", MerchantAliasResolver.resolve("Ra", aliases))
        assertEquals("Prajwal Kirana", MerchantAliasResolver.resolve("Prajwal Kirana", aliases))
    }

    private fun alias(source: String, display: String) = MerchantAliasEntity(
        sourceMerchant = source,
        displayName = display,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
    )
}
