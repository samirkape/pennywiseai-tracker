package com.spendly.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "prepaid_expenses",
    indices = [
        Index(value = ["source_transaction_id"]),
        Index(value = ["status"]),
        Index(value = ["start_date"]),
        Index(value = ["end_date"])
    ]
)
data class PrepaidExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "source_transaction_id")
    val sourceTransactionId: Long,

    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "total_amount")
    val totalAmount: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    @ColumnInfo(name = "total_months")
    val totalMonths: Int,

    @ColumnInfo(name = "end_date")
    val endDate: LocalDate,

    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: PrepaidExpenseStatus = PrepaidExpenseStatus.ACTIVE,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "profile_id", defaultValue = "NULL")
    val profileId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "cancelled_at")
    val cancelledAt: LocalDateTime? = null
)

enum class PrepaidExpenseStatus {
    ACTIVE,
    CANCELLED,
    COMPLETED,
    REFUNDED
}
