package com.pennywiseai.tracker.domain.model

/** A merchant label in history that fuzzy-matches the name being renamed. */
data class MerchantRenameMatch(
    val sourceMerchant: String,
    val similarityScore: Double,
)
