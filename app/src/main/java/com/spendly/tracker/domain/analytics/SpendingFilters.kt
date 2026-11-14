package com.spendly.tracker.domain.analytics

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType

/**
 * Single source of truth for what counts as real spending and real income.
 *
 * Loan-linked rows move money between parties (money lent, loan repayments,
 * borrowed money) and must never be treated as spending or income. Credit card
 * purchases count as spend; the card bill payment is a transfer and is handled
 * separately so the same money is not counted twice.
 *
 * Analytics, pay-period views, home totals and the AI connector all call these
 * predicates so the same period always produces the same figures everywhere.
 *
 * Callers that read rows outside the database filter must also skip
 * [TransactionEntity.isExcludedFromTracking]; the queries used by Analytics and
 * the connector already filter those at the SQL level.
 */
object SpendingFilters {

    /** Expense or credit card purchase that is not loan-linked. */
    fun countsAsSpending(transaction: TransactionEntity): Boolean =
        (transaction.transactionType == TransactionType.EXPENSE ||
            transaction.transactionType == TransactionType.CREDIT) &&
            transaction.loanId == null

    /** Income that is not loan-linked (loan repayments are not real income). */
    fun countsAsIncome(transaction: TransactionEntity): Boolean =
        transaction.transactionType == TransactionType.INCOME &&
            transaction.loanId == null
}
