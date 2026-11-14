package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One row of the optional per-transaction list in the shared brief.
 *
 * Deliberately narrow: date, amount, type, category and merchant only. Descriptions,
 * SMS bodies, references, account numbers, bank names and UPI handles never enter
 * this shape.
 */
data class TransactionBrief(
    val date: LocalDate,
    val type: Type,
    val amount: BigDecimal,
    val category: String,
    val merchant: String
) {
    enum class Type(val label: String) {
        EXPENSE("expense"),
        CARD("card"),
        INCOME("income")
    }
}
