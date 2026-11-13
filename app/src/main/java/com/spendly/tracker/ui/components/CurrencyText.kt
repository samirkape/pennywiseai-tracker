package com.spendly.tracker.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Headline money sizes (sp) chosen so a long total steps down a size class instead of being
 * clipped or ellipsized — a currency value must never be truncated.
 */
fun headlineMoneySize(length: Int): Float = when {
    length <= 10 -> 32f
    length <= 13 -> 28f
    else -> 24f
}

/** Same ladder for money rendered inline in dense rows/columns (base 15sp). */
fun inlineMoneySize(length: Int): Float = when {
    length <= 9 -> 15f
    length <= 12 -> 13f
    else -> 11f
}

/**
 * Renders a formatted currency string with the leading symbol (`₹`, `$`, `€`…) at ~62% of the
 * base size so the digits read as the dominant element, the way money is set in print.
 */
fun currencyAnnotated(text: String, baseSizeSp: Float, symbolScale: Float = 0.62f): AnnotatedString {
    val symbolEnd = text.indexOfFirst { it.isDigit() }
    if (symbolEnd <= 0) return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontSize = (baseSizeSp * symbolScale).sp,
                letterSpacing = 0.5f.sp,
            ),
        ) {
            append(text.substring(0, symbolEnd))
        }
        append(text.substring(symbolEnd))
    }
}
