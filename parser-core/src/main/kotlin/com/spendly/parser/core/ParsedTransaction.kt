package com.spendly.parser.core

import java.math.BigDecimal

data class ParsedTransaction(
    val amount: BigDecimal,
    val type: TransactionType,
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: BigDecimal?,
    val creditLimit: BigDecimal? = null,
    val smsBody: String,
    val sender: String,
    val timestamp: Long,
    val bankName: String,
    val transactionHash: String? = null,
    val isFromCard: Boolean = false,
    val currency: String = "INR",
    val fromAccount: String? = null,
    val toAccount: String? = null,
    /**
     * Optional transfer classification, used to flag transactions that should be
     * excluded from spending (e.g. credit card bill payments). Mirrors the
     * `transfer_kind` column on TransactionEntity. See [TransferKinds] for values.
     */
    val transferKind: String? = null,
    /**
     * Mobile-money wallet (e.g. eMola, M-Pesa Mozambique): the SMS carries a
     * running balance but no per-account number, because the whole wallet IS the
     * account. When true, the app derives a single service-level account row from
     * the balance instead of requiring an accountLast4.
     */
    val isMobileWallet: Boolean = false,
    /**
     * Currency the ACCOUNT (balance / limit) is kept in, when it differs from the
     * transaction's: a USD spend on an INR credit card. Null means "same as
     * [currency]" — the common case, and the right answer for a foreign-currency
     * account whose SMS reports its balance in that currency.
     */
    val accountCurrency: String? = null
) {
    /**
     * Convenience flag: true when this parsed transaction represents a credit card
     * bill payment leg (TRANSFER + transferKind = CC_BILL_PAYMENT).
     */
    fun isCreditCardBillPayment(): Boolean =
        type == TransactionType.TRANSFER && transferKind == TransferKinds.CC_BILL_PAYMENT

    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP)
        // Use SMS body hash for reliable deduplication across different timestamp sources
        // (BroadcastReceiver uses SC timestamp, ContentProvider uses device timestamp)
        val smsBodyHash = md5Hex(smsBody)
            .take(16) // First 16 chars of SMS body hash
        val data = "$sender|$normalizedAmount|$smsBodyHash"
        return md5Hex(data)
    }
}

/**
 * String constants for [ParsedTransaction.transferKind]. Kept in parser-core so
 * parsers can set them without depending on the app module.
 */
object TransferKinds {
    const val CC_BILL_PAYMENT = "CC_BILL_PAYMENT"
    const val SELF_TRANSFER = "SELF_TRANSFER"
    /** Detected as a possible self-transfer but awaiting user confirmation. */
    const val SELF_TRANSFER_PENDING = "SELF_TRANSFER_PENDING"
}


