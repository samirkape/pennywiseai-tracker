package com.pennywiseai.tracker.domain.model

/** Prompt shown after edit save to optionally apply name/category to future SMS parsing. */
data class FutureParsingPromptState(
    val rawMerchantName: String,
    val displayMerchantName: String,
    val category: String,
    val merchantChanged: Boolean,
    val categoryChanged: Boolean,
)
