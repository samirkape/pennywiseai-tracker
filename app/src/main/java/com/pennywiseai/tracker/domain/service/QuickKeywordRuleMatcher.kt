package com.pennywiseai.tracker.domain.service

import android.util.Log
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.time.LocalDateTime
import com.pennywiseai.parser.core.PayrollCreditDetector
import com.pennywiseai.parser.core.HdfcExplicitPayee
import com.pennywiseai.tracker.domain.model.QuickKeywordApplyScope
import com.pennywiseai.tracker.domain.model.QuickKeywordExpenseChannel
import com.pennywiseai.tracker.domain.model.QuickKeywordMatchField
import com.pennywiseai.tracker.domain.model.QuickKeywordMatchStats
import com.pennywiseai.tracker.domain.model.QuickKeywordTextMatchMode
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.data.database.entity.TransferKind
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleCompiler.QuickKeywordRuleInput

/**
 * Case-insensitive substring keyword matching (logcat tag: [QuickKeywordRule]).
 */
object QuickKeywordRuleMatcher {

    const val LOG_TAG = "QuickKeywordRule"

    data class Diagnosis(
        val matches: Boolean,
        val reason: String,
        val searchableTextLength: Int = 0,
        val matchedKeyword: String? = null,
        val matchedInField: String? = null,
    )

    data class BatchStats(
        val ruleName: String,
        val keywords: List<String>,
        val poolSize: Int,
        val skippedDeleted: Int = 0,
        val keywordMatched: Int = 0,
        val updated: Int = 0,
        val alreadyHadLabels: Int = 0,
        val typeOverwritten: Int = 0,
        val typeRejected: Int = 0,
        val emptySearchText: Int = 0,
        val noKeywordHit: Int = 0,
        val uncategorizedOnly: Boolean = false,
        val applyScope: QuickKeywordApplyScope = QuickKeywordApplyScope.AllTime,
        val transactionsInRange: Int = 0,
        val withSmsBody: Int = 0,
        val sampleFailures: List<String> = emptyList(),
    ) {
        fun logSummary() {
            Log.i(
                LOG_TAG,
                buildString {
                    append("Batch apply \"")
                    append(ruleName)
                    append("\": scope=")
                    append(applyScope.logLabel)
                    append(", inRange=")
                    append(transactionsInRange)
                    append(", pool=")
                    append(poolSize)
                    if (uncategorizedOnly) append(" (uncategorized/Others only)")
                    append(", keywords=")
                    append(keywords.joinToString(","))
                    append(", keywordMatched=")
                    append(keywordMatched)
                    append(", updated=")
                    append(updated)
                    append(", alreadyLabeled=")
                    append(alreadyHadLabels)
                    append(", typeOverwritten=")
                    append(typeOverwritten)
                    append(", typeRejected=")
                    append(typeRejected)
                    append(", emptySearchText=")
                    append(emptySearchText)
                    append(", noKeywordHit=")
                    append(noKeywordHit)
                    append(", withSmsBody=")
                    append(withSmsBody)
                    append("/")
                    append(poolSize)
                },
            )
            sampleFailures.take(8).forEach { sample ->
                Log.d(LOG_TAG, "No match sample: $sample")
            }
        }
    }

    /**
     * Full haystack for keyword search: scanned SMS body plus parsed fields from that message.
     */
    fun buildMatchText(
        transaction: TransactionEntity,
        smsText: String?,
        matchField: QuickKeywordMatchField = QuickKeywordMatchField.DEFAULT,
    ): String = when (matchField) {
        QuickKeywordMatchField.ALL_TEXT -> buildSearchableText(transaction, smsText)
        QuickKeywordMatchField.SMS_TEXT ->
            sequenceOf(smsText, transaction.smsBody)
                .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .distinct()
                .joinToString(" ")
        QuickKeywordMatchField.MERCHANT -> transaction.merchantName.trim()
        QuickKeywordMatchField.DESCRIPTION -> transaction.description?.trim().orEmpty()
        QuickKeywordMatchField.TAGS -> transaction.tags.trim()
    }

