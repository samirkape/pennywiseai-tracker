package com.spendly.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendly.tracker.data.database.entity.NetWorthSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetWorthSourceDao {

    @Query("SELECT * FROM net_worth_sources ORDER BY type ASC, name ASC")
    fun getAll(): Flow<List<NetWorthSourceEntity>>

    @Query("SELECT * FROM net_worth_sources")
    suspend fun getAllOnce(): List<NetWorthSourceEntity>

    @Query("SELECT * FROM net_worth_sources WHERE id = :id")
    suspend fun getById(id: Long): NetWorthSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NetWorthSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NetWorthSourceEntity>)

    @Update
    suspend fun update(entity: NetWorthSourceEntity)

    @Query("DELETE FROM net_worth_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM net_worth_sources WHERE origin = :origin")
    suspend fun deleteByOrigin(origin: String)

    @Query("DELETE FROM net_worth_sources")
    suspend fun deleteAll()

    /** Replaces every imported holding with the given set, atomically. */
    @Transaction
    suspend fun replaceByOrigin(origin: String, entities: List<NetWorthSourceEntity>) {
        deleteByOrigin(origin)
        insertAll(entities)
    }
}
