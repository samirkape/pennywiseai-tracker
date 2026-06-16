package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "financial_goals",
    indices = [
        Index(value = ["status"]),
        Index(value = ["goal_type"]),
        Index(value = ["target_date"]),
        Index(value = ["created_at"])
    ]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "goal_type")
    val goalType: GoalType,

    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: GoalStatus = GoalStatus.ACTIVE,

    @ColumnInfo(name = "target_amount")
    val targetAmount: BigDecimal,

    @ColumnInfo(name = "current_amount", defaultValue = "0")
    val currentAmount: BigDecimal = BigDecimal.ZERO,

    @ColumnInfo(name = "target_date")
    val targetDate: LocalDate,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "color", defaultValue = "#4CAF50")
    val color: String = "#4CAF50",

    @ColumnInfo(name = "tracking_mode", defaultValue = "MANUAL_DEPOSIT")
    val trackingMode: GoalTrackingMode = GoalTrackingMode.MANUAL_DEPOSIT,

    @ColumnInfo(name = "auto_track_categories", defaultValue = "")
    val autoTrackCategories: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "completed_at")
    val completedAt: LocalDateTime? = null
)

enum class GoalType {
    SAVINGS,
    EMERGENCY_FUND,
    PURCHASE,
    VACATION,
    DEBT_PAYOFF,
    INVESTMENT,
    CUSTOM
}

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    PAUSED,
    ABANDONED
}

enum class GoalTrackingMode {
    CATEGORY_AUTO,
    MANUAL_DEPOSIT
}
