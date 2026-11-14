package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.MerchantPrivacy
import com.spendly.tracker.connector.domain.TransactionBrief
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.domain.analytics.SpendingFilters

/**
 * Builds the optional per-transaction list for the shared brief.
 *
 * Only rows that count towards the spend / income totals are listed; transfers,
 * credit card bill payments, investments and loan-linked rows are covered by the
 * MONEY_MOVED summary instead of being repeated row by row.
 */
object TransactionBriefMapper {

    fun from(transactions: List<TransactionEntity>): List<TransactionBrief> =
        transactions
            .asSequence()
            .filter {
                !it.isExcludedFromTracking &&
                    it.amount.signum() > 0 &&
                    (SpendingFilters.countsAsSpending(it) || SpendingFilters.countsAsIncome(it))
            }
            .sortedByDescending { it.dateTime }
            .map { it.toBrief() }
            .toList()

    private fun TransactionEntity.toBrief(): TransactionBrief = TransactionBrief(
        date = dateTime.toLocalDate(),
        type = when (transactionType) {
            TransactionType.CREDIT -> TransactionBrief.Type.CARD
            TransactionType.INCOME -> TransactionBrief.Type.INCOME
            else -> TransactionBrief.Type.EXPENSE
        },
        amount = amount,
        category = category.takeIf { it.isNotBlank() } ?: UNCATEGORISED,
        merchant = MerchantPrivacy.safeMerchantName(merchantName)
            .takeIf { it.isNotBlank() }
            ?: UNKNOWN_MERCHANT
    )

    private const val UNCATEGORISED = "Uncategorised"
    private const val UNKNOWN_MERCHANT = "Unknown"
}
