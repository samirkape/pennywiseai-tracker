package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendly.tracker.data.database.entity.MerchantAliasEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface MerchantAliasDao {

    @Query("SELECT display_name FROM merchant_aliases WHERE source_merchant = :sourceMerchant")
    suspend fun getDisplayNameForMerchant(sourceMerchant: String): String?

    @Query("SELECT * FROM merchant_aliases WHERE source_merchant = :sourceMerchant LIMIT 1")
    suspend fun getAlias(sourceMerchant: String): MerchantAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAlias(alias: MerchantAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: MerchantAliasEntity)

    @Query("SELECT * FROM merchant_aliases ORDER BY source_merchant ASC")
    fun getAllAliases(): Flow<List<MerchantAliasEntity>>

    @Query("SELECT * FROM merchant_aliases")
    suspend fun getAllAliasesList(): List<MerchantAliasEntity>

    @Query("DELETE FROM merchant_aliases WHERE source_merchant = :sourceMerchant")
    suspend fun deleteAlias(sourceMerchant: String)

    @Query("UPDATE merchant_aliases SET last_used_at = :timestamp WHERE source_merchant = :sourceMerchant COLLATE NOCASE")
    suspend fun markAliasUsed(sourceMerchant: String, timestamp: LocalDateTime)

    /**
     * Backfill for aliases created before usage tracking existed: infers last use from
     * transaction history. A transaction "used" an alias when its SMS body mentions the
     * alias source, or when it was auto-renamed (not manually edited) to the display name
     * on/after the alias was created. No-op for aliases that already have last_used_at.
     */
    @Query("""
        UPDATE merchant_aliases SET last_used_at = COALESCE((
            SELECT MAX(t.date_time) FROM transactions t
            WHERE t.is_deleted = 0 AND (
                (length(source_merchant) >= 5 AND instr(lower(t.sms_body), lower(source_merchant)) > 0)
                OR (length(display_name) >= 5 AND t.merchant_manually_edited = 0
                    AND lower(t.merchant_name) = lower(display_name)
                    AND t.date_time >= created_at)
            )
        ), last_used_at)
        WHERE last_used_at IS NULL
    """)
    suspend fun backfillLastUsedFromTransactions(): Int

    @Query("DELETE FROM merchant_aliases")
    suspend fun deleteAllAliases()
}
