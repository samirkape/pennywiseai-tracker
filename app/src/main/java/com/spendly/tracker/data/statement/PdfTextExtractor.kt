package com.spendly.tracker.data.statement

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTextExtractor {

    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    fun extractText(context: Context, uri: Uri): String = extractText(context, uri, password = null)

    /**
     * @param password for encrypted PDFs (e.g. CAS files). A wrong or missing password
     * surfaces as [com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException].
     */
    fun extractText(context: Context, uri: Uri, password: String?): String {
        ensureInitialized(context)

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open PDF at $uri")

        return inputStream.use { stream ->
            val document = if (password.isNullOrEmpty()) {
                PDDocument.load(stream)
            } else {
                PDDocument.load(stream, password)
            }
            document.use { PDFTextStripper().getText(it) }
        }
    }
}
