package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.domain.model.MerchantRenameMatch
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Finds historical merchant labels similar to [originalMerchant] for bulk rename review.
 */
object MerchantRenameMatcher {

    private const val NAME_WEIGHT = 0.70
    private const val TEXT_BRIDGE_WEIGHT = 0.20
    private const val ENTITY_COMPAT_WEIGHT = 0.10
    private const val HIGH_NAME_CONFIDENCE = 0.90
    private const val MIN_TEXT_BRIDGE = 0.25
    private const val MIN_ENTITY_COMPAT = 0.40

    private val BUSINESS_KEYWORDS = setOf(
        "bank", "pay", "mart", "store", "shop", "hotel", "cafe", "food", "fuel",
        "petrol", "hospital", "clinic", "travel", "air", "airways", "telecom", "mall",
        "super", "online", "recharge", "bill", "finance", "ltd", "llp", "pvt", "corp",
    )

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
                val nameScore = MerchantNameMatcher.weightedSimilarity(original, details.merchantName)
                val textBridge = textBridgeScore(original, details.merchantName)
                val entityCompat = entityCompatibilityScore(target, details.merchantName)
                val hybridScore =
                    NAME_WEIGHT * nameScore +
                        TEXT_BRIDGE_WEIGHT * textBridge +
                        ENTITY_COMPAT_WEIGHT * entityCompat
                MatchScore(
                    details = details,
                    nameScore = nameScore,
                    textBridge = textBridge,
                    entityCompat = entityCompat,
                    hybridScore = hybridScore,
                )
            }
            // Guardrails: ignore likely false positives unless lexical similarity is very strong.
            .filter { it.nameScore >= MerchantNameMatcher.MATCH_THRESHOLD }
            .filter {
                val nameScore = it.nameScore
                val textBridge = it.textBridge
                val entityCompat = it.entityCompat
                nameScore >= HIGH_NAME_CONFIDENCE ||
                    (textBridge >= MIN_TEXT_BRIDGE && entityCompat >= MIN_ENTITY_COMPAT)
            }
            .filter { it.hybridScore >= MerchantNameMatcher.MATCH_THRESHOLD }
            .sortedWith(
                compareByDescending<MatchScore> { it.hybridScore }
                    .thenByDescending { it.nameScore }
                    .thenBy { it.details.merchantName.lowercase() }
            )
            .map { score ->
                MerchantRenameMatch(
                    sourceMerchant = score.details.merchantName,
                    similarityScore = score.hybridScore,
                )
            }
            .toList()
    }

    private data class MatchScore(
        val details: MerchantMatchDetails,
        val nameScore: Double,
        val textBridge: Double,
        val entityCompat: Double,
        val hybridScore: Double,
    )

    private fun textBridgeScore(left: String, right: String): Double {
        val leftNorm = normalizeAlphaNumeric(left)
        val rightNorm = normalizeAlphaNumeric(right)
        if (leftNorm.isEmpty() || rightNorm.isEmpty()) return 0.0
        if (leftNorm == rightNorm) return 1.0

        val shorterContains = when {
            leftNorm.length <= rightNorm.length && rightNorm.contains(leftNorm) -> {
                leftNorm.length.toDouble() / rightNorm.length
            }
            rightNorm.length < leftNorm.length && leftNorm.contains(rightNorm) -> {
                rightNorm.length.toDouble() / leftNorm.length
            }
            else -> 0.0
        }

        val tokenJaccard = tokenJaccardScore(left, right)
        return maxOf(shorterContains, tokenJaccard)
    }

    private fun entityCompatibilityScore(target: String, candidate: String): Double {
        val targetIsPerson = isLikelyPersonName(target)
        val candidateIsPerson = isLikelyPersonName(candidate)
        return when {
            targetIsPerson == candidateIsPerson -> 1.0
            candidateIsPerson -> 0.2
            else -> 0.6
        }
    }

    private fun isLikelyPersonName(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return false
        if (normalized.any { it.isDigit() }) return false

        val tokens = normalized
            .split(Regex("[^A-Za-z]+"))
            .filter { it.length >= 2 }
        if (tokens.size !in 2..4) return false
        if (tokens.any { token -> BUSINESS_KEYWORDS.contains(token.lowercase()) }) return false

        return true
    }

    private fun normalizeAlphaNumeric(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun tokenJaccardScore(left: String, right: String): Double {
        val leftTokens = left
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
        val rightTokens = right
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()

        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val intersection = leftTokens.intersect(rightTokens).size
        val union = leftTokens.union(rightTokens).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
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
