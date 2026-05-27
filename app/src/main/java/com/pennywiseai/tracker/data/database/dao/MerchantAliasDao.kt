package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM merchant_aliases")
    suspend fun deleteAllAliases()
}
