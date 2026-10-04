package com.spendly.tracker.data.networth

import com.spendly.tracker.data.database.entity.NetWorthSourceType
import java.math.BigDecimal

/** One text line of a statement page, split into table cells by horizontal gaps. */
data class CasRow(val page: Int, val cells: List<String>) {
    val text: String get() = cells.joinToString(" ")
}

data class CasHolding(
    val name: String,
    val isin: String?,
    val folio: String?,
    val value: BigDecimal,
    val type: NetWorthSourceType
) {
    /** Stable key so a re-import of the same holding replaces the previous row. */
    val externalKey: String get() = listOfNotNull(isin, folio).joinToString("|").ifEmpty { name }
}

/**
 * Parser for CDSL Consolidated Account Statements.
 *
 * The column rules follow the CDSL parser of the MIT-licensed casparser project
 * (https://github.com/codereverser/casparser, Copyright (c) Sandeep Somasekharan),
 * adapted to PdfBox cells instead of PDFium atoms.
 *
 * Real statements carry a bilingual overlay that garbles many headings, so section
 * headers cannot be relied on. Equity holdings are recognised by their shape instead:
 *  - the row starts with an ISIN,
 *  - it contains no date (transaction rows always carry one),
 *  - and it ends with the market value after the balance columns.
 * The mutual fund table ("MUTUAL FUND UNITS HELD AS ON") is still read by section, with rows
 * laid out as name | ISIN | folio | [ARN/DIRECT] | units | NAV | [invested] | value | ...
 */
object CasParser {

    private val isinRegex = Regex("""^[A-Z]{2}[0-9A-Z]{9}\d$""")
    private val infIsinRegex = Regex("""^INF[0-9A-Z]{8}\d$""")
    private val numericRegex = Regex("""^-?(?:[\d,]+(?:\.\d+)?|\.\d+)$""")
    private val folioTailRegex = Regex("""\d+/\d+""")
    private val dateRegex = Regex("""\d{2}[-/](?:\d{2}|[A-Za-z]{3})[-/]\d{4}""")
    private val totalLabels = setOf("sub total", "total", "grand total")

    fun parse(rows: List<CasRow>): List<CasHolding> {
        val holdings = mutableListOf<CasHolding>()
        var inMutualFundTable = false
        var pendingMutualFund: MutableList<String>? = null
        var equityCount = 0

        fun flushMutualFund() {
            pendingMutualFund?.let { cells -> parseMutualFundRow(cells)?.let(holdings::add) }
            pendingMutualFund = null
        }

        for (row in rows) {
            val lower = row.text.lowercase()
            when {
                "mutual fund units held as on" in lower -> {
                    flushMutualFund()
                    inMutualFundTable = true
                    continue
                }
                "statement of transactions" in lower || ("holding statement" in lower && "as on" in lower) -> {
                    flushMutualFund()
                    inMutualFundTable = false
                    continue
                }
            }

            val first = row.cells.firstOrNull()?.trim().orEmpty()
            when {
                first.lowercase() in totalLabels -> flushMutualFund()

                isinRegex.matches(first) -> {
                    flushMutualFund()
                    parseEquityRow(row.cells, equityCount + 1)?.let {
                        equityCount++
                        holdings += it
                    }
                }

                inMutualFundTable && row.cells.take(3).any { isinRegex.matches(it.trim()) } -> {
                    flushMutualFund()
                    pendingMutualFund = row.cells.toMutableList()
                }

                // Wrapped cells of a mutual fund row appear on the lines below it.
                else -> pendingMutualFund?.addAll(row.cells)
            }
        }
        flushMutualFund()
        return holdings
    }

    private fun parseEquityRow(cells: List<String>, sequence: Int): CasHolding? {
        val isin = cells.first().trim()
        val rest = cells.drop(1)
        if (rest.size < 2 || rest.any { dateRegex.containsMatchIn(it) }) return null
        // The market value is the final column.
        if (!looksNumeric(rest.last())) return null

        val dataStart = rest.indexOfFirst {
            val t = it.trim()
            looksNumeric(t) || t == "--" || t == "-"
        }
        if (dataStart < 0) return null
        if (rest.drop(dataStart).count { looksNumeric(it) } < 2) return null

        val value = toDecimal(rest.last())
        if (value.signum() <= 0) return null
        // Names often wrap onto separate lines around the ISIN row, so only an inline name is used.
        val name = rest.take(dataStart)
            .map { it.replace("\n", " ").trim() }
            .filter { it.isNotEmpty() && it != "@" }
            .joinToString(" ")
            .ifEmpty { isin }

        return CasHolding(
            name = name,
            isin = isin,
            // The demat account is not readable on every layout, so keep repeats of an ISIN apart.
            folio = "#$sequence",
            value = value,
            type = typeForIsin(isin)
        )
    }

    private fun parseMutualFundRow(cells: List<String>): CasHolding? {
        if (cells.size < 5) return null
        val isinIdx = (0 until minOf(3, cells.size)).firstOrNull { isinRegex.matches(cells[it].trim()) }
            ?: return null
        val isin = cells[isinIdx].trim()
        val name = cells.take(isinIdx).joinToString(" ") { it.replace("\n", " ").trim() }
            .trim().ifEmpty { isin }

        var folio = cells.getOrNull(isinIdx + 1)?.trim()?.ifEmpty { null }
        var folioEnd = isinIdx + 1
        // A long folio wraps its tail into the next cell, e.g. "910121125" | "82/0".
        val tail = cells.getOrNull(isinIdx + 2)?.trim()
        if (folio != null && tail != null && folioTailRegex.matches(tail)) {
            folio += tail
            folioEnd = isinIdx + 2
        }

        // A non-numeric cell after the folio is the ARN/DIRECT column.
        val discIdx = folioEnd + 1
        val hasDistributionColumn = discIdx < cells.size && !looksNumeric(cells[discIdx])
        val dataStart = discIdx + if (hasDistributionColumn) 1 else 0
        val numerics = cells.drop(dataStart).map { it.trim() }.filter(::looksNumeric)
        if (numerics.size < 3) return null

        // units | NAV | invested | value | ...  or, with no invested column, units | NAV | value.
        val value = toDecimal(if (numerics.size >= 4) numerics[3] else numerics[2])
        if (value.signum() <= 0) return null

        return CasHolding(name, isin, folio, value, typeForIsin(isin))
    }

    private fun typeForIsin(isin: String) =
        if (infIsinRegex.matches(isin)) NetWorthSourceType.MUTUAL_FUND else NetWorthSourceType.STOCKS

    private fun looksNumeric(text: String) = text.isNotBlank() && numericRegex.matches(text.trim())

    private fun toDecimal(text: String): BigDecimal =
        text.replace(",", "").trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
}
