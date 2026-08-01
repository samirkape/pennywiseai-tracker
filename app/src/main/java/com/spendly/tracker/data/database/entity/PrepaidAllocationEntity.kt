package com.spendly.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(
    tableName = "prepaid_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PrepaidExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["prepaid_expense_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["prepaid_expense_id"]),
        Index(value = ["period_year_month"]),
        Index(value = ["status"])
    ]
)
data class PrepaidAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "prepaid_expense_id")
    val prepaidExpenseId: Long,

    /** "yyyy-MM" (java.time.YearMonth#toString), sorts correctly with SQL BETWEEN. */
    @ColumnInfo(name = "period_year_month")
    val periodYearMonth: String,

    @ColumnInfo(name = "allocated_amount")
    val allocatedAmount: BigDecimal,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "status", defaultValue = "PENDING")
    val status: PrepaidAllocationStatus = PrepaidAllocationStatus.PENDING,

    @ColumnInfo(name = "recognized_at")
    val recognizedAt: LocalDateTime? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class PrepaidAllocationStatus {
    PENDING,
    RECOGNIZED,
    REVERSED
}
