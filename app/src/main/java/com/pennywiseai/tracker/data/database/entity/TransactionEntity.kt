package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transaction_hash"], unique = true),
        Index(value = ["linked_transaction_id"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "amount")
    val amount: BigDecimal,
    
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    
    @ColumnInfo(name = "category")
    val category: String,
    
    @ColumnInfo(name = "transaction_type")
    val transactionType: TransactionType,
    
    @ColumnInfo(name = "date_time")
    val dateTime: LocalDateTime,
    
    @SerializedName(value = "description", alternate = ["notes"])
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "sms_body")
    val smsBody: String? = null,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,
    
    @ColumnInfo(name = "sms_sender")
    val smsSender: String? = null,
    
    @ColumnInfo(name = "account_number")
    val accountNumber: String? = null,
    
    @ColumnInfo(name = "balance_after")
    val balanceAfter: BigDecimal? = null,
    
    @ColumnInfo(name = "transaction_hash", defaultValue = "")
    val transactionHash: String,
    
    @ColumnInfo(name = "is_recurring")
    val isRecurring: Boolean = false,
    
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "is_excluded_from_tracking", defaultValue = "0")
    val isExcludedFromTracking: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "from_account")
    val fromAccount: String? = null,

    @ColumnInfo(name = "to_account")
    val toAccount: String? = null,

    @ColumnInfo(name = "reference")
    val reference: String? = null,

    @ColumnInfo(name = "loan_id", defaultValue = "NULL")
    val loanId: Long? = null,

    @ColumnInfo(name = "receipt_path", defaultValue = "NULL")
    val receiptPath: String? = null,

    @ColumnInfo(name = "budget_category", defaultValue = "NULL")
    val budgetCategory: String? = null,

    @ColumnInfo(name = "budget_impact_type", defaultValue = "NULL")
    val budgetImpactType: BudgetImpactType? = null,

    @ColumnInfo(name = "group_id", defaultValue = "NULL")
    val groupId: Long? = null,

    @ColumnInfo(name = "profile_id", defaultValue = "NULL")
    val profileId: Long? = null,

    @ColumnInfo(name = "tags", defaultValue = "")
    val tags: String = "",

    @ColumnInfo(name = "linked_transaction_id", defaultValue = "NULL")
    val linkedTransactionId: Long? = null,

    @ColumnInfo(name = "transfer_kind", defaultValue = "NULL")
    val transferKind: String? = null
)

/**
 * Kinds of transfer-like records. Stored as raw strings in `transfer_kind`.
 * - CC_BILL_PAYMENT: credit card bill payment (the debit leg from a bank account
 *   and / or the credit leg on the card). These rows are excluded from spend.
 * - SELF_TRANSFER: a transfer between the user's own accounts (excluded from spend).
 * - OTHERS_TRANSFER: a transfer to someone else's account (included in spend tracking).
 */
object TransferKind {
    const val CC_BILL_PAYMENT = "CC_BILL_PAYMENT"
    const val SELF_TRANSFER = "SELF_TRANSFER"
    /** Auto-detected as a possible self-transfer; awaiting user confirmation. */
    const val SELF_TRANSFER_PENDING = "SELF_TRANSFER_PENDING"
    const val OTHERS_TRANSFER = "OTHERS_TRANSFER"
}

enum class BudgetImpactType {
    DEDUCT_SPENT,
    ADD_TO_LIMIT
}

enum class TransactionType {
    INCOME,     // Money received
    EXPENSE,    // Money spent from accounts
    CREDIT,     // Credit card purchases
    TRANSFER,   // Between own accounts
    INVESTMENT  // Mutual funds, stocks, etc.
}