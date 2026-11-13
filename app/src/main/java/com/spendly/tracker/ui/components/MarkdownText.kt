package com.spendly.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Renders the small subset of markdown the providers produce: headings, bullet and
 * numbered lists, bold, italics and inline code.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = buildMarkdown(text, style, color),
        modifier = modifier,
        style = style,
        color = color
    )
}

private val inlinePattern =
    Regex("(\\*\\*.+?\\*\\*|`[^`]+`|\\*[^*]+\\*)")

private val orderedListPattern = Regex("^(\\d+)\\.\\s+(.*)$")

fun buildMarkdown(text: String, style: TextStyle, color: Color): AnnotatedString =
    buildAnnotatedString {
        text.lines().forEachIndexed { index, rawLine ->
            if (index > 0) append('\n')
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> append('\n')
                line.startsWith("### ") -> appendHeading(line.removePrefix("### "), 3, style)
                line.startsWith("## ") -> appendHeading(line.removePrefix("## "), 2, style)
                line.startsWith("# ") -> appendHeading(line.removePrefix("# "), 1, style)
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("•  ")
                    appendInline(line.drop(2))
                }
                else -> {
                    val match = orderedListPattern.matchEntire(line.trim())
                    if (match != null) {
                        append("${match.groupValues[1]}.  ")
                        appendInline(match.groupValues[2])
                    } else {
                        appendInline(line)
                    }
                }
            }
        }
    }

private fun AnnotatedString.Builder.appendHeading(content: String, level: Int, style: TextStyle) {
    val scale = when (level) {
        1 -> 1.25f
        2 -> 1.15f
        else -> 1.05f
    }
    withStyle(
        SpanStyle(
            fontWeight = FontWeight.Bold,
            fontSize = style.fontSize * scale,
            color = style.color
        )
    ) {
        appendInline(content)
    }
    append('\n')
}

private fun AnnotatedString.Builder.appendInline(content: String) {
    var cursor = 0
    inlinePattern.findAll(content).forEach { match ->
        if (match.range.first > cursor) {
            append(content.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(token.removeSurrounding("`"))
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.removeSurrounding("*"))
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        append(content.substring(cursor))
    }
}
