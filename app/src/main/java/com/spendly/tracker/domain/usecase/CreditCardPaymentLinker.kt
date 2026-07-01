package com.spendly.tracker.domain.usecase

import android.util.Log
import com.spendly.tracker.data.database.dao.TransactionDao
import com.spendly.tracker.data.database.entity.AccountBalanceEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import com.spendly.tracker.data.repository.AccountBalanceRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pairs the two SMS legs of a credit card bill payment together.
 *
 * A bill payment is observed twice: once as a debit from the user's bank account
 * (e.g. HDFC debit / IMPS / NEFT towards a credit card) and once as a credit on
 * the credit card itself (e.g. "payment of Rs X received on your ICICI CC"). To
 * avoid double-counting these as spending we tag both legs as
 * `TransactionType.TRANSFER + TransferKind.CC_BILL_PAYMENT` and link them via
 * `linked_transaction_id`. The link is also used to reduce the CC outstanding
 * on the credit card account.
 *
 * Matching strategy (kept deliberately conservative to avoid false positives):
 * - exact amount + currency
 * - within +/- 3 days of the bill-payment row
 * - the matching account is known to be a credit card (via [AccountBalanceEntity.isCreditCard])
 * - exactly one candidate (if ambiguous, no link is made; classification is enough
 *   to fix spending totals, so we err on the side of safety)
 */
