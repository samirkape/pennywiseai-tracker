package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to cache computed smart insights.
 */
@Entity(tableName = "insights_cache")
data class InsightsCacheEntity(
    @PrimaryKey
    val key: String,         // e.g. "anomaly_2026-06", "pace_2026-06"

    val payload: String,     // JSON string of the insight data

    @ColumnInfo(name = "computed_at_epoch")
    val computedAtEpoch: Long,

    @ColumnInfo(name = "data_window_months")
    val dataWindowMonths: Int,

    @ColumnInfo(name = "transaction_count")
    val transactionCount: Int
)

