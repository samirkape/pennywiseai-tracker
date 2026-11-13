package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType

/**
 * The single definition of spending for all budget math (group summaries, trend chart,
 * widgets, breach alerts): EXPENSE/CREDIT outflows that are not loan-linked.
 *
 * Investments are savings, not spending, and transfers are money movement, so neither
 * consumes a budget. Mirrors analytics spending
 * ([com.spendly.tracker.presentation.common.matchesAnalyticsSpendingFilter]) so budgets
 * and insights always agree on what counts as spending.
 *
 * The per-transaction "exclude from tracking" flag intentionally does not apply here:
 * a budget is a plan, not a feed filter.
 */
fun TransactionEntity.countsTowardBudgetSpending(): Boolean =
    (transactionType == TransactionType.EXPENSE || transactionType == TransactionType.CREDIT) &&
        loanId == null
