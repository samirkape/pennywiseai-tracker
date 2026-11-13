package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType

/**
 * In-memory transaction search used by the transactions list filter bar.
 *
 * Free-text matching across every searchable field (merchant, description,
 * SMS body, tags, category, reference, bank, amount, type keyword). Field-
 * and operator-aware queries (Kibana-style) are handled by [TransactionQuery];
 * its free-text leaves delegate to [matchesText].
 */
object TransactionSearchMatcher {

    fun matches(transaction: TransactionEntity, rawQuery: String): Boolean =
        matchesText(transaction, rawQuery.trim())

    /**
     * Broad (unscoped) matching of a single term/phrase against every
     * searchable field. Also used as the fallback for plain-text searches and
     * for parse-error recovery by [TransactionQuery.matches].
     */
    fun matchesText(transaction: TransactionEntity, query: String): Boolean {
        if (query.isEmpty()) return true

        if (transaction.merchantName.contains(query, ignoreCase = true)) return true
        if (transaction.description.orEmpty().contains(query, ignoreCase = true)) return true
        if (transaction.smsBody.orEmpty().contains(query, ignoreCase = true)) return true
        if (transaction.tags.contains(query, ignoreCase = true)) return true
        if (transaction.category.contains(query, ignoreCase = true)) return true
        if (transaction.reference.orEmpty().contains(query, ignoreCase = true)) return true
        if (transaction.bankName.orEmpty().contains(query, ignoreCase = true)) return true

        if (matchesAmount(transaction, query)) return true
        if (matchesTransactionTypeKeyword(transaction.transactionType, query)) return true

        return false
    }

    private fun matchesAmount(transaction: TransactionEntity, query: String): Boolean {
        return try {
            val cleanedQuery = query.replace(",", "").replace(" ", "").trim()
            if (cleanedQuery.isNotEmpty() && cleanedQuery.all { it.isDigit() || it == '.' }) {
                val amountString = transaction.amount.toPlainString()
                amountString.contains(cleanedQuery) ||
                    amountString.replace(",", "").contains(cleanedQuery)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesTransactionTypeKeyword(type: TransactionType, query: String): Boolean {
        val keywords = when (type) {
            TransactionType.CREDIT -> listOf("card", "credit card")
            TransactionType.INCOME -> listOf("income")
            TransactionType.EXPENSE -> listOf("expense")
            TransactionType.TRANSFER -> listOf("transfer")
            TransactionType.INVESTMENT -> listOf("investment", "invest")
        }
        return keywords.any { keyword -> keyword.contains(query, ignoreCase = true) }
    }
}
