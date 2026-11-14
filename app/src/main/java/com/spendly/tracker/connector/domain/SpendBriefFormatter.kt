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
        appendLine("Spending excludes transfers, credit card bill payments, investments and")
        appendLine("loan-linked rows; those are reported separately under MONEY_MOVED.")
        appendLine("Figures use the same rules and currency as the app's Analytics screen.")
        appendLine("It contains amounts, dates, categories and merchant names - no messages,")
        appendLine("transaction descriptions, account numbers or bank names.")
        if (transactions.isNotEmpty()) {
            appendLine("The transaction list below covers spending and income rows only.")
        }
        appendLine()
        appendLine("HOW TO USE IT")
        appendLine("Attach this file to ChatGPT, Claude, Gemini or any other AI assistant,")
        appendLine("then ask your question. Examples:")
        appendLine("- Where is my money going?")
        appendLine("- Which recurring charges should I cancel?")
        appendLine("- How can I save 10% next month?")
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
            appendLine("TRANSACTIONS (date | type | amount | category | merchant)")
            transactions.forEach {
                appendLine(
                    "- ${it.date} | ${it.type.label} | ${plain(it.amount)} | " +
                        "${it.category} | ${it.merchant}"
                )
            }
            appendLine()
        }
    }

    /** Plain decimals only, so the model cannot misread locale-specific formatting. */
    private fun plain(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)
}
