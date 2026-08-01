package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendly.tracker.data.database.entity.PrepaidAllocationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** An allocation row paired with its parent plan's currency, for multi-currency rollups. */
data class PrepaidAllocationWithCurrency(
    @Embedded val allocation: PrepaidAllocationEntity,
    val currency: String
)

@Dao
interface PrepaidAllocationDao {

    @Query("SELECT * FROM prepaid_allocations WHERE prepaid_expense_id = :prepaidExpenseId ORDER BY period_year_month ASC")
    fun getAllocationsForPlan(prepaidExpenseId: Long): Flow<List<PrepaidAllocationEntity>>

    @Query("SELECT * FROM prepaid_allocations WHERE prepaid_expense_id = :prepaidExpenseId ORDER BY period_year_month ASC")
    suspend fun getAllocationsForPlanSync(prepaidExpenseId: Long): List<PrepaidAllocationEntity>

    /**
     * The join point for analytics/budgets: all non-reversed allocation rows whose
     * month falls within [startMonth, endMonth] inclusive (YearMonth strings, e.g. "2026-01").
     * Callers convert their date range to a YearMonth range first. Monthly and yearly
     * consumers use this exact same query with a wider/narrower range.
     */
    @Query("""
        SELECT a.* FROM prepaid_allocations a
        INNER JOIN prepaid_expenses p ON a.prepaid_expense_id = p.id
        WHERE a.status != 'REVERSED' AND p.status != 'CANCELLED'
        AND a.period_year_month BETWEEN :startMonth AND :endMonth
    """)
    fun getAllocationsBetweenMonths(startMonth: String, endMonth: String): Flow<List<PrepaidAllocationEntity>>

    @Query("""
        SELECT a.* FROM prepaid_allocations a
        INNER JOIN prepaid_expenses p ON a.prepaid_expense_id = p.id
        WHERE a.status != 'REVERSED' AND p.status != 'CANCELLED'
        AND a.period_year_month BETWEEN :startMonth AND :endMonth
    """)
    suspend fun getAllocationsBetweenMonthsSync(startMonth: String, endMonth: String): List<PrepaidAllocationEntity>

    /** Joined with parent for currency filtering, used by unified-currency analytics/budget aggregation. */
    @Query("""
        SELECT a.* FROM prepaid_allocations a
        INNER JOIN prepaid_expenses p ON a.prepaid_expense_id = p.id
        WHERE a.status != 'REVERSED' AND p.status != 'CANCELLED'
        AND a.period_year_month BETWEEN :startMonth AND :endMonth
        AND p.currency = :currency
    """)
    fun getAllocationsBetweenMonthsForCurrency(startMonth: String, endMonth: String, currency: String): Flow<List<PrepaidAllocationEntity>>

    /** Same join as [getAllocationsBetweenMonths] but also returns each row's currency, for multi-currency rollups (e.g. Home). */
    @Query("""
        SELECT a.*, p.currency AS currency FROM prepaid_allocations a
        INNER JOIN prepaid_expenses p ON a.prepaid_expense_id = p.id
        WHERE a.status != 'REVERSED' AND p.status != 'CANCELLED'
        AND a.period_year_month BETWEEN :startMonth AND :endMonth
    """)
    fun getAllocationsWithCurrencyBetweenMonths(startMonth: String, endMonth: String): Flow<List<PrepaidAllocationWithCurrency>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(allocations: List<PrepaidAllocationEntity>): List<Long>

    @Update
    suspend fun update(allocation: PrepaidAllocationEntity)

    @Query("UPDATE prepaid_allocations SET status = 'REVERSED', updated_at = :now WHERE prepaid_expense_id = :prepaidExpenseId AND period_year_month > :afterMonth")
    suspend fun reverseFutureAllocations(prepaidExpenseId: Long, afterMonth: String, now: LocalDateTime)

    @Query("DELETE FROM prepaid_allocations WHERE prepaid_expense_id = :prepaidExpenseId AND period_year_month >= :fromMonth")
    suspend fun deleteAllocationsFrom(prepaidExpenseId: Long, fromMonth: String)

    @Query("DELETE FROM prepaid_allocations WHERE prepaid_expense_id = :prepaidExpenseId")
    suspend fun deleteAllForPlan(prepaidExpenseId: Long)

    @Query("DELETE FROM prepaid_allocations")
    suspend fun deleteAll()
}
