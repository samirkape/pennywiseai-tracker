package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendly.tracker.data.database.entity.TransactionReceiptEntity

@Dao
interface TransactionReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: TransactionReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(receipts: List<TransactionReceiptEntity>)

    @Query("SELECT * FROM transaction_receipts WHERE transaction_id = :transactionId ORDER BY created_at ASC")
    suspend fun getReceiptsForTransaction(transactionId: Long): List<TransactionReceiptEntity>

    @Query("SELECT * FROM transaction_receipts")
    suspend fun getAllReceipts(): List<TransactionReceiptEntity>

    @Query("DELETE FROM transaction_receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long)

    @Query("DELETE FROM transaction_receipts WHERE transaction_id = :transactionId")
    suspend fun deleteReceiptsForTransaction(transactionId: Long)

    @Query("DELETE FROM transaction_receipts")
    suspend fun deleteAllReceipts()
}
