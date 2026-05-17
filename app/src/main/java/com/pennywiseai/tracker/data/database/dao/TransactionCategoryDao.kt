package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.TransactionCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionCategoryDao {

    @Query("SELECT category_name FROM transaction_categories WHERE transaction_id = :transactionId ORDER BY created_at ASC")
    fun getCategoriesForTransaction(transactionId: Long): Flow<List<String>>

    @Query("SELECT category_name FROM transaction_categories WHERE transaction_id = :transactionId ORDER BY created_at ASC")
    suspend fun getCategoriesForTransactionSync(transactionId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCategory(entity: TransactionCategoryEntity)

    @Query("DELETE FROM transaction_categories WHERE transaction_id = :transactionId AND category_name = :categoryName")
    suspend fun removeCategory(transactionId: Long, categoryName: String)

    @Query("DELETE FROM transaction_categories WHERE transaction_id = :transactionId")
    suspend fun clearCategoriesForTransaction(transactionId: Long)

    @Query("SELECT * FROM transaction_categories WHERE transaction_id IN (:transactionIds) ORDER BY transaction_id ASC")
    suspend fun getCategoriesForTransactions(transactionIds: List<Long>): List<TransactionCategoryEntity>

    @Query("SELECT transaction_id FROM transaction_categories GROUP BY transaction_id HAVING COUNT(*) >= 2")
    suspend fun getTransactionIdsWithMultipleCategories(): List<Long>

    @Query("""
        SELECT tc.category_name FROM transaction_categories tc
        INNER JOIN transactions t ON t.id = tc.transaction_id
        WHERE t.is_deleted = 0
        AND t.merchant_name = :merchantName
        AND t.id != :excludeTransactionId
        GROUP BY tc.category_name
        ORDER BY COUNT(*) DESC
        LIMIT :limit
    """)
    suspend fun getTagCategoriesForMerchant(
        merchantName: String,
        excludeTransactionId: Long,
        limit: Int = 8
    ): List<String>
}
