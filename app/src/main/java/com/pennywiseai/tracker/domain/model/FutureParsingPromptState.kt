package com.pennywiseai.tracker.domain.model

/** Prompt shown after edit save to optionally apply name/category to future SMS parsing. */
data class FutureParsingPromptState(
    val rawMerchantName: String,
    val displayMerchantName: String,
    val category: String,
    val merchantChanged: Boolean,
    val categoryChanged: Boolean,
    /** Truncated SMS body preview so the user sees context for optional aliases. */
    val smsSnippet: String? = null,
    /**
     * Additional exact-match alias sources detected from the SMS (not including [rawMerchantName]).
     * User picks which to save in the dialog.
     */
    val optionalBodyAliasSources: List<String> = emptyList(),
)
