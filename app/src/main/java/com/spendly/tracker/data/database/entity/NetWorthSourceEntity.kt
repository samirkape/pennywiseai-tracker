package com.spendly.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A single asset or liability counted toward net worth that is not derived from
 * transactions: manual entries (cash, gold, FD...) and holdings imported from a CAS.
 */
@Entity(
    tableName = "net_worth_sources",
    indices = [
        Index(value = ["origin"]),
        Index(value = ["type"]),
        Index(value = ["external_key"])
    ]
)
data class NetWorthSourceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: NetWorthSourceType,

    @ColumnInfo(name = "origin", defaultValue = "MANUAL")
    val origin: NetWorthSourceOrigin = NetWorthSourceOrigin.MANUAL,

    /** Current value in [currency]. Always positive; liabilities are signed by [NetWorthSourceType.isLiability]. */
    @ColumnInfo(name = "value")
    val value: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    /** Stable identifier for imported holdings (e.g. ISIN plus folio) so re-imports replace rather than duplicate. */
    @ColumnInfo(name = "external_key")
    val externalKey: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "profile_id", defaultValue = "NULL")
    val profileId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class NetWorthSourceOrigin {
    MANUAL,
    CAS
}

enum class NetWorthSourceType(
    val displayName: String,
    val isLiability: Boolean = false
) {
    CASH("Cash"),
    GOLD("Gold"),
    FIXED_DEPOSIT("Fixed deposit"),
    PROVIDENT_FUND("PF / EPF"),
    PROPERTY("Property"),
    MUTUAL_FUND("Mutual funds"),
    STOCKS("Stocks"),
    OTHER_ASSET("Other asset"),
    OTHER_LIABILITY("Other liability", isLiability = true)
}
