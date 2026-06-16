package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["goal_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["contributed_at"])
    ]
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "goal_id")
    val goalId: Long,

    @ColumnInfo(name = "transaction_id", defaultValue = "NULL")
    val transactionId: Long? = null,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "contributed_at")
    val contributedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "source", defaultValue = "MANUAL_DEPOSIT")
    val source: ContributionSource = ContributionSource.MANUAL_DEPOSIT
)

enum class ContributionSource {
    MANUAL_DEPOSIT,
    TRANSACTION_LINKED,
    AUTO
}
