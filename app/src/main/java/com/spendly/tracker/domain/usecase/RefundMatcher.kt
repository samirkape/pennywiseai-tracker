package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.utils.MerchantNameMatcher
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scores expense rows as potential originals of a refund (INCOME) row.
 *
 * Pure logic with no storage access so it can be unit tested. Candidates are
 * pre-filtered by the DAO (type, currency, deleted/excluded, loan); this class
 * applies the date window and remaining-refundable checks, then ranks.
 */
@Singleton
class RefundMatcher @Inject constructor() {

    data class Candidate(
        val original: TransactionEntity,
        val score: Int,
        val merchantMatched: Boolean,
        val remainingRefundable: BigDecimal
    )

    /**
     * @param alreadyRefunded sum of refunds already linked to each original id.
     * @return viable candidates, best first.
     */
    fun rank(
        refund: TransactionEntity,
        candidates: List<TransactionEntity>,
        alreadyRefunded: Map<Long, BigDecimal>
    ): List<Candidate> {
        if (refund.transactionType != TransactionType.INCOME) return emptyList()
        return candidates.mapNotNull { original ->
            if (original.id == refund.id) return@mapNotNull null
            if (original.transactionType != TransactionType.EXPENSE &&
                original.transactionType != TransactionType.CREDIT
            ) return@mapNotNull null
            if (original.currency != refund.currency) return@mapNotNull null
            if (original.dateTime.isAfter(refund.dateTime)) return@mapNotNull null
            val ageDays = Duration.between(original.dateTime, refund.dateTime).toDays()
            if (ageDays > WINDOW_DAYS) return@mapNotNull null

            val remaining = original.amount - (alreadyRefunded[original.id] ?: BigDecimal.ZERO)
            if (remaining < refund.amount) return@mapNotNull null

            val merchantSim = MerchantNameMatcher.weightedSimilarity(
                original.merchantName,
                refund.merchantName
            )
            val merchantMatch = merchantSim >= MerchantNameMatcher.MATCH_THRESHOLD
            val exactAmount = original.amount.compareTo(refund.amount) == 0
            // A partial refund is only believable when the merchant lines up.
            if (!exactAmount && !merchantMatch) return@mapNotNull null

            var score = 0
            if (merchantMatch) score += SCORE_MERCHANT
            if (sameInstrument(original, refund)) score += SCORE_INSTRUMENT
            if (exactAmount) score += SCORE_EXACT_AMOUNT
            if (hasRefundKeyword(refund)) score += SCORE_KEYWORD
            score += recencyBonus(ageDays)

            Candidate(original, score, merchantMatch, remaining)
        }.sortedWith(
            compareByDescending<Candidate> { it.score }
                .thenByDescending { it.original.dateTime }
        )
    }

    /**
     * The match to link automatically, or null when it is not confident enough
     * (below threshold, or too close to the runner-up).
     */
    fun pickAutoMatch(ranked: List<Candidate>): Candidate? {
        val best = ranked.firstOrNull() ?: return null
        if (!best.merchantMatched) {
            // No merchant evidence: only trust a sole exact-amount candidate with
            // supporting signals (same instrument / refund wording / recent).
            return if (ranked.size == 1 && best.score >= AMOUNT_ONLY_THRESHOLD) best else null
        }
        if (best.score < AUTO_THRESHOLD) return null
        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null && best.score - runnerUp.score < AUTO_MARGIN) return null
        return best
    }

    private fun sameInstrument(a: TransactionEntity, b: TransactionEntity): Boolean {
        val accountA = a.accountNumber?.takeIf { it.isNotBlank() }
        val accountB = b.accountNumber?.takeIf { it.isNotBlank() }
        if (accountA != null && accountB != null) return accountA == accountB
        val bankA = a.bankName?.takeIf { it.isNotBlank() }
        val bankB = b.bankName?.takeIf { it.isNotBlank() }
        return bankA != null && bankB != null && bankA.equals(bankB, ignoreCase = true)
    }

    private fun hasRefundKeyword(refund: TransactionEntity): Boolean {
        val text = listOfNotNull(refund.smsBody, refund.description, refund.merchantName, refund.category)
            .joinToString(" ")
            .lowercase()
        return REFUND_KEYWORDS.any { text.contains(it) }
    }

    private fun recencyBonus(ageDays: Long): Int = when {
        ageDays <= 7 -> 10
        ageDays <= 30 -> 5
        else -> 0
    }

    companion object {
        const val WINDOW_DAYS = 90L
        const val SCORE_MERCHANT = 50
        const val SCORE_INSTRUMENT = 20
        const val SCORE_EXACT_AMOUNT = 20
        const val SCORE_KEYWORD = 5

        /** Merchant match needs at least one supporting signal on top of the name. */
        const val AUTO_THRESHOLD = 60
        const val AMOUNT_ONLY_THRESHOLD = 45
        const val AUTO_MARGIN = 15

        private val REFUND_KEYWORDS = listOf("refund", "reversal", "reversed", "cashback reversal")
    }
}
