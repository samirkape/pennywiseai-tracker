package com.spendly.tracker.domain.usecase

import android.util.Log
import com.spendly.tracker.data.database.dao.TransactionDao
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.utils.TransactionSearchMatcher
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Links refund (INCOME) rows to the expense they reverse.
 *
 * Auto mode runs for new rows when the "auto-link refunds" setting is on. Manual
 * link/unlink always wins: it sets `refund_link_manually_edited`, after which
 * auto mode leaves that row alone.
 */
@Singleton
class RefundLinker @Inject constructor(
    private val transactionDao: TransactionDao,
    private val refundMatcher: RefundMatcher,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    /** Returns the original's id if an auto link was made. */
    suspend fun autoLinkIfApplicable(refund: TransactionEntity): Long? {
        if (refund.id <= 0L) return null
        if (refund.transactionType != TransactionType.INCOME) return null
        if (refund.refundOfTransactionId != null || refund.refundLinkManuallyEdited) return null
        if (!userPreferencesRepository.autoLinkRefundsEnabled.first()) return null

        val match = refundMatcher.pickAutoMatch(rankedCandidates(refund)) ?: return null
        transactionDao.setRefundOf(refund.id, match.original.id, manual = false)
        Log.d(TAG, "Auto-linked refund ${refund.id} to ${match.original.id}")
        return match.original.id
    }

    /** Ranked originals for the manual picker. */
    suspend fun rankedCandidates(refund: TransactionEntity): List<RefundMatcher.Candidate> {
        val candidates = transactionDao.findRefundOriginalCandidates(
            excludeId = refund.id,
            currency = refund.currency,
            dateStart = refund.dateTime.minus(RefundMatcher.WINDOW_DAYS, ChronoUnit.DAYS),
            dateEnd = refund.dateTime
        )
        val refunded = refundedAmountByOriginal(excludeRefundId = refund.id)
        return refundMatcher.rank(refund, candidates, refunded)
    }

    /**
     * Free-text search across all earlier debits (no date window, no amount or merchant
     * requirement) so partial refunds can be linked to any expense. Originals without
     * enough unrefunded amount left are omitted since they could not be linked.
     */
    suspend fun searchOriginals(
        refund: TransactionEntity,
        query: String,
        limit: Int = 50
    ): List<RefundMatcher.Candidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val candidates = transactionDao.findRefundOriginalCandidates(
            excludeId = refund.id,
            currency = refund.currency,
            dateStart = refund.dateTime.minusYears(SEARCH_YEARS),
            dateEnd = refund.dateTime
        )
        val refunded = refundedAmountByOriginal(excludeRefundId = refund.id)
        return candidates.asSequence()
            .filter { TransactionSearchMatcher.matches(it, trimmed) }
            .mapNotNull { original ->
                val remaining = original.amount - (refunded[original.id] ?: BigDecimal.ZERO)
                if (remaining < refund.amount) null
                else RefundMatcher.Candidate(original, 0, false, remaining)
            }
            .take(limit)
            .toList()
    }

    suspend fun linkManually(refundId: Long, originalId: Long): Boolean {
        val refund = transactionDao.getTransactionById(refundId) ?: return false
        val original = transactionDao.getTransactionById(originalId) ?: return false
        if (refund.transactionType != TransactionType.INCOME) return false
        if (original.transactionType != TransactionType.EXPENSE &&
            original.transactionType != TransactionType.CREDIT
        ) return false
        if (original.id == refund.id || original.refundOfTransactionId != null) return false
        val remaining = original.amount -
            (refundedAmountByOriginal(excludeRefundId = refundId)[originalId] ?: BigDecimal.ZERO)
        if (remaining < refund.amount) return false
        transactionDao.setRefundOf(refundId, originalId, manual = true)
        return true
    }

    suspend fun unlink(refundId: Long) {
        transactionDao.setRefundOf(refundId, null, manual = true)
    }

    private suspend fun refundedAmountByOriginal(excludeRefundId: Long): Map<Long, BigDecimal> =
        transactionDao.getAllLinkedRefunds()
            .filter { it.id != excludeRefundId }
            .groupBy { it.refundOfTransactionId!! }
            .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.amount } }

    private companion object {
        const val TAG = "RefundLinker"
        const val SEARCH_YEARS = 5L
    }
}
