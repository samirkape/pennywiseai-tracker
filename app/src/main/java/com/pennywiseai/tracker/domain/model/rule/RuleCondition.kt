package com.pennywiseai.tracker.domain.model.rule

import kotlinx.serialization.Serializable

@Serializable
data class RuleCondition(
    val field: TransactionField,
    val operator: ConditionOperator,
    val value: String,
    val logicalOperator: LogicalOperator = LogicalOperator.AND
) {
    fun validate(): Boolean {
        return value.isNotBlank() && when (field) {
            TransactionField.AMOUNT -> {
                when (operator) {
                    ConditionOperator.LESS_THAN,
                    ConditionOperator.GREATER_THAN,
                    ConditionOperator.LESS_THAN_OR_EQUAL,
                    ConditionOperator.GREATER_THAN_OR_EQUAL -> value.toBigDecimalOrNull() != null
                    else -> true
                }
            }
            TransactionField.TRANSACTION_HOUR -> {
                val hour = value.toIntOrNull()
                hour != null && hour in 0..23
            }
            TransactionField.TRANSACTION_TIME -> {
                value.matches(Regex("""\d{2}:\d{2}"""))
            }
            TransactionField.TRANSACTION_DAY_OF_WEEK -> {
                when (operator) {
                    ConditionOperator.IN, ConditionOperator.NOT_IN ->
                        value.split(",").all { it.trim().toIntOrNull()?.let { d -> d in 1..7 } == true }
                    else -> value.toIntOrNull()?.let { it in 1..7 } == true
                }
            }
            TransactionField.TRANSACTION_DAY_OF_MONTH -> {
                when (operator) {
                    ConditionOperator.IN, ConditionOperator.NOT_IN ->
                        value.split(",").all { it.trim().toIntOrNull()?.let { d -> d in 1..31 } == true }
                    else -> value.toIntOrNull()?.let { it in 1..31 } == true
                }
            }
            else -> true
        }
    }
}

@Serializable
enum class TransactionField {
    AMOUNT,                  // Transaction amount
    TYPE,                    // INCOME, EXPENSE, or TRANSFER
    CATEGORY,                // Transaction category
    MERCHANT,                // Merchant/vendor name
    NARRATION,               // Description/notes
    SMS_TEXT,                // Original SMS text
    BANK_NAME,               // Bank name from SMS
    TRANSACTION_HOUR,        // Hour (00-23)
    TRANSACTION_TIME,        // Time as HH:mm
    TRANSACTION_DAY_OF_WEEK, // 1=Monday .. 7=Sunday
    TRANSACTION_DAY_OF_MONTH,// 01-31
    TRANSACTION_DATE,        // yyyy-MM-dd
    SEARCHABLE_TEXT          // SMS + merchant + description (rule evaluation only)
}

@Serializable
enum class ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    REGEX_MATCHES,
    /** Value is keyword list encoded with [QuickKeywordRuleCompiler] storage delimiter; any case-insensitive substring match. */
    CONTAINS_ANY_KEYWORD,
    /** Encoded keyword list; every keyword must appear as a case-insensitive substring. */
    CONTAINS_ALL_KEYWORDS,
    /** Encoded keyword list; searchable text must equal one keyword (ignore case). */
    EQUALS_ANY_KEYWORD,
    /** Encoded keyword list; text starts with any keyword (ignore case). */
    STARTS_WITH_ANY_KEYWORD,
    /** Encoded keyword list; text ends with any keyword (ignore case). */
    ENDS_WITH_ANY_KEYWORD,
    /** Encoded keyword list; no keyword appears as a case-insensitive substring. */
    NOT_CONTAINS_ANY_KEYWORD,
    /** Encoded keyword list; any keyword matches as a case-insensitive regex. */
    REGEX_ANY_KEYWORD,
    IS_EMPTY,
    IS_NOT_EMPTY
}

@Serializable
enum class LogicalOperator {
    AND,
    OR
}