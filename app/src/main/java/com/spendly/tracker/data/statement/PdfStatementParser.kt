package com.spendly.tracker.data.statement

import com.spendly.parser.core.ParsedTransaction

interface PdfStatementParser {
    fun canHandle(text: String): Boolean
    fun parse(text: String): List<ParsedTransaction>
}