    fun buildSearchableText(transaction: TransactionEntity, smsText: String?): String {
        val smsFromScan = sequenceOf(smsText, transaction.smsBody)
            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinct()
            .joinToString(" ")

        return listOfNotNull(
            smsFromScan.takeIf { it.isNotBlank() },
            transaction.merchantName.trim().takeIf { it.isNotEmpty() },
            transaction.description?.trim()?.takeIf { it.isNotEmpty() },
            transaction.bankName?.trim()?.takeIf { it.isNotEmpty() },
            transaction.smsSender?.trim()?.takeIf { it.isNotEmpty() },
            transaction.reference?.trim()?.takeIf { it.isNotEmpty() },
            transaction.fromAccount?.trim()?.takeIf { it.isNotEmpty() },
            transaction.toAccount?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
    }

    fun decodeKeywordPayload(encoded: String): List<String> =
        if (encoded.contains(QuickKeywordRuleCompiler.KEYWORD_STORAGE_DELIMITER)) {
            encoded.split(QuickKeywordRuleCompiler.KEYWORD_STORAGE_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            listOf(encoded.trim()).filter { it.isNotEmpty() }
        }

    /** True if [haystack] contains any keyword as a case-insensitive substring. */
    fun matchesAnyKeyword(haystack: String, keywords: List<String>): Boolean =
        matchesKeywords(haystack, keywords, QuickKeywordTextMatchMode.CONTAINS_ANY)

    fun matchesKeywords(
        haystack: String,
        keywords: List<String>,
        mode: QuickKeywordTextMatchMode,
    ): Boolean {
        val active = keywords.filter { it.isNotBlank() }
        if (active.isEmpty()) return false
        val text = haystack.trim()
        if (text.isEmpty() && mode != QuickKeywordTextMatchMode.NOT_CONTAINS_ANY) return false

        return when (mode) {
            QuickKeywordTextMatchMode.CONTAINS_ANY ->
                active.any { text.contains(it, ignoreCase = true) }
            QuickKeywordTextMatchMode.CONTAINS_ALL ->
                active.all { text.contains(it, ignoreCase = true) }
            QuickKeywordTextMatchMode.EQUALS_ONE_OF ->
                active.any { text.equals(it.trim(), ignoreCase = true) }
            QuickKeywordTextMatchMode.STARTS_WITH_ANY ->
                active.any { text.startsWith(it, ignoreCase = true) }
            QuickKeywordTextMatchMode.ENDS_WITH_ANY ->
                active.any { text.endsWith(it, ignoreCase = true) }
            QuickKeywordTextMatchMode.NOT_CONTAINS_ANY ->
                active.none { text.contains(it, ignoreCase = true) }
            QuickKeywordTextMatchMode.REGEX_ANY ->
                active.any { keyword ->
                    runCatching {
                        Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(text)
                    }.getOrDefault(false)
                }
        }
    }

    fun findMatchingKeyword(
        haystack: String,
        keywords: List<String>,
        mode: QuickKeywordTextMatchMode = QuickKeywordTextMatchMode.CONTAINS_ANY,
    ): String? {
        val active = keywords.filter { it.isNotBlank() }
        if (active.isEmpty()) return null
        val text = haystack.trim()
        if (!matchesKeywords(text, active, mode)) return null
        return when (mode) {
            QuickKeywordTextMatchMode.CONTAINS_ALL,
            QuickKeywordTextMatchMode.NOT_CONTAINS_ANY,
            ->
                active.first()
            else ->
                active.firstOrNull { keywordMatches(text, it.trim(), mode) }
        }
    }

    private fun keywordMatches(text: String, keyword: String, mode: QuickKeywordTextMatchMode): Boolean =
        when (mode) {
            QuickKeywordTextMatchMode.CONTAINS_ANY,
            QuickKeywordTextMatchMode.CONTAINS_ALL,
            ->
                text.contains(keyword, ignoreCase = true)
            QuickKeywordTextMatchMode.EQUALS_ONE_OF ->
                text.equals(keyword, ignoreCase = true)
            QuickKeywordTextMatchMode.STARTS_WITH_ANY ->
                text.startsWith(keyword, ignoreCase = true)
            QuickKeywordTextMatchMode.ENDS_WITH_ANY ->
                text.endsWith(keyword, ignoreCase = true)
            QuickKeywordTextMatchMode.NOT_CONTAINS_ANY ->
                !text.contains(keyword, ignoreCase = true)
            QuickKeywordTextMatchMode.REGEX_ANY ->
                runCatching {
                    Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(text)
                }.getOrDefault(false)
        }

    fun evaluateKeywordCondition(
        haystack: String,
        encodedKeywords: String,
        operator: ConditionOperator,
    ): Boolean {
        val keywords = decodeKeywordPayload(encodedKeywords)
        val mode = QuickKeywordTextMatchMode.fromConditionOperator(operator) ?: return false
        return matchesKeywords(haystack, keywords, mode)
    }

    fun matchFieldDescription(field: QuickKeywordMatchField): String = when (field) {
        QuickKeywordMatchField.ALL_TEXT -> "all SMS and parsed fields"
        QuickKeywordMatchField.SMS_TEXT -> "SMS body only"
        QuickKeywordMatchField.MERCHANT -> "merchant name only"
        QuickKeywordMatchField.DESCRIPTION -> "description only"
        QuickKeywordMatchField.TAGS -> "tags only"
    }

    fun parseTagsString(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun formatTagsString(tags: List<String>): String =
        tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(",")

    fun textMatchModeDescription(mode: QuickKeywordTextMatchMode): String = when (mode) {
        QuickKeywordTextMatchMode.CONTAINS_ANY -> "contains any keyword"
        QuickKeywordTextMatchMode.CONTAINS_ALL -> "contains all keywords"
        QuickKeywordTextMatchMode.EQUALS_ONE_OF -> "equals one of the keywords"
        QuickKeywordTextMatchMode.STARTS_WITH_ANY -> "starts with any keyword"
        QuickKeywordTextMatchMode.ENDS_WITH_ANY -> "ends with any keyword"
        QuickKeywordTextMatchMode.NOT_CONTAINS_ANY -> "does not contain any keyword"
        QuickKeywordTextMatchMode.REGEX_ANY -> "matches a regex pattern"
    }

    private fun findMatchedField(
        transaction: TransactionEntity,
        smsText: String?,
        keyword: String,
    ): String? {
        val fields = listOf(
            "sms" to sequenceOf(smsText, transaction.smsBody)
                .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .distinct()
                .joinToString(" "),
            "merchant" to transaction.merchantName,
            "description" to (transaction.description ?: ""),
            "bank" to (transaction.bankName ?: ""),
            "sender" to (transaction.smsSender ?: ""),
            "reference" to (transaction.reference ?: ""),
        )
        return fields.firstOrNull { (_, text) ->
            text.isNotBlank() && text.contains(keyword, ignoreCase = true)
        }?.first // field hint for substring hits; sufficient for diagnostics
    }

    fun diagnose(
        transaction: TransactionEntity,
        smsText: String?,
        input: QuickKeywordRuleInput,
    ): Diagnosis {
        if (!input.overwriteTransactionType && !passesMatchTypeFilter(transaction, smsText, input)) {
            return Diagnosis(
                matches = false,
                reason = "Type is ${transaction.transactionType.name} (${matchTypeDescription(input)})",
            )
        }

        val searchable = buildMatchText(transaction, smsText, input.matchField)
        if (searchable.isBlank()) {
            return Diagnosis(
                matches = false,
                reason = "No text in ${matchFieldDescription(input.matchField)} to search",
                searchableTextLength = 0,
            )
        }

        val hit = findMatchingKeyword(searchable, input.keywords, input.textMatchMode)
        if (hit == null) {
            return Diagnosis(
                matches = false,
                reason = "No ${textMatchModeDescription(input.textMatchMode)} in scan text (len=${searchable.length})",
                searchableTextLength = searchable.length,
            )
        }

        return Diagnosis(
            matches = true,
            reason = "OK",
            searchableTextLength = searchable.length,
            matchedKeyword = hit,
            matchedInField = findMatchedField(transaction, smsText, hit),
        )
    }

    /**
     * Income-only rules accept INCOME/CREDIT, and payroll credits mis-tagged as INVESTMENT
     * (e.g. HDFC "ACH C-SAL" salary SMS matching the generic "ach" investment keyword).
     */
    fun passesMatchTypeFilter(
        transaction: TransactionEntity,
        smsText: String?,
        input: QuickKeywordRuleCompiler.QuickKeywordRuleInput,
    ): Boolean {
        return when (input.matchType) {
            null -> true
            TransactionType.INCOME -> passesIncomeTypeFilter(transaction, smsText)
            TransactionType.EXPENSE -> matchesExpenseFamily(transaction, input.matchExpenseChannel)
            TransactionType.CREDIT -> transaction.transactionType == TransactionType.CREDIT
            TransactionType.TRANSFER -> {
                transaction.transactionType == TransactionType.TRANSFER &&
                    (input.matchTransferKind == null ||
                        transaction.transferKind == input.matchTransferKind)
            }
            TransactionType.INVESTMENT -> transaction.transactionType == TransactionType.INVESTMENT
        }
    }

    private fun matchesExpenseFamily(
        transaction: TransactionEntity,
        channel: QuickKeywordExpenseChannel?,
    ): Boolean = when (channel) {
        QuickKeywordExpenseChannel.CREDIT_CARD -> transaction.transactionType == TransactionType.CREDIT
        QuickKeywordExpenseChannel.ACCOUNT,
        QuickKeywordExpenseChannel.CASH,
        -> transaction.transactionType == TransactionType.EXPENSE
        null -> transaction.transactionType == TransactionType.EXPENSE ||
            transaction.transactionType == TransactionType.CREDIT
    }

    fun matchTypeDescription(input: QuickKeywordRuleCompiler.QuickKeywordRuleInput): String {
        return when (input.matchType) {
            null -> "any type"
            TransactionType.INCOME -> "income or credit"
            TransactionType.EXPENSE -> when (input.matchExpenseChannel) {
                QuickKeywordExpenseChannel.CREDIT_CARD -> "credit card"
                QuickKeywordExpenseChannel.ACCOUNT -> "account expense"
                QuickKeywordExpenseChannel.CASH -> "cash expense"
                null -> "expense or credit"
            }
            TransactionType.CREDIT -> "credit card"
            TransactionType.TRANSFER -> when (input.matchTransferKind) {
                TransferKind.SELF_TRANSFER -> "self transfer"
                TransferKind.OTHERS_TRANSFER -> "transfer to others"
                else -> "transfer"
            }
            TransactionType.INVESTMENT -> "investment"
        }
    }

    fun passesIncomeTypeFilter(
        transaction: TransactionEntity,
        smsText: String?,
    ): Boolean {
        when (transaction.transactionType) {
            TransactionType.INCOME, TransactionType.CREDIT -> return true
            TransactionType.INVESTMENT -> {
                val raw = smsText?.takeIf { it.isNotBlank() } ?: transaction.smsBody.orEmpty()
                return PayrollCreditDetector.isPayrollCreditMessage(raw)
            }
            else -> return false
        }
    }

    fun wouldChangeLabels(
        transaction: TransactionEntity,
        input: QuickKeywordRuleInput,
    ): Boolean = hasPendingOverwrites(transaction, input)

    fun applyOverwrites(
        transaction: TransactionEntity,
        input: QuickKeywordRuleInput,
    ): TransactionEntity {
        var updated = transaction
        if (input.overwriteMerchant &&
            !shouldPreserveParsedMerchant(transaction.smsBody, transaction.merchantName)
        ) {
            updated = updated.copy(merchantName = input.merchantLabel.trim())
        }
        if (input.overwriteCategory) {
            updated = updated.copy(category = input.categoryLabel.trim())
        }
        if (input.overwriteTransactionType) {
            input.resolvedOverwriteType()?.let { newType ->
                updated = updated.copy(transactionType = newType)
            }
        }
        if (input.overwriteTags && input.tags.isNotEmpty()) {
            val existing = parseTagsString(updated.tags)
            val merged = (existing + input.tags.map { it.trim() })
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            updated = updated.copy(tags = formatTagsString(merged))
        }
        return updated.copy(updatedAt = LocalDateTime.now())
    }

    /**
     * Keeps parser-extracted HDFC payees when a keyword rule matched on incidental
     * SMS text (account number, footer boilerplate) instead of the payee name.
     */
    fun shouldPreserveParsedMerchant(sms: String?, parsedMerchant: String): Boolean =
        HdfcExplicitPayee.shouldPreserveMerchant(sms, parsedMerchant)

    fun hasPendingOverwrites(
        transaction: TransactionEntity,
        input: QuickKeywordRuleInput,
    ): Boolean {
        val patched = applyOverwrites(transaction, input)
        if (input.overwriteMerchant &&
            !transaction.merchantName.equals(patched.merchantName, ignoreCase = true)
        ) {
            return true
        }
        if (input.overwriteCategory &&
            !transaction.category.equals(patched.category, ignoreCase = true)
        ) {
            return true
        }
        if (input.overwriteTransactionType &&
            transaction.transactionType != patched.transactionType
        ) {
            return true
        }
        if (input.overwriteTags && input.tags.isNotEmpty()) {
            val before = parseTagsString(transaction.tags).map { it.lowercase() }.toSet()
            val after = parseTagsString(patched.tags).map { it.lowercase() }.toSet()
            if (after != before) return true
        }
        return false
    }

    fun humanEffectSummary(input: QuickKeywordRuleInput): String {
        val overwrites = buildList {
            if (input.overwriteMerchant) add("merchant")
            if (input.overwriteCategory) add("category")
            if (input.overwriteTransactionType) {
                input.resolvedOverwriteType()?.let { add("type→${it.name.lowercase()}") }
            }
            if (input.overwriteTags && input.tags.isNotEmpty()) {
                add("tags→${input.tags.joinToString(", ")}")
            }
        }.joinToString(", ")
        val textLine = "Text (${matchFieldDescription(input.matchField)}): " +
            textMatchModeDescription(input.textMatchMode)
        val matchLine = when {
            input.overwriteTransactionType -> "Keyword match only (fixes wrong types)"
            input.matchType != null -> "Type: ${matchTypeDescription(input)} only"
            else -> "Any transaction type"
        }
        return "Overwrite: $overwrites. $textLine. $matchLine"
    }

    /**
     * Scans [pool] and returns match counters plus sample rejection reasons (logcat tag [QuickKeywordRule]).
     */
    fun scanPool(
        pool: List<TransactionEntity>,
        input: QuickKeywordRuleInput,
        logTag: String = LOG_TAG,
    ): QuickKeywordMatchStats {
        var keywordMatched = 0
        var wouldUpdate = 0
        var alreadyLabeled = 0
        var typeRejected = 0
        var noKeywordHit = 0
        var emptySearchText = 0
        val rejectionSamples = mutableListOf<String>()

        Log.d(
            logTag,
            buildString {
                append("scanPool start: pool=")
                append(pool.size)
                append(" keywords=")
                append(input.keywords)
                append(" mode=")
                append(input.textMatchMode)
                append(" matchField=")
                append(input.matchField)
                append(" typeFilter=")
                append(input.matchType?.name ?: "any")
                if (input.matchExpenseChannel != null) {
                    append(" expenseChannel=")
                    append(input.matchExpenseChannel)
                }
                append(" textMatch=")
                append(textMatchModeDescription(input.textMatchMode))
                append(" uncategorizedOnly=")
                append(input.applyUncategorizedOnly)
            },
        )

        pool.forEach { transaction ->
            if (transaction.isDeleted) return@forEach

            val smsBody = transaction.smsBody
            val diagnosis = diagnose(transaction, smsBody, input)

            when {
                !diagnosis.matches && diagnosis.reason.startsWith("Type is") -> {
                    typeRejected++
                    if (rejectionSamples.size < 8) {
                        rejectionSamples.add(
                            "id=${transaction.id} type=${transaction.transactionType.name}: ${diagnosis.reason}",
                        )
                    }
                }
                !diagnosis.matches && diagnosis.reason.contains("No text in") -> {
                    emptySearchText++
                    if (rejectionSamples.size < 8) {
                        rejectionSamples.add("id=${transaction.id}: ${diagnosis.reason}")
                    }
                }
                !diagnosis.matches -> {
                    noKeywordHit++
                    if (rejectionSamples.size < 8) {
                        val snippet = buildMatchText(transaction, smsBody, input.matchField)
                            .take(80)
                            .replace('\n', ' ')
                        rejectionSamples.add(
                            "id=${transaction.id} ${diagnosis.reason}; text=\"$snippet\"",
                        )
                    }
                }
                else -> {
                    keywordMatched++
                    val shouldUpdate = input.forceOverwriteExisting ||
                        hasPendingOverwrites(transaction, input)
                    if (shouldUpdate) {
                        wouldUpdate++
                    } else {
                        alreadyLabeled++
                        if (rejectionSamples.size < 8) {
                            rejectionSamples.add(
                                "id=${transaction.id}: keyword \"${diagnosis.matchedKeyword}\" but labels already match",
                            )
                        }
                    }
                }
            }
        }

        val stats = QuickKeywordMatchStats(
            poolSize = pool.size,
            keywordMatched = keywordMatched,
            wouldUpdate = wouldUpdate,
            alreadyLabeled = alreadyLabeled,
            typeRejected = typeRejected,
            noKeywordHit = noKeywordHit,
            emptySearchText = emptySearchText,
            rejectionSamples = rejectionSamples,
        )

        Log.i(
            logTag,
            "scanPool done: pool=${stats.poolSize} keywordMatched=${stats.keywordMatched} " +
                "wouldUpdate=${stats.wouldUpdate} alreadyLabeled=${stats.alreadyLabeled} " +
                "typeRejected=${stats.typeRejected} noKeywordHit=${stats.noKeywordHit} " +
                "emptySearch=${stats.emptySearchText}",
        )
        stats.rejectionSamples.forEach { sample ->
            Log.d(logTag, "scanPool sample: $sample")
        }

        return stats
    }
}
