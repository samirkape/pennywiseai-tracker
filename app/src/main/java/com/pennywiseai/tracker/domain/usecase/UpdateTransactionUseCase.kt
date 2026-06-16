package com.pennywiseai.tracker.domain.usecase

import android.net.Uri
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.time.LocalDateTime
import javax.inject.Inject

data class UpdateTransactionRequest(
    val original: TransactionEntity,
    val updated: TransactionEntity,
    val pendingReceiptUris: List<Uri>,
    val removedReceiptIds: List<Long>,
    val removedReceiptPaths: List<String>,
    val updateCategoryForMerchant: Boolean,
    val bulkCategoryNotBefore: LocalDateTime?,
    val bulkCategoryMerchantName: String,
    val bulkSyncMerchantName: Boolean,
    val bulkSyncTransactionType: Boolean,
    val showSplitEditor: Boolean,
    val hasOriginalSplits: Boolean,
    val splits: List<TransactionSplitEntity>,
)

data class UpdateTransactionResult(
    val bulkCategoryUndoCount: Int,
)

class UpdateTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val receiptManager: ReceiptManager,
    private val processAutoGoalContributions: ProcessAutoGoalContributionsUseCase,
) {
    suspend fun execute(request: UpdateTransactionRequest): UpdateTransactionResult {
        val original = request.original
        val updated = request.updated

        // Delete removed receipts from disk and DB
        for ((index, id) in request.removedReceiptIds.withIndex()) {
            val path = request.removedReceiptPaths.getOrNull(index)
            path?.let { receiptManager.deleteReceipt(it) }
            transactionRepository.deleteReceipt(id)
        }

        // Persist new receipts
        val newPaths = receiptManager.saveReceipts(request.pendingReceiptUris)
        if (newPaths.isNotEmpty()) {
            transactionRepository.insertReceipts(updated.id, newPaths)
        }

        // Persist the main transaction
        transactionRepository.updateTransaction(updated)

        // Keep subscription rows in sync with recurring flag / merchant / amount changes
        subscriptionRepository.syncRecurringWithSubscriptions(before = original, after = updated)

        // Fix account balance on both old and new account when the assignment changes
        val accountChanged = original.bankName != updated.bankName ||
            original.accountNumber != updated.accountNumber
        if (accountChanged) {
            revertOldAccountBalance(original, updated)
            applyNewAccountBalance(updated)
        }

        // Persist splits
        if (request.showSplitEditor && request.splits.isNotEmpty()) {
            transactionRepository.saveSplits(updated.id, request.splits)
        } else if (request.hasOriginalSplits && !request.showSplitEditor) {
            transactionRepository.removeSplits(updated.id)
        }

        var bulkCategoryUndoCount = 0
        val bulkMerchantKey = request.bulkCategoryMerchantName.trim()
            .ifEmpty { updated.merchantName.trim() }

        // Bulk merchant rename (rename all sibling transactions to the new display name)
        if (request.bulkSyncMerchantName &&
            bulkMerchantKey.isNotEmpty() &&
            !bulkMerchantKey.equals(updated.merchantName.trim(), ignoreCase = true)
        ) {
            transactionRepository.updateMerchantNameForMerchant(bulkMerchantKey, updated.merchantName)
        }

        // After bulk merchant rename, the effective key for subsequent bulk ops is the new name
        val merchantKeyForBulkFollowUps = if (request.bulkSyncMerchantName) {
            updated.merchantName.trim()
        } else {
            bulkMerchantKey
        }

        // Bulk category propagation — captures undo snapshot before writing
        if (request.updateCategoryForMerchant && merchantKeyForBulkFollowUps.isNotEmpty()) {
            val snapshot = transactionRepository.captureBulkCategoryUndoSnapshot(
                merchantKeyForBulkFollowUps,
                updated.id,
                request.bulkCategoryNotBefore,
            )
            transactionRepository.updateCategoryForMerchant(
                merchantKeyForBulkFollowUps,
                updated.category,
                request.bulkCategoryNotBefore,
            )
            if (snapshot.isNotEmpty()) {
                transactionRepository.rememberBulkCategoryUndo(snapshot)
                bulkCategoryUndoCount = snapshot.size
            }
        }

        // Bulk transaction type / transfer kind propagation
        if (request.bulkSyncTransactionType && merchantKeyForBulkFollowUps.isNotEmpty()) {
            transactionRepository.bulkUpdateTypeAndTransferKindForMerchant(
                merchantName = merchantKeyForBulkFollowUps,
                type = updated.transactionType,
                transferKind = updated.transferKind,
                excludeId = updated.id,
                notBefore = request.bulkCategoryNotBefore,
            )
        }

        // Revoke and re-evaluate auto goal contributions when category/type changes
        val categoryOrTypeChanged = original.category != updated.category ||
            original.transactionType != updated.transactionType
        if (categoryOrTypeChanged) {
            runCatching {
                processAutoGoalContributions.revokeContribution(updated.id)
                processAutoGoalContributions.execute(updated)
            }
        }

        return UpdateTransactionResult(bulkCategoryUndoCount = bulkCategoryUndoCount)
    }

    /**
     * Reverts the impact of the original transaction on the old account's balance.
     * Only called when the account assignment has actually changed.
     */
    private suspend fun revertOldAccountBalance(original: TransactionEntity, updated: TransactionEntity) {
        val oldBank = original.bankName ?: return
        val oldAccount = original.accountNumber ?: return
        if (oldBank == updated.bankName && oldAccount == updated.accountNumber) return
        val oldBalance = accountBalanceRepository.getLatestBalance(oldBank, oldAccount) ?: return
        val revert = when (original.transactionType) {
            TransactionType.INCOME -> -original.amount
            TransactionType.EXPENSE, TransactionType.CREDIT,
            TransactionType.TRANSFER, TransactionType.INVESTMENT -> original.amount
        }
        accountBalanceRepository.insertBalance(
            oldBalance.copy(
                id = 0,
                balance = oldBalance.balance + revert,
                timestamp = updated.dateTime,
                transactionId = updated.id,
                sourceType = "TRANSACTION",
                smsSource = null,
            )
        )
    }

    /**
     * Applies the impact of the updated transaction on the new account's balance.
     */
    private suspend fun applyNewAccountBalance(updated: TransactionEntity) {
        val bank = updated.bankName ?: return
        val account = updated.accountNumber ?: return
        val currentBalance = accountBalanceRepository.getLatestBalance(bank, account) ?: return
        val change = when (updated.transactionType) {
            TransactionType.INCOME -> updated.amount
            TransactionType.EXPENSE, TransactionType.CREDIT,
            TransactionType.TRANSFER, TransactionType.INVESTMENT -> -updated.amount
        }
        accountBalanceRepository.insertBalance(
            currentBalance.copy(
                id = 0,
                balance = currentBalance.balance + change,
                timestamp = updated.dateTime,
                transactionId = updated.id,
                sourceType = "TRANSACTION",
                smsSource = null,
            )
        )
    }
}
