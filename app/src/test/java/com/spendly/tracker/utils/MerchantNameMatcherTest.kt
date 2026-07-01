package com.spendly.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNameMatcherTest {

    @Test
    fun exactMatch_returnsOne() {
        assertEquals(1.0, MerchantNameMatcher.weightedSimilarity("Firstcry", "firstcry"), 0.001)
    }

    @Test
    fun smsVariant_matchesAboveThreshold() {
        val score = MerchantNameMatcher.weightedSimilarity("fss4firstcry", "fss4FIRSTCRY")
        assertTrue(score >= MerchantNameMatcher.MATCH_THRESHOLD)
    }

    @Test
    fun messyName_matchesCleanNameInHistory() {
        val score = MerchantNameMatcher.weightedSimilarity("fss4firstcry", "Firstcry")
        assertTrue(score >= MerchantNameMatcher.MATCH_THRESHOLD)
    }

    @Test
    fun unrelatedMerchants_belowThreshold() {
        val score = MerchantNameMatcher.weightedSimilarity("fss4firstcry", "Amazon")
        assertTrue(score < MerchantNameMatcher.MATCH_THRESHOLD)
    }

    @Test
    fun findBestDisplayName_returnsDisplayForFuzzySource() {
        val result = MerchantNameMatcher.findBestDisplayName(
            query = "fss4firstcry",
            candidates = listOf("fss4FIRSTCRY" to "Firstcry", "amazon" to "Amazon")
        )
        assertEquals("Firstcry", result)
    }

    @Test
    fun findBestDisplayName_returnsNullWhenBelowThreshold() {
        val result = MerchantNameMatcher.findBestDisplayName(
            query = "fss4firstcry",
            candidates = listOf("amazon" to "Amazon")
        )
        assertNull(result)
    }

    @Test
    fun findBestMerchantLabel_suggestsCleanerName() {
        val result = MerchantNameMatcher.findBestMerchantLabel(
            query = "fss4firstcry",
            knownMerchants = listOf("fss4firstcry", "Firstcry", "Swiggy")
        )
        assertEquals("Firstcry", result)
    }

    @Test
    fun findBestDisplayName_skipsSameAsQuery() {
        val result = MerchantNameMatcher.findBestDisplayName(
            query = "Firstcry",
            candidates = listOf("firstcry" to "Firstcry")
        )
        assertNull(result)
    }

    @Test
    fun findBestMerchantLabel_notNullForSimilarVariant() {
        assertNotNull(
            MerchantNameMatcher.findBestMerchantLabel(
                query = "FSS4FIRSTCRY",
                knownMerchants = listOf("fss4firstcry", "Firstcry")
            )
        )
    }

    @Test
    fun findSuggestions_returnsUpToThreeSortedByScore() {
        val result = MerchantNameMatcher.findSuggestions(
            query = "fss4firstcry",
            aliasCandidates = listOf("fss4FIRSTCRY" to "Firstcry"),
            merchantLabels = listOf("fss4firstcry", "Firstcry", "Swiggy", "Amazon"),
        )

        assertTrue(result.size <= MerchantNameMatcher.MAX_EDIT_SUGGESTIONS)
        assertTrue(result.isNotEmpty())
        assertEquals("Firstcry", result.first())
        assertTrue(result.none { it.equals("fss4firstcry", ignoreCase = true) })
    }

    @Test
    fun findSuggestions_excludesBelowSuggestionThreshold() {
        val result = MerchantNameMatcher.findSuggestions(
            query = "fss4firstcry",
            aliasCandidates = emptyList(),
            merchantLabels = listOf("Amazon"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun autocompleteMatches_prefersPrefixAndExcludesExactQuery() {
        val result = MerchantNameMatcher.autocompleteMatches(
            query = "ama",
            knownMerchants = listOf("Amazon", "Amazon Pay", "Swiggy", "ama"),
        )

        assertEquals(listOf("Amazon", "Amazon Pay"), result)
    }

    @Test
    fun autocompleteMatches_returnsEmptyForBlankQuery() {
        assertTrue(
            MerchantNameMatcher.autocompleteMatches(
                query = "  ",
                knownMerchants = listOf("Amazon"),
            ).isEmpty()
        )
    }
}
