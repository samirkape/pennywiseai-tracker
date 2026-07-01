package com.spendly.tracker.domain.model

import com.spendly.tracker.domain.model.rule.TransactionField

/** Which transaction field(s) keyword rules search. */
enum class QuickKeywordMatchField {
    /** SMS, merchant, bank, sender, reference, description (default). */
    ALL_TEXT,

    /** Scanned / stored SMS body only. */
    SMS_TEXT,

    /** Parsed merchant name on the transaction. */
    MERCHANT,

    /** Description / narration field. */
    DESCRIPTION,

    /** Comma-separated tags on the transaction. */
    TAGS,
    ;

    fun toTransactionField(): TransactionField = when (this) {
        ALL_TEXT -> TransactionField.SEARCHABLE_TEXT
        SMS_TEXT -> TransactionField.SMS_TEXT
        MERCHANT -> TransactionField.MERCHANT
        DESCRIPTION -> TransactionField.NARRATION
        TAGS -> TransactionField.TAGS
    }

    companion object {
        val DEFAULT = ALL_TEXT
    }
}
