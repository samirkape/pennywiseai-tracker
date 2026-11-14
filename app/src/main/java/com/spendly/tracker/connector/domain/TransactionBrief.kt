package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One row of the optional per-transaction list in the shared brief.
 *
 * The schema is deliberately flat and machine readable so an assistant can parse
 * it without guessing. Every field is privacy safe: descriptions, SMS bodies,
 * references, account numbers and bank names never enter this shape.
 *
 * @param transactionId stable id of the row, unique inside the file.
 * @param type what the money did — never how it was paid.
 * @param paymentMethod how the money moved.
 * @param merchantOriginal the merchant exactly as the user sees it.
 * @param merchantNormalized the same merchant reduced to a comparable key.
 * @param accountAlias privacy-safe stand-in for the account, never the real number.
 * @param linkedTransactionId the row this one belongs to, for refunds and reversals.
 * @param notes short privacy-safe purpose label, for example `credit_card_bill_payment`.
 */
data class TransactionBrief(
    val transactionId: String,
    val date: LocalDate,
    val type: FinancialType,
    val amount: BigDecimal,
    val currency: String,
    val category: String,
    val subcategory: String?,
    val merchantOriginal: String,
    val merchantNormalized: String,
    val paymentMethod: PaymentMethod,
    val accountAlias: String?,
    val tag: String?,
    val linkedTransactionId: String?,
    val notes: String?,
    val essentiality: Essentiality,
    val behaviour: ExpenseBehaviour,
    val refundKind: RefundKind?,
    val refundMatchStatus: RefundMatchStatus?
) {
    companion object {
        /** Stable, file-local id for a database row. */
        fun idOf(rowId: Long): String = "txn_${rowId.toString().padStart(6, '0')}"
    }
}