@Singleton
class CreditCardPaymentLinker @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountBalanceRepository: AccountBalanceRepository
) {
    companion object {
        private const val TAG = "CCPaymentLinker"
        private const val WINDOW_DAYS = 3L
    }

    /**
     * Attempts to find and link the counterpart of [newTransaction]. Returns the
     * id of the linked counterpart, or null if no unambiguous match was found.
     *
     * Safe to call for any new transaction: it short-circuits when [newTransaction]
     * is neither a CC bill-payment leg nor a candidate CC-side row.
     */
    suspend fun linkIfApplicable(newTransaction: TransactionEntity): Long? {
        if (newTransaction.id <= 0L) return null
        if (newTransaction.linkedTransactionId != null) return newTransaction.linkedTransactionId

        val isBillPaymentLeg = newTransaction.transferKind == TransferKind.CC_BILL_PAYMENT &&
            newTransaction.transactionType == TransactionType.TRANSFER

        // Could also be a CC-side row that the parser left as CREDIT/INCOME but
        // the linker should still try to pair if the bank-side leg comes in later.
        val isCardSideCandidate = !isBillPaymentLeg && isPotentialCardSideRow(newTransaction)

        if (!isBillPaymentLeg && !isCardSideCandidate) return null

        val window = WINDOW_DAYS
        val start = newTransaction.dateTime.minusDays(window)
        val end = newTransaction.dateTime.plusDays(window)

        val candidates = transactionDao.findLinkCandidates(
            excludeId = newTransaction.id,
            amount = newTransaction.amount,
            currency = newTransaction.currency,
            dateStart = start,
            dateEnd = end
        )

        // Determine which candidates touch a known credit card account.
        val ccTouching = candidates.filter { isCardSideRow(it) || isBillPaymentRow(it) }
        if (ccTouching.size != 1) {
            if (ccTouching.size > 1) {
                Log.d(TAG, "Ambiguous CC link candidates (${ccTouching.size}) for tx#${newTransaction.id}; skipping")
            }
            return null
        }

        val counterpart = ccTouching.first()
        val now = LocalDateTime.now()

        transactionDao.setLinkedTransaction(
            transactionId = newTransaction.id,
            linkedId = counterpart.id,
            transferKind = TransferKind.CC_BILL_PAYMENT,
            now = now
        )
        transactionDao.setLinkedTransaction(
            transactionId = counterpart.id,
            linkedId = newTransaction.id,
            transferKind = TransferKind.CC_BILL_PAYMENT,
            now = now
        )

        // Re-classify the counterpart if needed so the CC-side row is also
        // excluded from spending. The original parser-assigned category is kept
        // unless the counterpart was previously marked EXPENSE (legacy data).
        if (counterpart.transactionType != TransactionType.TRANSFER) {
            transactionDao.reclassifyAsTransfer(
                transactionId = counterpart.id,
                type = TransactionType.TRANSFER,
                transferKind = TransferKind.CC_BILL_PAYMENT,
                forceCategory = if (counterpart.transactionType == TransactionType.EXPENSE ||
                    counterpart.transactionType == TransactionType.INCOME
                ) {
                    "Credit Card Payment"
                } else {
                    null
                },
                now = now
            )
        }

        Log.d(TAG, "Linked CC bill payment legs: tx#${newTransaction.id} <-> tx#${counterpart.id}")

        // Apply CC outstanding balance adjustment for the card-side row.
        applyOutstandingReduction(newTransaction, counterpart)

        return counterpart.id
    }

    private suspend fun isCardSideRow(row: TransactionEntity): Boolean {
        if (row.accountNumber == null || row.bankName == null) return false
        val balance = accountBalanceRepository.getLatestBalance(row.bankName, row.accountNumber)
        return balance?.isCreditCard == true
    }

    private suspend fun isBillPaymentRow(row: TransactionEntity): Boolean {
        return row.transferKind == TransferKind.CC_BILL_PAYMENT &&
            row.transactionType == TransactionType.TRANSFER
    }

    private fun isPotentialCardSideRow(row: TransactionEntity): Boolean {
        // Lightweight pre-filter to avoid DB lookups for every new transaction.
        // The linker still validates via account_balances before linking.
        return row.accountNumber != null && row.bankName != null && (
            row.transactionType == TransactionType.CREDIT ||
            row.transactionType == TransactionType.INCOME
        )
    }

    /**
     * Subtracts the bill-payment amount from the credit-card account's current
     * outstanding (its `current_balance` in account_balances).
     *
     * The card-side row is the one whose [TransactionEntity.accountNumber] maps
     * to a credit-card AccountBalanceEntity.
     */
    private suspend fun applyOutstandingReduction(
        a: TransactionEntity,
        b: TransactionEntity
    ) {
        val pair = listOf(a, b)
        val cardSide = pair.firstOrNull { row ->
            row.accountNumber != null && row.bankName != null &&
                accountBalanceRepository.getLatestBalance(row.bankName, row.accountNumber)
                    ?.isCreditCard == true
        } ?: return

        val bankName = cardSide.bankName ?: return
        val last4 = cardSide.accountNumber ?: return
        val account = accountBalanceRepository.getLatestBalance(bankName, last4) ?: return
        if (!account.isCreditCard) return

        val newBalance = (account.balance - cardSide.amount).max(BigDecimal.ZERO)
        accountBalanceRepository.insertBalance(
            account.copy(
                id = 0,
                balance = newBalance,
                timestamp = cardSide.dateTime,
                transactionId = cardSide.id,
                sourceType = "CC_BILL_PAYMENT",
                smsSource = null
            )
        )
        Log.d(TAG, "Reduced CC outstanding for $bankName **$last4 by ${cardSide.amount} -> $newBalance")
    }

    /**
     * One-shot historical pass: walks all unlinked CC bill payments (e.g. those
     * created before this feature shipped) and attempts to pair them. Cheap to
     * run on app startup once because the query is index-backed and the linker
     * short-circuits on ambiguous matches.
     */
    suspend fun backfillHistoricalLinks(): Int {
        val rows = transactionDao.getUnlinkedCcBillPayments()
        var linked = 0
        for (row in rows) {
            // Re-fetch in case a prior iteration set the link via the counterpart.
            val fresh = transactionDao.getTransactionById(row.id) ?: continue
            if (fresh.linkedTransactionId != null) continue
            if (linkIfApplicable(fresh) != null) linked++
        }
        if (linked > 0) Log.d(TAG, "Historical CC payment linker linked $linked pair(s)")
        return linked
    }
}
