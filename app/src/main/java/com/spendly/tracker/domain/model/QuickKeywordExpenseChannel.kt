package com.spendly.tracker.domain.model

/**
 * Expense sub-type filter for keyword rules (maps to [TransactionType.EXPENSE] or [TransactionType.CREDIT]).
 */
enum class QuickKeywordExpenseChannel {
    ACCOUNT,
    CASH,
    CREDIT_CARD,
}
