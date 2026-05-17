package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.LocalDateTime

@Entity(
    tableName = "transaction_categories",
    primaryKeys = ["transaction_id", "category_name"]
)
data class TransactionCategoryEntity(
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "category_name")
    val categoryName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
