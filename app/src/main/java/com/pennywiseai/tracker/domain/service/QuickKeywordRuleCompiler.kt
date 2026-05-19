package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransferKind
import com.pennywiseai.tracker.domain.model.QuickKeywordExpenseChannel
import com.pennywiseai.tracker.domain.model.QuickKeywordTextMatchMode
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import java.util.UUID

/**
 * Compiles simple comma-separated keyword rules into [TransactionRule] records
 * consumed by [RuleEngine]. Metadata is stored in [TransactionRule.description].
 */
object QuickKeywordRuleCompiler {

    const val MARKER = "@quick_keyword|"

    data class QuickKeywordRuleInput(
        val name: String,
        val keywords: List<String>,
        val textMatchMode: QuickKeywordTextMatchMode = QuickKeywordTextMatchMode.DEFAULT,
        /** Shown as the transaction merchant name in lists and detail. */
        val merchantLabel: String,
        /** Budget / analytics category bucket. */
        val categoryLabel: String,
        /** null = match any transaction type */
        val matchType: TransactionType? = null,
        val matchExpenseChannel: QuickKeywordExpenseChannel? = null,
        val matchTransferKind: String? = null,
        val syncNameWithLabel: Boolean = true,
        val runOnPastWhenSaved: Boolean = false,
        val applyUncategorizedOnly: Boolean = false,
        /** When true, set merchant on keyword-matched transactions (batch and new SMS). */
        val overwriteMerchant: Boolean = true,
        /** When true, set category on keyword-matched transactions. */
        val overwriteCategory: Boolean = true,
        /**
         * When true, set transaction type from the type filter (income → INCOME, expense → EXPENSE).
         * Matching uses keywords only so misclassified rows (e.g. salary as INVESTMENT) can be fixed.
         */
        val overwriteTransactionType: Boolean = false,
        /** When true, apply overwrites even if merchant/category already match the rule labels. */
        val forceOverwriteExisting: Boolean = false,
        val priority: Int = 75,
        val isActive: Boolean = true,
    ) {
        fun validate(): Boolean =
            name.isNotBlank() &&
                keywords.isNotEmpty() &&
                keywords.all { it.isNotBlank() } &&
                merchantLabel.isNotBlank() &&
                categoryLabel.isNotBlank() &&
                (overwriteMerchant || overwriteCategory || overwriteTransactionType) &&
                (!overwriteTransactionType || matchType != null)

        /** Resolved type written when [overwriteTransactionType] is enabled. */
        fun resolvedOverwriteType(): TransactionType? = matchType
    }

    /** Separates keywords in rule conditions and stored metadata (not user-visible). */
    const val KEYWORD_STORAGE_DELIMITER = '\u001e'

    /**
     * Parses user input: commas, newlines, or semicolons between keywords.
     * Double-quoted segments may contain those separators (e.g. `"foo, bar"`).
     * Any text is matched literally (regex-special characters are escaped).
     */
    fun parseKeywords(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            when {
                text[i].isWhitespace() -> i++
                text[i] == '"' -> {
                    val end = text.indexOf('"', i + 1)
                    if (end == -1) {
                        tokens.add(text.substring(i + 1).trim())
                        break
                    }
                    tokens.add(text.substring(i + 1, end).trim())
                    i = end + 1
                }
                else -> {
                    val end = text.indexOfAny(charArrayOf(',', ';', '\n'), i)
                    if (end == -1) {
                        tokens.add(text.substring(i).trim())
                        break
                    }
                    tokens.add(text.substring(i, end).trim())
                    i = end + 1
                }
            }
        }
        return tokens
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    fun encodeKeywordsForStorage(keywords: List<String>): String =
        keywords.joinToString(KEYWORD_STORAGE_DELIMITER.toString())

