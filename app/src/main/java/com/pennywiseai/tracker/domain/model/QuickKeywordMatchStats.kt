package com.pennywiseai.tracker.domain.model

/**
 * Live / batch scan counters for debugging keyword rule matching.
 */
data class QuickKeywordMatchStats(
    val poolSize: Int = 0,
    val transactionsInRange: Int = 0,
    val keywordMatched: Int = 0,
    val wouldUpdate: Int = 0,
    val alreadyLabeled: Int = 0,
    val typeRejected: Int = 0,
    val noKeywordHit: Int = 0,
    val emptySearchText: Int = 0,
    val rejectionSamples: List<String> = emptyList(),
)
