package com.pennywiseai.tracker.data.database.entity

import android.util.Log
import androidx.room.Embedded
import androidx.room.Relation
import java.math.BigDecimal

data class TransactionWithSplits(
    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val splits: List<TransactionSplitEntity>
) {
    val hasSplits: Boolean
        get() = splits.isNotEmpty()

    /**
     * Returns amount breakdown by category.
     * If transaction has splits, returns split amounts.
     * Otherwise, returns the full transaction amount under its category.
     */
    fun getAmountByCategory(): Map<String, BigDecimal> {
        return if (hasSplits) {
            // Group by category and sum amounts (in case of duplicates)
            val result = splits.groupBy { it.category }
                .mapValues { (_, splitList) -> splitList.sumOf { it.amount } }
            val splitsTotal = result.values.fold(BigDecimal.ZERO) { a, b -> a + b }
            val diff = (transaction.amount - splitsTotal).abs()
            if (diff > BigDecimal("0.01")) {
                Log.w("BudgetBucket", "SPLIT_MISMATCH id=${transaction.id} merchant='${transaction.merchantName}' txAmt=${transaction.amount} splitsTotal=$splitsTotal diff=$diff categories=$result")
            } else {
                Log.d("BudgetBucket", "  getAmountByCategory[splits] id=${transaction.id} merchant='${transaction.merchantName}' txAmt=${transaction.amount} → $result")
            }
            result
        } else {
            Log.d("BudgetBucket", "  getAmountByCategory[primary] id=${transaction.id} merchant='${transaction.merchantName}' txAmt=${transaction.amount} cat='${transaction.category}'")
            mapOf(transaction.category to transaction.amount)
        }
    }

    /**
     * Calculates total of all splits. Should equal transaction.amount when valid.
     */
    fun getSplitsTotal(): BigDecimal {
        return splits.sumOf { it.amount }
    }

    /**
     * Checks if splits are valid (sum equals transaction amount within tolerance).
     */
    fun areSplitsValid(tolerance: BigDecimal = BigDecimal("0.01")): Boolean {
        if (!hasSplits) return true
        val difference = (transaction.amount - getSplitsTotal()).abs()
        return difference <= tolerance
    }
}
