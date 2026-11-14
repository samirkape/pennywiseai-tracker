package com.spendly.tracker.connector.data.export

import com.spendly.tracker.connector.domain.TransactionBrief
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The per-transaction rows as CSV, for spreadsheets and scripts.
 *
 * The columns are the same as the ones in the plain-text brief, in the same
 * order, so a row can be lined up with the text file.
 */
object SpendBriefCsvFormatter {

    private val COLUMNS = listOf(
        "transaction_id",
        "date",
        "type",
        "amount",
        "currency",
        "category",
        "subcategory",
        "merchant_original",
        "merchant_normalized",
        "payment_method",
        "account_alias",
        "tag",
        "linked_transaction_id",
        "notes",
        "essentiality",
        "behaviour",
        "refund_kind",
        "refund_status"
    )

    fun format(transactions: List<TransactionBrief>): String = buildString {
        appendLine(COLUMNS.joinToString(","))
        transactions.forEach { row ->
            appendLine(
                listOf(
                    row.transactionId,
                    row.date.toString(),
                    row.type.label,
                    plain(row.amount),
                    row.currency,
                    row.category,
                    row.subcategory,
                    row.merchantOriginal,
                    row.merchantNormalized,
                    row.paymentMethod.label,
                    row.accountAlias,
                    row.tag,
                    row.linkedTransactionId,
                    row.notes,
                    row.essentiality.label,
                    row.behaviour.label,
                    row.refundKind?.label,
                    row.refundMatchStatus?.label
                ).joinToString(",") { escape(it) }
            )
        }
    }

    /** Wraps a value in quotes and doubles any quote inside it. */
    private fun escape(value: String?): String {
        val raw = value ?: ""
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    private fun plain(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
