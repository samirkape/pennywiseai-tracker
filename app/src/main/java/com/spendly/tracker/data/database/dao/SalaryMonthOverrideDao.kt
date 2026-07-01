package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendly.tracker.data.database.entity.SalaryMonthOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryMonthOverrideDao {

    @Query("SELECT * FROM salary_month_overrides ORDER BY year_month DESC")
    fun getAllOverrides(): Flow<List<SalaryMonthOverrideEntity>>

    @Upsert
    suspend fun upsert(override: SalaryMonthOverrideEntity)

    @Query("DELETE FROM salary_month_overrides WHERE year_month = :yearMonth")
    suspend fun deleteOverride(yearMonth: String)

    @Query("DELETE FROM salary_month_overrides")
    suspend fun deleteAllOverrides()
}
