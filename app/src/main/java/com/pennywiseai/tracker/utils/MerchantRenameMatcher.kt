package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.domain.model.MerchantRenameMatch
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Finds historical merchant labels similar to [originalMerchant] for bulk rename review.
 */
object MerchantRenameMatcher {

    fun findCandidates(
        originalMerchant: String,
        newMerchantName: String,
        merchantDetails: List<MerchantMatchDetails>,
    ): List<MerchantRenameMatch> {
        val original = originalMerchant.trim()
        val target = newMerchantName.trim()
        if (original.isEmpty() || target.isEmpty()) return emptyList()

        return merchantDetails
            .asSequence()
            .filter { !it.merchantName.equals(target, ignoreCase = true) }
            .map { details ->
                details to MerchantNameMatcher.weightedSimilarity(original, details.merchantName)
            }
            .filter { (_, score) -> score >= MerchantNameMatcher.MATCH_THRESHOLD }
            .sortedWith(
                compareByDescending<Pair<MerchantMatchDetails, Double>> { it.second }
                    .thenBy { it.first.merchantName.lowercase() }
            )
            .map { (details, score) ->
                MerchantRenameMatch(
                    sourceMerchant = details.merchantName,
                    similarityScore = score,
                )
            }
            .toList()
    }

    data class MerchantMatchDetails(
        val merchantName: String,
        val transactionCount: Int,
        val sample: MerchantSample,
    )

    data class MerchantSample(
        val amount: BigDecimal,
        val currency: String,
        val dateTime: LocalDateTime,
        val category: String,
    )
}
