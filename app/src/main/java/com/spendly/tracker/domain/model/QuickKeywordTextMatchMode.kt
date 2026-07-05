package com.spendly.tracker.domain.model

import com.spendly.tracker.domain.model.rule.ConditionOperator

/**
 * How keyword list values are matched against searchable transaction text.
 */
enum class QuickKeywordTextMatchMode {
    /** Case-insensitive substring: any keyword appears in the text. */
    CONTAINS_ANY,

    /** Case-insensitive substring: every keyword appears in the text. */
    CONTAINS_ALL,

    /** Entire searchable text equals one keyword (ignore case, trimmed). */
    EQUALS_ONE_OF,

    /** Text starts with any keyword (ignore case). */
    STARTS_WITH_ANY,

    /** Text ends with any keyword (ignore case). */
    ENDS_WITH_ANY,

    /** None of the keywords appear as case-insensitive substrings. */
    NOT_CONTAINS_ANY,

    /** Any keyword is treated as a case-insensitive regex pattern. */
    REGEX_ANY,
    ;

    fun toConditionOperator(): ConditionOperator = when (this) {
        CONTAINS_ANY -> ConditionOperator.CONTAINS_ANY_KEYWORD
        CONTAINS_ALL -> ConditionOperator.CONTAINS_ALL_KEYWORDS
        EQUALS_ONE_OF -> ConditionOperator.EQUALS_ANY_KEYWORD
        STARTS_WITH_ANY -> ConditionOperator.STARTS_WITH_ANY_KEYWORD
        ENDS_WITH_ANY -> ConditionOperator.ENDS_WITH_ANY_KEYWORD
        NOT_CONTAINS_ANY -> ConditionOperator.NOT_CONTAINS_ANY_KEYWORD
        REGEX_ANY -> ConditionOperator.REGEX_ANY_KEYWORD
    }

    companion object {
        fun fromConditionOperator(operator: ConditionOperator): QuickKeywordTextMatchMode? =
            when (operator) {
                ConditionOperator.CONTAINS_ANY_KEYWORD -> CONTAINS_ANY
                ConditionOperator.CONTAINS_ALL_KEYWORDS -> CONTAINS_ALL
                ConditionOperator.EQUALS_ANY_KEYWORD -> EQUALS_ONE_OF
                ConditionOperator.STARTS_WITH_ANY_KEYWORD -> STARTS_WITH_ANY
                ConditionOperator.ENDS_WITH_ANY_KEYWORD -> ENDS_WITH_ANY
                ConditionOperator.NOT_CONTAINS_ANY_KEYWORD -> NOT_CONTAINS_ANY
                ConditionOperator.REGEX_ANY_KEYWORD -> REGEX_ANY
                else -> null
            }

        val DEFAULT = CONTAINS_ANY
    }
}
