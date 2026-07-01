package com.spendly.tracker.data.database.dao

import androidx.room.*
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY date_time DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """)
    fun getTransactionsBetweenDates(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    /**
     * Optimized query that filters transactions at the database level.
     * Combines date range, currency, and transaction type filters to reduce memory usage.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @param currency Currency code to filter by (e.g., "INR", "USD")
     * @param transactionType Optional transaction type filter (null means all types)
     * @return Flow of filtered transactions ordered by date descending
     */
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
        AND date_time BETWEEN :startDate AND :endDate
        AND currency = :currency
        AND (:transactionType IS NULL OR transaction_type = :transactionType)
        ORDER BY date_time DESC
    """)
    fun getTransactionsFiltered(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        currency: String,
        transactionType: TransactionType?
    ): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND transaction_type = :type 
        ORDER BY date_time DESC
    """)
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND category = :category 
        ORDER BY date_time DESC
    """)
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND (merchant_name LIKE '%' || :searchQuery || '%' 
        OR description LIKE '%' || :searchQuery || '%'
        OR sms_body LIKE '%' || :searchQuery || '%'
        OR tags LIKE '%' || :searchQuery || '%'
        OR category LIKE '%' || :searchQuery || '%') 
        ORDER BY date_time DESC
    """)
    fun searchTransactions(searchQuery: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT DISTINCT category FROM transactions WHERE is_deleted = 0 ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
    """)
    fun getTrackedTransactionCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
        AND (category IS NULL OR TRIM(category) = '' OR category = 'Others')
    """)
    fun getUncategorizedTransactionCount(): Flow<Int>

    @Query("""
        SELECT DISTINCT category FROM transactions
        WHERE is_deleted = 0
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY category ASC
    """)
    fun getCategoriesUsedBetweenDates(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>>

    @Query("""
        SELECT category FROM transactions
        WHERE is_deleted = 0
        GROUP BY category
        ORDER BY COUNT(*) DESC
        LIMIT :limit
    """)
    suspend fun getTopCategoriesByUsage(limit: Int = 3): List<String>

    @Query("SELECT DISTINCT merchant_name FROM transactions WHERE is_deleted = 0 ORDER BY merchant_name ASC")
    fun getAllMerchants(): Flow<List<String>>

    @Query("SELECT DISTINCT merchant_name FROM transactions WHERE is_deleted = 0 ORDER BY merchant_name ASC")
    suspend fun getDistinctMerchantNames(): List<String>

    /**
     * Prunes the merchant universe for rename review: only names appearing on at least one
     * non-deleted, tracked row other than [excludeId], and matching any of up to six LIKE tokens.
     * Tokens should be short substrings derived from the old/new labels (see repository).
     */
    @Query(
        """
        SELECT DISTINCT merchant_name FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
        AND id != :excludeId
        AND TRIM(merchant_name) != ''
        AND LOWER(TRIM(merchant_name)) != LOWER(TRIM(:newName))
        AND (
            (LENGTH(:t0) > 0 AND LOWER(merchant_name) LIKE '%' || LOWER(:t0) || '%') OR
            (LENGTH(:t1) > 0 AND LOWER(merchant_name) LIKE '%' || LOWER(:t1) || '%') OR
            (LENGTH(:t2) > 0 AND LOWER(merchant_name) LIKE '%' || LOWER(:t2) || '%') OR
            (LENGTH(:t3) > 0 AND LOWER(merchant_name) LIKE '%' || LOWER(:t3) || '%') OR
            (LENGTH(:t4) > 0 AND LOWER(merchant_name) LIKE '%' || LOWER(:t4) || '%') OR
            (LENGTH(:t5) > 0 AND LOWER(merchant_name) LIKE '%' || LOWER(:t5) || '%')
        )
        LIMIT :resultLimit
        """,
    )
    suspend fun getDistinctMerchantNamesForRenameCandidates(
        excludeId: Long,
        newName: String,
        t0: String,
        t1: String,
        t2: String,
        t3: String,
        t4: String,
        t5: String,
        resultLimit: Int,
    ): List<String>

    @Query("SELECT tags FROM transactions WHERE is_deleted = 0 AND tags != '' ORDER BY updated_at DESC")
    suspend fun getDistinctTagStrings(): List<String>

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
        AND transaction_type = :type
        AND date_time BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalAmountByTypeAndPeriod(
        type: TransactionType,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionForRestore(transaction: TransactionEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
    
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query(
        """
        UPDATE transactions SET description = :description, updated_at = :updatedAt
        WHERE id = :id
        AND is_deleted = 0
        AND (description IS NULL OR TRIM(description) = '')
        """
    )
    suspend fun updateTransactionDescriptionIfEmpty(
        id: Long,
        description: String,
        updatedAt: LocalDateTime,
    ): Int

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
    
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)
    
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("UPDATE transactions SET linked_transaction_id = NULL")
    suspend fun clearAllLinkedTransactionIds()
    
    @Query(
        """
        UPDATE transactions SET category = :newCategory, updated_at = :updatedAt
        WHERE is_deleted = 0
        AND LOWER(merchant_name) = LOWER(:merchantName)
        AND ((:applySince = 0) OR (date_time >= :sinceCutoff))
        """
    )
    suspend fun updateCategoryForMerchant(
        merchantName: String,
        newCategory: String,
        updatedAt: LocalDateTime,
        applySince: Int,
        sinceCutoff: LocalDateTime,
    )

    @Query(
        """
        UPDATE transactions SET
            transaction_type = :transactionType,
            transfer_kind = :transferKind,
            updated_at = :updatedAt
        WHERE is_deleted = 0
        AND LOWER(merchant_name) = LOWER(:merchantName)
        AND id != :excludeId
        AND ((:applySince = 0) OR (date_time >= :sinceCutoff))
        """
    )
    suspend fun bulkUpdateTypeAndTransferKindForMerchant(
        merchantName: String,
        transactionType: TransactionType,
        transferKind: String?,
        excludeId: Long,
        updatedAt: LocalDateTime,
        applySince: Int,
        sinceCutoff: LocalDateTime,
    ): Int

    @Query(
        """
        UPDATE transactions SET merchant_name = :newMerchantName, updated_at = :updatedAt
        WHERE is_deleted = 0 AND LOWER(merchant_name) = LOWER(:oldMerchantName)
        """
    )
    suspend fun updateMerchantNameForMerchant(
        oldMerchantName: String,
        newMerchantName: String,
        updatedAt: LocalDateTime,
    )

    @Query("SELECT COUNT(*) FROM transactions WHERE merchant_name = :merchantName AND id != :excludeId")
    suspend fun getTransactionCountForMerchant(merchantName: String, excludeId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE is_deleted = 0
        AND LOWER(merchant_name) = LOWER(:merchantName)
        AND id != :excludeId
        AND ((:applySince = 0) OR (date_time >= :sinceCutoff))
        """
    )
    suspend fun getActiveTransactionCountForMerchant(
        merchantName: String,
        excludeId: Long,
        applySince: Int,
        sinceCutoff: LocalDateTime,
    ): Int

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0 AND merchant_name = :merchantName
        ORDER BY date_time DESC
        LIMIT 1
    """)
    suspend fun getLatestTransactionForMerchant(merchantName: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND merchant_name = :merchantName
        AND id != :excludeId
        ORDER BY date_time DESC
    """)
    suspend fun getActiveTransactionsForMerchant(merchantName: String, excludeId: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET merchant_name = :newMerchantName, updated_at = :updatedAt WHERE id = :transactionId")
    suspend fun updateMerchantNameById(
        transactionId: Long,
        newMerchantName: String,
        updatedAt: LocalDateTime,
    )

    @Query("""
        SELECT category FROM transactions
        WHERE is_deleted = 0
        AND LOWER(merchant_name) = LOWER(:merchantName)
        AND id != :excludeTransactionId
        GROUP BY category
        ORDER BY COUNT(*) DESC, MAX(date_time) DESC
        LIMIT :limit
    """)
    suspend fun getCategoriesForMerchant(
        merchantName: String,
        excludeTransactionId: Long,
        limit: Int = 8
    ): List<String>

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 ORDER BY currency")
    fun getAllCurrencies(): Flow<List<String>>

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 AND date_time BETWEEN :startDate AND :endDate ORDER BY currency")
    fun getCurrenciesForPeriod(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>>

    @Query("UPDATE transactions SET is_excluded_from_tracking = :excluded, updated_at = :now WHERE id = :transactionId")
    suspend fun updateExcludedFromTracking(transactionId: Long, excluded: Boolean, now: LocalDateTime = LocalDateTime.now())

    // Soft delete methods - preserve hash so the deduplication check in the SMS worker
    // can still find and skip this transaction on future scans
    @Query("UPDATE transactions SET is_deleted = 1 WHERE id = :transactionId")
    suspend fun softDeleteTransaction(transactionId: Long)

    @Query("UPDATE transactions SET is_deleted = 1 WHERE transaction_hash = :transactionHash")
    suspend fun softDeleteByHash(transactionHash: String)

    /**
     * Returns hashes of all soft-deleted transactions.
     * Used during MERGE restore to prevent resurrecting transactions the user
     * intentionally deleted after the backup was made.
     */
    @Query("SELECT transaction_hash FROM transactions WHERE is_deleted = 1")
    suspend fun getSoftDeletedHashes(): List<String>

    // Method to check if transaction exists by hash (including deleted)
    @Query("SELECT * FROM transactions WHERE transaction_hash = :transactionHash LIMIT 1")
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity?
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND is_excluded_from_tracking = 0
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """)
    suspend fun getTransactionsBetweenDatesList(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity>
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND bank_name = :bankName
        AND (account_number = :accountLast4 OR account_number IS NULL)
        ORDER BY date_time DESC
    """)
    fun getTransactionsByAccount(
        bankName: String,
        accountLast4: String
    ): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND bank_name = :bankName
        AND account_number = :accountLast4
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """)
    fun getTransactionsByAccountAndDateRange(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE reference = :reference AND is_deleted = 0 LIMIT 1")
    suspend fun getTransactionByReference(reference: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND amount = :amount
        AND date_time BETWEEN :dateStart AND :dateEnd
    """)
    suspend fun getTransactionByAmountAndDate(
        amount: BigDecimal,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity>

    /**
     * Find candidate counterpart rows for credit-card-bill-payment pairing.
     *
     * Matches by amount + currency within a date window, excluding the caller's
     * own row, soft-deleted rows, rows already linked, and the same transfer-kind
     * direction (so a bill-payment debit leg only matches an unlinked credit leg
     * and vice versa). Used by `CreditCardPaymentLinker`.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND id != :excludeId
        AND amount = :amount
        AND currency = :currency
        AND date_time BETWEEN :dateStart AND :dateEnd
        AND linked_transaction_id IS NULL
        AND transaction_type IN ('TRANSFER', 'CREDIT', 'INCOME', 'EXPENSE')
        ORDER BY date_time ASC
    """)
    suspend fun findLinkCandidates(
        excludeId: Long,
        amount: BigDecimal,
        currency: String,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity>

    /**
     * Atomically sets the linked_transaction_id pointer for a single row.
     */
    @Query("""
        UPDATE transactions
        SET linked_transaction_id = :linkedId,
            transfer_kind = COALESCE(:transferKind, transfer_kind),
            updated_at = :now
        WHERE id = :transactionId
    """)
    suspend fun setLinkedTransaction(
        transactionId: Long,
        linkedId: Long?,
        transferKind: String?,
        now: LocalDateTime = LocalDateTime.now()
    )

    /**
     * Re-classifies a row as a transfer of the given kind. Used by the linker
     * when it pairs a CC-side row that the parser originally tagged INCOME/CREDIT.
     */
    @Query("""
        UPDATE transactions
        SET transaction_type = :type,
            transfer_kind = :transferKind,
            category = CASE WHEN :forceCategory IS NULL THEN category ELSE :forceCategory END,
            updated_at = :now
        WHERE id = :transactionId
    """)
    suspend fun reclassifyAsTransfer(
        transactionId: Long,
        type: TransactionType,
        transferKind: String?,
        forceCategory: String?,
        now: LocalDateTime = LocalDateTime.now()
    )

    /**
     * Clears `linked_transaction_id` on any row that points to the given id.
     * Called when a linked transaction is deleted, to avoid dangling pointers.
     */
    @Query("""
        UPDATE transactions
        SET linked_transaction_id = NULL, updated_at = :now
        WHERE linked_transaction_id = :transactionId
    """)
    suspend fun clearLinksTo(
        transactionId: Long,
        now: LocalDateTime = LocalDateTime.now()
    )

    /**
     * Unlinked CC bill payment legs, oldest first. Used by the one-shot
     * historical linker pass to backfill `linked_transaction_id` on rows that
     * existed before the linking logic shipped.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
          AND linked_transaction_id IS NULL
          AND transfer_kind = 'CC_BILL_PAYMENT'
        ORDER BY date_time ASC
    """)
    suspend fun getUnlinkedCcBillPayments(): List<TransactionEntity>

    /**
     * Clears recurring on transactions that matched a deleted manual subscription
     * (same merchant, amount, currency; excludes transfers).
     */
    @Query(
        """
        UPDATE transactions
        SET is_recurring = 0, updated_at = :updatedAt
        WHERE is_deleted = 0
          AND is_recurring = 1
          AND merchant_name = :merchantName
          AND amount = :amount
          AND currency = :currency
          AND transaction_type != :transferType
        """
    )
    suspend fun clearRecurringForMerchantAmountMatching(
        merchantName: String,
        amount: BigDecimal,
        currency: String,
        transferType: TransactionType,
        updatedAt: LocalDateTime,
    ): Int

    @Query(
        """
        SELECT id, category FROM transactions
        WHERE is_deleted = 0
        AND LOWER(merchant_name) = LOWER(:merchantName)
        AND id != :excludeId
        AND ((:applySince = 0) OR (date_time >= :sinceCutoff))
        """
    )
    suspend fun getIdCategoryPairsForBulkCategoryUpdate(
        merchantName: String,
        excludeId: Long,
        applySince: Int,
        sinceCutoff: LocalDateTime,
    ): List<TransactionIdCategoryRow>

    @Query(
        """
        SELECT id, merchant_name, category, amount, currency, date_time FROM transactions
        WHERE is_deleted = 0
        AND LOWER(merchant_name) = LOWER(:merchantName)
        AND id != :excludeId
        AND ((:applySince = 0) OR (date_time >= :sinceCutoff))
        ORDER BY date_time DESC
        LIMIT :limit
        """
    )
    suspend fun getBulkCategoryPreviewRows(
        merchantName: String,
        excludeId: Long,
        applySince: Int,
        sinceCutoff: LocalDateTime,
        limit: Int,
    ): List<BulkCategoryPreviewDaoRow>

    @Query(
        """
        UPDATE transactions SET category = :category, updated_at = :updatedAt
        WHERE id = :id AND is_deleted = 0
        """
    )
    suspend fun updateTransactionCategoryById(
        id: Long,
        category: String,
        updatedAt: LocalDateTime,
    ): Int

    @Query(
        """
        UPDATE transactions SET category = :newCategory, updated_at = :updatedAt
        WHERE category = :oldCategory AND is_deleted = 0
        """
    )
    suspend fun updateAllTransactionsByCategory(
        oldCategory: String,
        newCategory: String,
        updatedAt: LocalDateTime,
    )

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND transaction_type = 'TRANSFER'
        AND transfer_kind = 'SELF_TRANSFER_PENDING'
        ORDER BY date_time DESC
    """)
    fun getPendingSelfTransfers(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE is_deleted = 0
        AND transaction_type = 'TRANSFER'
        AND transfer_kind = 'SELF_TRANSFER_PENDING'
    """)
    fun getPendingSelfTransferCount(): Flow<Int>

    @Query("""
        UPDATE transactions SET transfer_kind = :transferKind, updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateTransferKind(id: Long, transferKind: String, updatedAt: LocalDateTime): Int
}

data class TransactionIdCategoryRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "category") val category: String,
)

data class BulkCategoryPreviewDaoRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "amount") val amount: BigDecimal,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "date_time") val dateTime: LocalDateTime,
)
