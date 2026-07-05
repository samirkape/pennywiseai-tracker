package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendly.tracker.data.database.entity.InsightsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightsCacheDao {
    @Query("SELECT * FROM insights_cache")
    fun getAllInsights(): Flow<List<InsightsCacheEntity>>

    @Query("SELECT * FROM insights_cache WHERE `key` = :key")
    suspend fun getInsightByKey(key: String): InsightsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: InsightsCacheEntity)

    @Query("DELETE FROM insights_cache WHERE `key` = :key")
    suspend fun deleteInsightByKey(key: String)

    @Query("DELETE FROM insights_cache")
    suspend fun clearAll()
}

