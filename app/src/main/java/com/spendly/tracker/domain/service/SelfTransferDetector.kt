package com.spendly.tracker.domain.service

import android.util.Log
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import com.spendly.tracker.data.repository.AccountBalanceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects whether a TRANSFER transaction is a self-transfer (between the user's own accounts)
 * based on known accounts inferred from AccountBalanceEntity records.
 *
 * Classification logic:
 * - [TransferKind.SELF_TRANSFER]: toAccount last-4 matches a known user account (high confidence).
 * - [TransferKind.OTHERS_TRANSFER]: toAccount is present but does NOT match any known account.
 * - [TransferKind.SELF_TRANSFER_PENDING]: toAccount is absent — ambiguous, needs user review.
 * - null: no reclassification needed (non-TRANSFER, or already classified as CC_BILL_PAYMENT).
 */
@Singleton
class SelfTransferDetector @Inject constructor(
    private val accountBalanceRepository: AccountBalanceRepository
) {
    companion object {
        private const val TAG = "SelfTransferDetector"
    }

    /**
     * Returns the suggested [TransferKind] for [entity], or null if no change is needed.
     * Only acts on TRANSFER transactions that have not already been classified as
     * CC_BILL_PAYMENT, SELF_TRANSFER, or OTHERS_TRANSFER.
     */
    suspend fun classify(entity: TransactionEntity): String? {
        if (entity.transactionType != TransactionType.TRANSFER) return null
        if (entity.transferKind == TransferKind.CC_BILL_PAYMENT ||
            entity.transferKind == TransferKind.SELF_TRANSFER ||
            entity.transferKind == TransferKind.OTHERS_TRANSFER
        ) return null

        val toAccount = entity.toAccount?.trim()?.takeLast(4)

        if (toAccount.isNullOrEmpty()) {
            // No destination account info — mark pending for user review
            Log.d(TAG, "Transfer id=${entity.id} has no toAccount — marking SELF_TRANSFER_PENDING")
            return TransferKind.SELF_TRANSFER_PENDING
        }

        val knownAccounts = accountBalanceRepository.getAllKnownAccountLast4s().toSet()
        return if (toAccount in knownAccounts) {
            Log.d(TAG, "Transfer id=${entity.id} toAccount=$toAccount matches known account — SELF_TRANSFER")
            TransferKind.SELF_TRANSFER
        } else {
            Log.d(TAG, "Transfer id=${entity.id} toAccount=$toAccount not in known accounts — OTHERS_TRANSFER")
            TransferKind.OTHERS_TRANSFER
        }
    }
}
