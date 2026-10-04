package com.spendly.tracker.data.statement

import android.content.Context
import android.net.Uri
import com.spendly.tracker.data.networth.CasRow
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reads a PDF into lines of table cells using glyph positions.
 *
 * Words that sit on the same baseline are joined into one cell unless the horizontal gap
 * between them exceeds a fraction of the font size, which separates table columns from
 * ordinary word spacing.
 */
object PdfCellExtractor {

    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    fun extractRows(context: Context, uri: Uri, password: String?): List<CasRow> {
        ensureInitialized(context)
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open PDF")
        return stream.use {
            val document = if (password.isNullOrEmpty()) PDDocument.load(it) else PDDocument.load(it, password)
            document.use { doc -> collect(doc) }
        }
    }

    private class Word(val page: Int, val text: String, val x0: Float, val x1: Float, val y: Float, val size: Float)

    private fun collect(document: PDDocument): List<CasRow> {
        val words = mutableListOf<Word>()
        val stripper = object : PDFTextStripper() {
            private var page = 0

            override fun startPage(page: PDPage?) {
                this.page++
                super.startPage(page)
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                if (text.isBlank() || textPositions.isEmpty()) return
                val first = textPositions.first()
                val last = textPositions.last()
                words += Word(
                    page = page,
                    text = text.trim(),
                    x0 = first.xDirAdj,
                    x1 = last.xDirAdj + last.widthDirAdj,
                    y = first.yDirAdj,
                    size = first.fontSizeInPt
                )
            }
        }
        stripper.sortByPosition = true
        stripper.getText(document)

        return words
            .groupBy { it.page to (it.y / BASELINE_TOLERANCE).roundToInt() }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .map { (key, lineWords) -> CasRow(key.first, toCells(lineWords.sortedBy { it.x0 })) }
            .filter { it.cells.isNotEmpty() }
    }

    private fun toCells(words: List<Word>): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var previousEnd = 0f
        words.forEachIndexed { index, word ->
            val gap = word.x0 - previousEnd
            if (index > 0 && gap > max(MIN_COLUMN_GAP, word.size * COLUMN_GAP_FONT_FRACTION)) {
                cells += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word.text)
            previousEnd = word.x1
        }
        if (current.isNotEmpty()) cells += current.toString()
        return cells
    }

    private const val BASELINE_TOLERANCE = 3f
    private const val MIN_COLUMN_GAP = 5f
    private const val COLUMN_GAP_FONT_FRACTION = 0.8f
}