    fun decodeKeywordsFromStorage(segment: String): List<String> =
        if (segment.contains(KEYWORD_STORAGE_DELIMITER)) {
            segment.split(KEYWORD_STORAGE_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            // Legacy rules used comma-separated storage
            parseKeywords(segment)
        }

    fun isQuickKeywordRule(rule: TransactionRule): Boolean =
        rule.description?.startsWith(MARKER) == true ||
            rule.conditions.any { it.field == TransactionField.SEARCHABLE_TEXT }

    fun decompile(rule: TransactionRule): QuickKeywordRuleInput? {
        val metaLine = rule.description
            ?.lineSequence()
            ?.firstOrNull()
            ?.takeIf { it.startsWith(MARKER) }
            ?: return null
        val payload = metaLine.removePrefix(MARKER)
        val segments = payload.split('|')
        val keywordSegment = segments.firstOrNull().orEmpty()
        val keywords = decodeKeywordsFromStorage(keywordSegment)
        if (keywords.isEmpty()) return null

        var matchType: TransactionType? = null
        var matchExpenseChannel: QuickKeywordExpenseChannel? = null
        var matchTransferKind: String? = null
        var syncNameWithLabel = true
        var runOnPastWhenSaved = false
        var applyUncategorizedOnly = false
        var overwriteMerchant = true
        var overwriteCategory = true
        var overwriteTransactionType = false
        var forceOverwriteExisting = false
        var textMatchMode = QuickKeywordTextMatchMode.DEFAULT
        segments.drop(1).forEach { flag ->
            when {
                flag.startsWith("matchType=", ignoreCase = true) -> {
                    val value = flag.substringAfter('=')
                    matchType = runCatching { TransactionType.valueOf(value) }.getOrNull()
                }
                flag.startsWith("matchExpenseChannel=", ignoreCase = true) -> {
                    val value = flag.substringAfter('=')
                    matchExpenseChannel = runCatching {
                        QuickKeywordExpenseChannel.valueOf(value)
                    }.getOrNull()
                }
                flag.startsWith("matchTransferKind=", ignoreCase = true) -> {
                    val value = flag.substringAfter('=')
                    matchTransferKind = value.takeIf {
                        it == TransferKind.SELF_TRANSFER || it == TransferKind.OTHERS_TRANSFER
                    }
                }
                flag.equals("incomeOnly=true", ignoreCase = true) -> matchType = TransactionType.INCOME
                flag.equals("expenseOnly=true", ignoreCase = true) -> matchType = TransactionType.EXPENSE
                flag.equals("syncName=true", ignoreCase = true) -> syncNameWithLabel = true
                flag.equals("syncName=false", ignoreCase = true) -> syncNameWithLabel = false
                flag.equals("runOnSave=true", ignoreCase = true) -> runOnPastWhenSaved = true
                flag.equals("uncategorizedOnly=true", ignoreCase = true) -> applyUncategorizedOnly = true
                flag.equals("uncategorizedOnly=false", ignoreCase = true) -> applyUncategorizedOnly = false
                flag.equals("owMerchant=true", ignoreCase = true) -> overwriteMerchant = true
                flag.equals("owMerchant=false", ignoreCase = true) -> overwriteMerchant = false
                flag.equals("owCategory=true", ignoreCase = true) -> overwriteCategory = true
                flag.equals("owCategory=false", ignoreCase = true) -> overwriteCategory = false
                flag.equals("owType=true", ignoreCase = true) -> overwriteTransactionType = true
                flag.equals("owType=false", ignoreCase = true) -> overwriteTransactionType = false
                flag.equals("forceOw=true", ignoreCase = true) -> forceOverwriteExisting = true
                flag.equals("forceOw=false", ignoreCase = true) -> forceOverwriteExisting = false
                flag.startsWith("textMatchMode=", ignoreCase = true) -> {
                    val value = flag.substringAfter('=')
                    textMatchMode = runCatching {
                        QuickKeywordTextMatchMode.valueOf(value)
                    }.getOrDefault(QuickKeywordTextMatchMode.DEFAULT)
                }
            }
        }

        val keywordCondition = rule.conditions.firstOrNull {
            it.field == TransactionField.SEARCHABLE_TEXT
        }
        keywordCondition?.let { condition ->
            QuickKeywordTextMatchMode.fromConditionOperator(condition.operator)?.let {
                textMatchMode = it
            }
        }

        val merchantLabel = rule.actions.firstOrNull {
            it.field == TransactionField.MERCHANT && it.actionType == ActionType.SET
        }?.value ?: return null
        val categoryLabel = rule.actions.firstOrNull {
            it.field == TransactionField.CATEGORY && it.actionType == ActionType.SET
        }?.value ?: merchantLabel

        return QuickKeywordRuleInput(
            name = rule.name,
            keywords = keywords,
            textMatchMode = textMatchMode,
            merchantLabel = merchantLabel,
            categoryLabel = categoryLabel,
            matchType = matchType,
            matchExpenseChannel = matchExpenseChannel,
            matchTransferKind = matchTransferKind,
            syncNameWithLabel = syncNameWithLabel,
            runOnPastWhenSaved = runOnPastWhenSaved,
            applyUncategorizedOnly = applyUncategorizedOnly,
            overwriteMerchant = overwriteMerchant,
            overwriteCategory = overwriteCategory,
            overwriteTransactionType = overwriteTransactionType,
            forceOverwriteExisting = forceOverwriteExisting,
            priority = rule.priority,
            isActive = rule.isActive,
        )
    }

    fun compile(
        input: QuickKeywordRuleInput,
        existingId: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): TransactionRule {
        require(input.validate()) { "Invalid quick keyword rule input" }

        val conditions = buildList {
            if (!input.overwriteTransactionType) {
                when (input.matchType) {
                    TransactionType.INCOME -> add(
                        RuleCondition(
                            field = TransactionField.TYPE,
                            operator = ConditionOperator.IN,
                            value = "INCOME,CREDIT",
                        ),
                    )
                    TransactionType.EXPENSE -> {
                        val typeValue = when (input.matchExpenseChannel) {
                            QuickKeywordExpenseChannel.CREDIT_CARD -> "CREDIT"
                            QuickKeywordExpenseChannel.ACCOUNT,
                            QuickKeywordExpenseChannel.CASH,
                            null,
                            -> "EXPENSE,CREDIT"
                        }
                        add(
                            RuleCondition(
                                field = TransactionField.TYPE,
                                operator = if (typeValue.contains(',')) {
                                    ConditionOperator.IN
                                } else {
                                    ConditionOperator.EQUALS
                                },
                                value = typeValue,
                            ),
                        )
                    }
                    TransactionType.CREDIT -> add(
                        RuleCondition(
                            field = TransactionField.TYPE,
                            operator = ConditionOperator.EQUALS,
                            value = "CREDIT",
                        ),
                    )
                    TransactionType.TRANSFER -> add(
                        RuleCondition(
                            field = TransactionField.TYPE,
                            operator = ConditionOperator.EQUALS,
                            value = "TRANSFER",
                        ),
                    )
                    TransactionType.INVESTMENT -> add(
                        RuleCondition(
                            field = TransactionField.TYPE,
                            operator = ConditionOperator.EQUALS,
                            value = "INVESTMENT",
                        ),
                    )
                    null -> Unit
                }
            }
            add(
                RuleCondition(
                    field = TransactionField.SEARCHABLE_TEXT,
                    operator = input.textMatchMode.toConditionOperator(),
                    value = encodeKeywordsForStorage(input.keywords),
                )
            )
        }

        val actions = buildList {
            if (input.overwriteMerchant) {
                add(
                    RuleAction(
                        field = TransactionField.MERCHANT,
                        actionType = ActionType.SET,
                        value = input.merchantLabel.trim(),
                    ),
                )
            }
            if (input.overwriteCategory) {
                add(
                    RuleAction(
                        field = TransactionField.CATEGORY,
                        actionType = ActionType.SET,
                        value = input.categoryLabel.trim(),
                    ),
                )
            }
            if (input.overwriteTransactionType) {
                input.resolvedOverwriteType()?.let { type ->
                    add(
                        RuleAction(
                            field = TransactionField.TYPE,
                            actionType = ActionType.SET,
                            value = type.name,
                        ),
                    )
                }
            }
        }

        val metaDescription = buildMetaDescription(input)
        val humanDescription = buildString {
            append("Keywords: ${input.keywords.joinToString(", ")}\n")
            append("Match: ${QuickKeywordRuleMatcher.textMatchModeDescription(input.textMatchMode)}\n")
            append("Sets merchant=\"${input.merchantLabel.trim()}\" category=\"${input.categoryLabel.trim()}\"")
        }

        return TransactionRule(
            id = existingId ?: UUID.randomUUID().toString(),
            name = input.name.trim(),
            description = "$metaDescription\n$humanDescription",
            priority = input.priority,
            conditions = conditions,
            actions = actions,
            isActive = input.isActive,
            isSystemTemplate = false,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun buildMetaDescription(input: QuickKeywordRuleInput): String {
        val flags = buildList {
            if (input.textMatchMode != QuickKeywordTextMatchMode.DEFAULT) {
                add("textMatchMode=${input.textMatchMode.name}")
            }
            input.matchType?.let { add("matchType=${it.name}") }
            input.matchExpenseChannel?.let { add("matchExpenseChannel=${it.name}") }
            input.matchTransferKind?.let { add("matchTransferKind=$it") }
            if (input.syncNameWithLabel) add("syncName=true") else add("syncName=false")
            if (input.runOnPastWhenSaved) add("runOnSave=true")
            if (input.applyUncategorizedOnly) add("uncategorizedOnly=true")
            else add("uncategorizedOnly=false")
            if (input.overwriteMerchant) add("owMerchant=true") else add("owMerchant=false")
            if (input.overwriteCategory) add("owCategory=true") else add("owCategory=false")
            if (input.overwriteTransactionType) add("owType=true") else add("owType=false")
            if (input.forceOverwriteExisting) add("forceOw=true") else add("forceOw=false")
        }
        val keywordPart = encodeKeywordsForStorage(input.keywords)
        return "$MARKER$keywordPart|${flags.joinToString("|")}"
    }
}
