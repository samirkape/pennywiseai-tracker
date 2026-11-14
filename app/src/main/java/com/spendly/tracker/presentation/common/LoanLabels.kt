package com.spendly.tracker.presentation.common

import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType

/**
 * Short label describing what a loan-linked row represents, e.g. "Lent to Asha".
 *
 * Loan-linked rows move money between parties instead of being spent or earned, so the
 * label keeps them from reading as a plain debit/credit in the transaction list.
 */
fun loanLabelFor(transaction: TransactionEntity, loan: LoanEntity): String = when {
    loan.direction == LoanDirection.LENT && transaction.transactionType == TransactionType.INCOME ->
        "Repayment from ${loan.personName}"
    loan.direction == LoanDirection.BORROWED && transaction.transactionType == TransactionType.EXPENSE ->
        "Repaid to ${loan.personName}"
    loan.direction == LoanDirection.LENT -> "Lent to ${loan.personName}"
    else -> "Borrowed from ${loan.personName}"
}
