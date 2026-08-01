package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendly.tracker.data.database.entity.PrepaidExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface PrepaidExpenseDao {

    @Query("SELECT * FROM prepaid_expenses WHERE status = 'ACTIVE' ORDER BY start_date DESC")
    fun getActivePrepaidExpenses(): Flow<List<PrepaidExpenseEntity>>

    @Query("SELECT * FROM prepaid_expenses ORDER BY created_at DESC")
    fun getAllPrepaidExpenses(): Flow<List<PrepaidExpenseEntity>>

    @Query("SELECT * FROM prepaid_expenses WHERE id = :id")
    suspend fun getById(id: Long): PrepaidExpenseEntity?

    @Query("SELECT * FROM prepaid_expenses WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<PrepaidExpenseEntity?>

    @Query("SELECT * FROM prepaid_expenses WHERE source_transaction_id = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: Long): PrepaidExpenseEntity?

    /** Active plans ending within the given date window — powers upcoming-renewal surfacing. */
    @Query("""
        SELECT * FROM prepaid_expenses
        WHERE status = 'ACTIVE' AND end_date BETWEEN :fromDate AND :untilDate
        ORDER BY end_date ASC
    """)
    suspend fun getUpcomingRenewals(fromDate: LocalDate, untilDate: LocalDate): List<PrepaidExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PrepaidExpenseEntity): Long

    @Update
    suspend fun update(entity: PrepaidExpenseEntity)

    @Query("UPDATE prepaid_expenses SET status = :status, cancelled_at = :cancelledAt, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, cancelledAt: LocalDateTime?, now: LocalDateTime)

    @Delete
    suspend fun delete(entity: PrepaidExpenseEntity)

    @Query("DELETE FROM prepaid_expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM prepaid_expenses")
    suspend fun deleteAll()
}
