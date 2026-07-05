package com.spendly.tracker.worker

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendly.tracker.data.database.entity.BudgetImpactType
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionSplitEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.receipt.ReceiptManager
import com.spendly.tracker.data.repository.AccountBalanceRepository
import com.spendly.tracker.data.repository.SubscriptionRepository
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDateTime

@HiltWorker
class SaveTransactionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val receiptManager: ReceiptManager,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "SaveTransactionWorker"

        private const val KEY_PATCH_JSON = "patch_json"
        private const val KEY_ORIGINAL_BANK = "original_bank"
        private const val KEY_ORIGINAL_ACCOUNT = "original_account"
        private const val KEY_PENDING_RECEIPT_URIS = "pending_receipt_uris"
        private const val KEY_REMOVED_RECEIPT_IDS = "removed_receipt_ids"
        private const val KEY_REMOVED_RECEIPT_PATHS = "removed_receipt_paths"
        private const val KEY_UPDATE_CATEGORY_FOR_MERCHANT = "update_category_for_merchant"
        private const val KEY_BULK_CATEGORY_NOT_BEFORE_ISO = "bulk_category_not_before_iso"
        private const val KEY_BULK_CATEGORY_MERCHANT_NAME = "bulk_category_merchant_name"
        private const val KEY_BULK_SYNC_MERCHANT_NAME = "bulk_sync_merchant_name"
        private const val KEY_BULK_SYNC_TRANSACTION_TYPE = "bulk_sync_transaction_type"
        private const val KEY_SHOW_SPLIT_EDITOR = "show_split_editor"
        private const val KEY_HAS_ORIGINAL_SPLITS = "has_original_splits"
        private const val KEY_SPLITS_JSON = "splits_json"

        const val OUTPUT_ERROR = "save_error"
        /** Number of other transactions bulk-updated (undo restores these rows). */
        const val OUTPUT_BULK_CATEGORY_UNDO_COUNT = "output_bulk_category_undo_count"

        private val lenientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun buildInputData(
            patch: TransactionPatch,
            originalBank: String?,
            originalAccount: String?,
            pendingReceiptUris: List<Uri>,
            removedReceiptIds: List<Long>,
            removedReceiptPaths: List<String>,
            updateCategoryForMerchant: Boolean,
            bulkCategoryNotBeforeIso: String,
            bulkCategoryMerchantName: String = "",
            bulkSyncMerchantName: Boolean = false,
            bulkSyncTransactionType: Boolean = false,
            showSplitEditor: Boolean,
            hasOriginalSplits: Boolean,
            splits: List<SplitPatch>,
        ): Data = workDataOf(
            KEY_PATCH_JSON to lenientJson.encodeToString(patch),
            KEY_ORIGINAL_BANK to originalBank,
            KEY_ORIGINAL_ACCOUNT to originalAccount,
            KEY_PENDING_RECEIPT_URIS to pendingReceiptUris.map { it.toString() }.toTypedArray(),
            KEY_REMOVED_RECEIPT_IDS to removedReceiptIds.toLongArray(),
            KEY_REMOVED_RECEIPT_PATHS to removedReceiptPaths.toTypedArray(),
            KEY_UPDATE_CATEGORY_FOR_MERCHANT to updateCategoryForMerchant,
            KEY_BULK_CATEGORY_NOT_BEFORE_ISO to bulkCategoryNotBeforeIso,
            KEY_BULK_CATEGORY_MERCHANT_NAME to bulkCategoryMerchantName,
            KEY_BULK_SYNC_MERCHANT_NAME to bulkSyncMerchantName,
            KEY_BULK_SYNC_TRANSACTION_TYPE to bulkSyncTransactionType,
            KEY_SHOW_SPLIT_EDITOR to showSplitEditor,
            KEY_HAS_ORIGINAL_SPLITS to hasOriginalSplits,
            KEY_SPLITS_JSON to lenientJson.encodeToString(splits),
        )

        fun buildRequest(inputData: Data): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SaveTransactionWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val patchJson = inputData.getString(KEY_PATCH_JSON)
                ?: return@withContext Result.failure(workDataOf(OUTPUT_ERROR to "Missing patch data"))
            val patch = lenientJson.decodeFromString<TransactionPatch>(patchJson)

            val originalBank = inputData.getString(KEY_ORIGINAL_BANK)
            val originalAccount = inputData.getString(KEY_ORIGINAL_ACCOUNT)
            val pendingReceiptUriStrings = inputData.getStringArray(KEY_PENDING_RECEIPT_URIS) ?: emptyArray()
            val removedReceiptIds = inputData.getLongArray(KEY_REMOVED_RECEIPT_IDS) ?: LongArray(0)
            val removedReceiptPaths = inputData.getStringArray(KEY_REMOVED_RECEIPT_PATHS) ?: emptyArray()
            val updateCategoryForMerchant = inputData.getBoolean(KEY_UPDATE_CATEGORY_FOR_MERCHANT, false)
            val bulkCategoryNotBeforeIso = inputData.getString(KEY_BULK_CATEGORY_NOT_BEFORE_ISO).orEmpty()
            val bulkCategoryNotBefore: LocalDateTime? =
                bulkCategoryNotBeforeIso.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }
            val bulkCategoryMerchantName = inputData.getString(KEY_BULK_CATEGORY_MERCHANT_NAME).orEmpty()
            val bulkSyncMerchantName = inputData.getBoolean(KEY_BULK_SYNC_MERCHANT_NAME, false)
            val bulkSyncTransactionType = inputData.getBoolean(KEY_BULK_SYNC_TRANSACTION_TYPE, false)
            val showSplitEditor = inputData.getBoolean(KEY_SHOW_SPLIT_EDITOR, false)
            val hasOriginalSplits = inputData.getBoolean(KEY_HAS_ORIGINAL_SPLITS, false)
            val splitsJson = inputData.getString(KEY_SPLITS_JSON) ?: "[]"
            val splits = lenientJson.decodeFromString<List<SplitPatch>>(splitsJson)

            // Delete removed receipts
            for ((index, id) in removedReceiptIds.withIndex()) {
                val path = removedReceiptPaths.getOrNull(index)
                path?.let { receiptManager.deleteReceipt(it) }
                transactionRepository.deleteReceipt(id)
            }

            // Save new pending receipts
            val newPaths = receiptManager.saveReceipts(
                pendingReceiptUriStrings.map { it.toUri() }
            )
            if (newPaths.isNotEmpty()) {
                transactionRepository.insertReceipts(patch.id, newPaths)
            }

            // Reconstruct and update transaction
            val existing = transactionRepository.getTransactionById(patch.id)
                ?: return@withContext Result.failure(workDataOf(OUTPUT_ERROR to "Transaction not found: ${patch.id}"))

            val updated = existing.copy(
                amount = BigDecimal(patch.amount),
                merchantName = patch.merchantName,
                category = patch.category,
                transactionType = TransactionType.valueOf(patch.transactionType),
                dateTime = LocalDateTime.parse(patch.dateTime),
                description = patch.description,
                accountNumber = patch.accountNumber,
                fromAccount = patch.fromAccount,
                toAccount = patch.toAccount,
                bankName = patch.bankName,
                currency = patch.currency,
                tags = patch.tags,
                isRecurring = patch.isRecurring,
                isExcludedFromTracking = patch.isExcludedFromTracking,
                profileId = patch.profileId,
                transferKind = patch.transferKind,
                budgetImpactType = patch.budgetImpactType?.let { BudgetImpactType.valueOf(it) },
                budgetCategory = patch.budgetCategory,
            )

            transactionRepository.updateTransaction(updated)

            subscriptionRepository.syncRecurringWithSubscriptions(
                before = existing,
                after = updated,
            )

            // Update account balance if account was changed
            val accountChanged = originalBank != updated.bankName || originalAccount != updated.accountNumber
            if (accountChanged && updated.bankName != null && updated.accountNumber != null) {
                val currentBalance = accountBalanceRepository.getLatestBalance(updated.bankName, updated.accountNumber)
                if (currentBalance != null) {
                    val balanceChange = when (updated.transactionType) {
                        TransactionType.INCOME -> updated.amount
                        TransactionType.EXPENSE, TransactionType.CREDIT -> -updated.amount
                        TransactionType.TRANSFER -> -updated.amount
                        TransactionType.INVESTMENT -> -updated.amount
                    }
                    accountBalanceRepository.insertBalance(
                        currentBalance.copy(
                            id = 0,
                            balance = currentBalance.balance + balanceChange,
                            timestamp = updated.dateTime,
                            transactionId = updated.id,
                            sourceType = "TRANSACTION",
                            smsSource = null
                        )
                    )
                }
            }

            // Save or remove splits
            if (showSplitEditor && splits.isNotEmpty()) {
                val splitEntities = splits.map { s ->
                    TransactionSplitEntity(
                        id = s.id,
                        transactionId = updated.id,
                        category = s.category,
                        amount = BigDecimal(s.amount),
                        tags = s.tags
                    )
                }
                transactionRepository.saveSplits(updated.id, splitEntities)
            } else if (hasOriginalSplits && !showSplitEditor) {
                transactionRepository.removeSplits(updated.id)
            }

            var bulkCategoryUndoCount = 0
            val bulkMerchantKey = bulkCategoryMerchantName.trim().ifEmpty { updated.merchantName.trim() }

            if (bulkSyncMerchantName && bulkMerchantKey.isNotEmpty() &&
                !bulkMerchantKey.equals(updated.merchantName.trim(), ignoreCase = true)
            ) {
                transactionRepository.updateMerchantNameForMerchant(
                    bulkMerchantKey,
                    updated.merchantName,
                )
            }

            val merchantKeyForBulkFollowUps = if (bulkSyncMerchantName) {
                updated.merchantName.trim()
            } else {
                bulkMerchantKey
            }

            if (updateCategoryForMerchant && merchantKeyForBulkFollowUps.isNotEmpty()) {
                val snapshot = transactionRepository.captureBulkCategoryUndoSnapshot(
                    merchantKeyForBulkFollowUps,
                    updated.id,
                    bulkCategoryNotBefore,
                )
                transactionRepository.updateCategoryForMerchant(
                    merchantKeyForBulkFollowUps,
                    updated.category,
                    bulkCategoryNotBefore,
                )
                if (snapshot.isNotEmpty()) {
                    transactionRepository.rememberBulkCategoryUndo(snapshot)
                    bulkCategoryUndoCount = snapshot.size
                }
            }

            if (bulkSyncTransactionType && merchantKeyForBulkFollowUps.isNotEmpty()) {
                transactionRepository.bulkUpdateTypeAndTransferKindForMerchant(
                    merchantName = merchantKeyForBulkFollowUps,
                    type = updated.transactionType,
                    transferKind = updated.transferKind,
                    excludeId = updated.id,
                    notBefore = bulkCategoryNotBefore,
                )
            }

            Result.success(
                workDataOf(OUTPUT_BULK_CATEGORY_UNDO_COUNT to bulkCategoryUndoCount),
            )
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(workDataOf(OUTPUT_ERROR to (e.message ?: "Unknown error")))
            }
        }
    }

    @Serializable
    data class TransactionPatch(
        val id: Long,
        val amount: String,
        val merchantName: String,
        val category: String,
        val transactionType: String,
        val dateTime: String,
        val description: String?,
        val accountNumber: String?,
        val fromAccount: String?,
        val toAccount: String?,
        val bankName: String?,
        val currency: String,
        val tags: String,
        val isRecurring: Boolean,
        val isExcludedFromTracking: Boolean,
        val profileId: Long?,
        val transferKind: String?,
        val budgetImpactType: String?,
        val budgetCategory: String?,
    )

    @Serializable
    data class SplitPatch(
        val id: Long,
        val category: String,
        val amount: String,
        val tags: String,
    )
}
