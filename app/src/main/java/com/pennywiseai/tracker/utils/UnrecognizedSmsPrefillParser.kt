package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Best-effort extraction from bank SMS that failed automatic parsing.
 * User can edit all fields before saving.
 */
object UnrecognizedSmsPrefillParser {

    data class Prefill(
        val amount: BigDecimal?,
        val merchant: String?,
        val dateTime: LocalDateTime,
        val transactionType: TransactionType,
        val bankNameHint: String?,
        val smsBody: String,
        val smsSender: String,
    )

    fun parse(message: UnrecognizedSmsEntity): Prefill {
        val sms = message.smsBody
        val lower = sms.lowercase()
        return Prefill(
            amount = extractAmount(sms),
            merchant = extractMerchantAtOn(sms),
            dateTime = extractDateTime(sms) ?: message.receivedAt,
            transactionType = when {
                lower.contains("credited") || lower.contains("deposited") ||
                    lower.contains("received") -> TransactionType.INCOME
                lower.contains("card") || lower.contains("block cc") ||
                    lower.contains("block pcc") -> TransactionType.CREDIT
                lower.contains("transfer") -> TransactionType.TRANSFER
                lower.contains("mutual fund") || lower.contains("sip") ||
                    lower.contains("investment") -> TransactionType.INVESTMENT
                else -> TransactionType.EXPENSE
            },
            bankNameHint = inferBankFromSender(message.sender),
            smsBody = sms,
            smsSender = message.sender,
        )
    }

    fun extractAmount(sms: String): BigDecimal? {
        val patterns = listOf(
            Regex("""Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""INR\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""₹\s*([0-9,]+(?:\.\d{1,2})?)"""),
        )
        for (pattern in patterns) {
            pattern.find(sms)?.let { match ->
                val raw = match.groupValues[1].replace(",", "")
                return runCatching { BigDecimal(raw) }.getOrNull()
            }
        }
        return null
    }

    fun extractMerchantAtOn(sms: String): String? {
        val atIndex = sms.indexOf(" At ", ignoreCase = true)
        val onIndex = sms.indexOf(" On ", ignoreCase = true)
        if (atIndex != -1 && onIndex != -1 && onIndex > atIndex) {
            return sms.substring(atIndex + 4, onIndex).trim().takeIf { it.isNotEmpty() }
        }
        val atPattern = Regex("""\bat\s+([^@\n]+?)(?:\s+on\s+|\s+by\s+|$)""", RegexOption.IGNORE_CASE)
        return atPattern.find(sms)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun extractDateTime(sms: String): LocalDateTime? {
        val onPattern = Regex(
            """On\s+(\d{4}-\d{2}-\d{2}:\d{2}:\d{2}(?::\d{2})?)""",
            RegexOption.IGNORE_CASE,
        )
        onPattern.find(sms)?.let { match ->
            val raw = match.groupValues[1]
            return parseDateTime(raw)
        }
        val onDateOnly = Regex("""On\s+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
        onDateOnly.find(sms)?.let { match ->
            return runCatching {
                LocalDateTime.parse("${match.groupValues[1]}T00:00:00")
            }.getOrNull()
        }
        return null
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm"),
        )
        for (formatter in formatters) {
            try {
                return LocalDateTime.parse(raw, formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    fun inferBankFromSender(sender: String): String? {
        val upper = sender.uppercase()
        return when {
            upper.contains("HDFC") -> "HDFC Bank"
            upper.contains("ICICI") -> "ICICI Bank"
            upper.contains("SBI") || upper.contains("SBIN") -> "State Bank of India"
            upper.contains("AXIS") -> "Axis Bank"
            upper.contains("KOTAK") -> "Kotak Bank"
            upper.contains("AMEX") -> "American Express"
            else -> null
        }
    }
}
