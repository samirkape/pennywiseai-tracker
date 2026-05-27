package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MerchantAliasAuditorTest {

    @Test
    fun audit_flagsOpaqueAmazonAlias() {
        val result = MerchantAliasAuditor.audit(
            alias("561,5612 M.10", "Amazon"),
        )
        assertEquals(MerchantAliasAuditor.RiskLevel.HIGH, result.risk)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun audit_okForConsistentAlias() {
        val result = MerchantAliasAuditor.audit(
            alias("fss4firstcry", "Firstcry"),
        )
        assertEquals(MerchantAliasAuditor.RiskLevel.OK, result.risk)
    }

    @Test
    fun resolveExact_doesNotFuzzyMatchUnknownToRiskyAlias() {
        val aliases = listOf(alias("561,5612 M.10", "Amazon"))
        assertEquals(
            "Unknown Merchant",
            MerchantAliasResolver.resolveExact("Unknown Merchant", aliases),
        )
        assertEquals(
            "Unknown Merchant",
            MerchantAliasResolver.resolve("Unknown Merchant", aliases),
        )
    }

    private fun alias(source: String, display: String) = MerchantAliasEntity(
        sourceMerchant = source,
        displayName = display,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
    )
}
