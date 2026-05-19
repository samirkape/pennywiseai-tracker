package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class MerchantRenameMatcherTest {

    private fun sampleDetails(merchant: String, count: Int = 2) =
        MerchantRenameMatcher.MerchantMatchDetails(
            merchantName = merchant,
            transactionCount = count,
            sample = MerchantRenameMatcher.MerchantSample(
                BigDecimal("100"),
                "INR",
                LocalDateTime.of(2025, 1, 1, 12, 0),
                "Shopping",
            ),
        )

    @Test
    fun findCandidates_includesFuzzyVariants_sortedByScore() {
        val result = MerchantRenameMatcher.findCandidates(
            originalMerchant = "Firstcry",
            newMerchantName = "FirstCry Kids",
            merchantDetails = listOf(
                sampleDetails("fss4FIRSTCRY"),
                sampleDetails("fss4firstcry"),
                sampleDetails("Amazon"),
                sampleDetails("FirstCry Kids"),
            ),
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.similarityScore >= MerchantNameMatcher.MATCH_THRESHOLD })
        assertTrue(result[0].similarityScore >= result[1].similarityScore)
    }

    @Test
    fun findCandidates_excludesTargetNameAndUnrelated() {
        val result = MerchantRenameMatcher.findCandidates(
            originalMerchant = "Firstcry",
            newMerchantName = "FirstCry Kids",
            merchantDetails = listOf(
                sampleDetails("FirstCry Kids"),
                sampleDetails("Amazon"),
            ),
        )

        assertTrue(result.isEmpty())
    }
}
