package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionWithSplits
import com.spendly.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

/**
 * Nets linked refunds out of spend.
 *
 * Refund INCOME rows are removed from the list and the refunded amount is
 * deducted from the original expense (floored at zero). The deduction is keyed by
 * the original's id, so it lands in the original's month even when the refund
 * row itself is outside the date range being summed.
 */
object RefundAdjustments {

    /** Sum of linked refund amounts per original expense id. */
    fun refundedByOriginal(linkedRefunds: List<TransactionEntity>): Map<Long, BigDecimal> =
        linkedRefunds
            .filter { it.transactionType == TransactionType.INCOME && !it.isDeleted }
            .mapNotNull { r -> r.refundOfTransactionId?.let { it to r.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO) { a, b -> a + b } }

    fun isLinkedRefund(t: TransactionEntity): Boolean =
        t.transactionType == TransactionType.INCOME && t.refundOfTransactionId != null

    /** Amount of [t] after deducting refunds; unchanged when nothing is linked. */
    fun effectiveAmount(t: TransactionEntity, refunded: Map<Long, BigDecimal>): BigDecimal {
        val refund = refunded[t.id] ?: return t.amount
        return (t.amount - refund).max(BigDecimal.ZERO)
    }

    /** Fraction of [t] still counted as spend (1 = no refund, 0 = fully refunded). */
    fun remainingFraction(t: TransactionEntity, refunded: Map<Long, BigDecimal>): BigDecimal {
        if (t.amount.signum() == 0) return BigDecimal.ONE
        val effective = effectiveAmount(t, refunded)
        return effective.divide(t.amount, 10, java.math.RoundingMode.HALF_UP)
    }

    fun net(
        transactions: List<TransactionEntity>,
        refunded: Map<Long, BigDecimal>
    ): List<TransactionEntity> {
        if (refunded.isEmpty() && transactions.none(::isLinkedRefund)) return transactions
        return transactions.mapNotNull { t ->
            when {
                isLinkedRefund(t) -> null
                refunded.containsKey(t.id) -> t.copy(amount = effectiveAmount(t, refunded))
                else -> t
            }
        }
    }

    /** Split-aware variant: refunded share is spread proportionally across splits. */
    fun netWithSplits(
        transactions: List<TransactionWithSplits>,
        refunded: Map<Long, BigDecimal>
    ): List<TransactionWithSplits> {
        if (refunded.isEmpty() && transactions.none { isLinkedRefund(it.transaction) }) return transactions
        return transactions.mapNotNull { tws ->
            val t = tws.transaction
            when {
                isLinkedRefund(t) -> null
                refunded.containsKey(t.id) -> {
                    val fraction = remainingFraction(t, refunded)
                    tws.copy(
                        transaction = t.copy(amount = effectiveAmount(t, refunded)),
                        splits = tws.splits.map { it.copy(amount = it.amount.multiply(fraction)) }
                    )
                }
                else -> tws
            }
        }
    }
}
