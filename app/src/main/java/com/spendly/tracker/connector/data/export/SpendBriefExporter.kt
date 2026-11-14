package com.spendly.tracker.connector.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.SpendBriefFormatter
import com.spendly.tracker.connector.domain.TransactionBrief
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the AI-safe spending brief to a shareable file.
 *
 * Files live in the cache directory exposed by the app's [FileProvider], so the user
 * picks the destination themselves through the system share sheet.
 */
@Singleton
class SpendBriefExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** File name suggested to the system save dialog. */
    fun suggestedFileName(format: BriefFormat = BriefFormat.TEXT): String =
        "$FILE_PREFIX${LocalDate.now().format(DATE_STAMP)}${format.extension}"

    /** Writes [text] to a user-picked destination (Downloads, Drive, …). */
    fun writeTo(uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw java.io.IOException("Could not open the chosen location")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun write(
        analysis: SpendAnalysis,
        question: String,
        transactions: List<TransactionBrief> = emptyList(),
        format: BriefFormat = BriefFormat.TEXT
    ): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        directory.listFiles { file -> file.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }

        val file = File(directory, suggestedFileName(format))
        file.writeText(render(analysis, question, transactions, format), Charsets.UTF_8)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /** The brief in the requested shape. */
    fun render(
        analysis: SpendAnalysis,
        question: String,
        transactions: List<TransactionBrief> = emptyList(),
        format: BriefFormat = BriefFormat.TEXT
    ): String = when (format) {
        BriefFormat.TEXT -> SpendBriefFormatter.format(analysis, question, transactions)
        BriefFormat.JSON -> SpendBriefJsonFormatter.format(analysis, transactions)
        BriefFormat.CSV -> SpendBriefCsvFormatter.format(transactions)
    }

    /** Shapes the brief can be shared in. */
    enum class BriefFormat(val extension: String, val mimeType: String, val label: String) {
        TEXT(".txt", "text/plain", "Text"),
        JSON(".json", "application/json", "JSON"),
        CSV(".csv", "text/csv", "CSV")
    }

    private companion object {
        const val DIRECTORY = "exports"
        const val FILE_PREFIX = "spendly-brief-"
        val DATE_STAMP: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
