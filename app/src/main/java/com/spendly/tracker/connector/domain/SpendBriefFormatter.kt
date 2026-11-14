package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a [SpendAnalysis] as a portable text brief the user can hand to any AI app.
 *
 * The data section is byte-for-byte the payload the connector sends over the network,
 * so both paths expose exactly the same aggregated figures.
 */
object SpendBriefFormatter {

    /** Column order of the per-transaction rows. */
    private val TRANSACTION_COLUMNS = listOf(
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

    fun format(
        analysis: SpendAnalysis,
        question: String = "",
        transactions: List<TransactionBrief> = emptyList(),
        generatedAt: LocalDateTime = LocalDateTime.now()
    ): String = buildString {
        appendLine("SPENDLY SPENDING BRIEF")
        appendLine("Generated: ${generatedAt.format(TIMESTAMP)}")
        appendLine()
        appendLine("ABOUT THIS FILE")
        appendLine("Aggregated spending totals for one period, computed on the device.")
        appendLine("Amounts are plain decimals in the currency named below; dates are ISO (YYYY-MM-DD).")
        appendLine()
        appendLine("HOW THE NUMBERS ARE BUILT")
        appendLine("- gross_expense is every spending row in the period, before refunds.")
        appendLine("- refunds_received is money received back (refunds, returns, insurance")
        appendLine("  reimbursements, card cashback). Reimbursements are never income.")
        appendLine("- net_expense is gross_expense minus refunds_received.")
        appendLine("- A refund is linked to the expense it reverses when the original is in")
        appendLine("  the period; otherwise it is reported as an unmatched refund.")
        appendLine("- A refund counts in the period it was received, so a refund that arrives")
        appendLine("  after this period is not deducted yet.")
        appendLine("- Spending excludes transfers, credit card bill payments, investments and")
        appendLine("  loan-linked rows; those are reported separately under MONEY_MOVED.")
        appendLine("- A credit card purchase counts as spending; the card bill payment that")
        appendLine("  settles it does not, so the same money is never counted twice.")
        appendLine("- essentiality says whether a row was needed; behaviour says whether the")
        appendLine("  amount is fixed, variable or occasional. Unknown means the data did not")
        appendLine("  say, not that the answer is zero.")
        appendLine("- DATA_QUALITY lists everything that was excluded or could not be matched.")
        appendLine("It contains amounts, dates, categories and merchant names - no messages,")
        appendLine("transaction descriptions, account numbers or bank names.")
        if (transactions.isNotEmpty()) {
            appendLine("The transaction list below covers every row in the period, spending,")
            appendLine("income, refunds and money that only moved.")
        }
        appendLine()
        appendLine("HOW TO USE IT")
        appendLine("Attach this file to ChatGPT, Claude, Gemini or any other AI assistant,")
        appendLine("then ask your question. Examples:")
        appendLine("- Where is my money going?")
        appendLine("- Which recurring charges should I cancel?")
        appendLine("- How can I save 10% next month?")
        appendLine("- Which months did I overspend, and why?")
        appendLine()

        question.trim().takeIf { it.isNotEmpty() }?.let {
            appendLine("QUESTION")
            appendLine(it)
            appendLine()
        }

        appendLine("DATA")
        append(analysis.renderPromptPayload().trimEnd())
        appendLine()

        if (transactions.isNotEmpty()) {
            appendLine()
            appendLine("TRANSACTIONS (${TRANSACTION_COLUMNS.joinToString(" | ")})")
            transactions.forEach { row ->
                appendLine("- ${row.render()}")
            }
            appendLine()
        }
    }

    /** One row of the transaction list, with `-` standing in for an empty value. */
    private fun TransactionBrief.render(): String = listOf(
        transactionId,
        date.toString(),
        type.label,
        plain(amount),
        currency,
        category,
        subcategory.orDash(),
        merchantOriginal,
        merchantNormalized,
        paymentMethod.label,
        accountAlias.orDash(),
        tag.orDash(),
        linkedTransactionId.orDash(),
        notes.orDash(),
        essentiality.label,
        behaviour.label,
        refundKind?.label.orDash(),
        refundMatchStatus?.label.orDash()
    ).joinToString(" | ")

    private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "-"

    /** Plain decimals only, so the model cannot misread locale-specific formatting. */
    private fun plain(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)
}
