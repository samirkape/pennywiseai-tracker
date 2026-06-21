package com.pennywiseai.tracker.domain.model

/**
 * Confidence tier for a merchant rename match, derived from its similarity score.
 *
 * Tiers drive the grouped Phase 2 review UI:
 * - EXACT  → bulk "Apply all" (safe, no individual review needed)
 * - CLOSE  → bulk "Apply" (user opts in, sees sample merchants first)
 * - FUZZY  → individual flash-card review one by one
 */
enum class ConfidenceTier {
    EXACT,
    CLOSE,
    FUZZY;

    companion object {
        const val EXACT_THRESHOLD = 0.95
        const val CLOSE_THRESHOLD = 0.80

        fun from(similarityScore: Double): ConfidenceTier = when {
            similarityScore >= EXACT_THRESHOLD -> EXACT
            similarityScore >= CLOSE_THRESHOLD -> CLOSE
            else -> FUZZY
        }
    }
}

