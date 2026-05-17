package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "merchant_aliases")
data class MerchantAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_merchant")
    val sourceMerchant: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
