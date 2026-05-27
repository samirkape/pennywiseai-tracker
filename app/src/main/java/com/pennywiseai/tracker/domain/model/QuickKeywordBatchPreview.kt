package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleCompiler
import java.math.BigDecimal
import java.time.LocalDateTime

/** One transaction that will change if the user confirms batch apply. */
data class QuickKeywordBatchChange(
    val transactionId: Long,
    val amount: BigDecimal,
    val dateTime: LocalDateTime,
    val beforeMerchant: String,
    val afterMerchant: String,
    val beforeCategory: String,
    val afterCategory: String,
    val beforeType: TransactionType,
    val afterType: TransactionType,
    val matchedKeyword: String?,
    val before: TransactionEntity,
    val after: TransactionEntity,
) {
    val merchantChanges: Boolean =
        !beforeMerchant.equals(afterMerchant, ignoreCase = true)
    val categoryChanges: Boolean =
        !beforeCategory.equals(afterCategory, ignoreCase = true)
    val typeChanges: Boolean = beforeType != afterType
    val tagsChanges: Boolean = before.tags != after.tags
}

data class QuickKeywordBatchPreview(
    val ruleName: String,
    val applyScope: QuickKeywordApplyScope,
    val poolSize: Int,
    val keywordMatched: Int,
    val willUpdate: Int,
    val alreadyLabeled: Int,
    val sampleChanges: List<QuickKeywordBatchChange>,
    val pendingChanges: List<QuickKeywordBatchChange>,
    val matchStats: QuickKeywordMatchStats = QuickKeywordMatchStats(),
)

data class PendingKeywordBatchApply(
    val ruleName: String,
    val input: QuickKeywordRuleCompiler.QuickKeywordRuleInput,
    val applyScope: QuickKeywordApplyScope,
    val preview: QuickKeywordBatchPreview,
)

data class KeywordBatchUndoSession(
    val ruleName: String,
    val appliedAtMillis: Long,
    val expiresAtMillis: Long,
    val transactionCount: Int,
) {
    fun remainingMinutes(nowMillis: Long = System.currentTimeMillis()): Int {
        val remaining = expiresAtMillis - nowMillis
        if (remaining <= 0) return 0
        return ((remaining + 59_999) / 60_000).toInt()
    }
}
