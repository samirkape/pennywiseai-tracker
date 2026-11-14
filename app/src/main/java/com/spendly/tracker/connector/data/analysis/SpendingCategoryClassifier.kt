package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.Essentiality
import com.spendly.tracker.connector.domain.ExpenseBehaviour

/**
 * Maps a category to how essential it is and how its amount behaves.
 *
 * The category is the primary signal; the merchant is only consulted for the
 * cases where the category alone cannot tell. When neither says anything the
 * answer stays `unknown` rather than being guessed.
 */
object SpendingCategoryClassifier {

    /** Categories that keep the household running. */
    private val ESSENTIAL = setOf(
        "groceries", "bills & utilities", "bills and utilities", "utilities", "healthcare",
        "health", "medical", "transportation", "transport", "commute", "mobile", "phone",
        "personal care", "education", "fuel", "petrol", "gas", "childcare", "child care",
        "groceries & essentials", "household"
    )

    /** Standing obligations: the same amount comes out every period. */
    private val FINANCIAL_COMMITMENT = setOf(
        "credit card payment", "credit card bill payment", "loan repayment", "emi",
        "rent", "insurance", "tax", "taxes", "bank charges", "maintenance", "subscription"
    )

    /** Money put to work rather than consumed. */
    private val INVESTMENT = setOf(
        "investments", "investment", "mutual funds", "mutual fund", "sip", "stocks",
        "equity", "fd", "recurring deposit", "ppf", "provident fund", "epf", "nps",
        "gold bond", "savings"
    )

    /** Nice to have. */
    private val DISCRETIONARY = setOf(
        "food & dining", "food and dining", "shopping", "entertainment", "travel",
        "fitness", "gifts", "hobbies", "events", "nightlife", "vacation", "holiday"
    )

    /** Categories whose default amount moves with usage. */
    private val VARIABLE = setOf(
        "groceries", "food & dining", "food and dining", "shopping", "transportation",
        "transport", "commute", "personal care", "fitness", "travel", "entertainment",
        "education", "fuel", "petrol", "gas", "childcare", "child care"
    )

    /** Categories that only show up now and then. */
    private val OCCASIONAL = setOf(
        "healthcare", "health", "medical", "others", "other", "miscellaneous", "repair",
        "repairs", "maintenance", "gifts"
    )

    /** Categories that are a standing amount. */
    private val FIXED = setOf(
        "bills & utilities", "bills and utilities", "utilities", "mobile", "phone",
        "insurance", "credit card payment", "credit card bill payment", "rent",
        "loan repayment", "emi", "subscription", "maintenance"
    )

    /** How essential a category is. */
    fun essentiality(category: String?): Essentiality {
        val key = key(category)
        return when {
            key.isEmpty() -> Essentiality.UNKNOWN
            key in INVESTMENT -> Essentiality.INVESTMENT
            key in FINANCIAL_COMMITMENT -> Essentiality.FINANCIAL_COMMITMENT
            key in ESSENTIAL -> Essentiality.ESSENTIAL
            key in DISCRETIONARY -> Essentiality.DISCRETIONARY
            else -> Essentiality.UNKNOWN
        }
    }

    /**
     * How a category's amount usually behaves.
     *
     * @param occurrences how often the same merchant shows up in the period. A
     *   merchant that repeats is a standing amount even when its category is
     *   normally variable.
     */
    fun behaviour(category: String?, occurrences: Int = 1): ExpenseBehaviour {
        val key = key(category)
        if (occurrences >= RECURRING_OCCURRENCES) return ExpenseBehaviour.FIXED
        return when {
            key.isEmpty() -> ExpenseBehaviour.UNKNOWN
            key in FIXED -> ExpenseBehaviour.FIXED
            key in VARIABLE -> ExpenseBehaviour.VARIABLE
            key in OCCASIONAL -> ExpenseBehaviour.OCCASIONAL
            else -> ExpenseBehaviour.UNKNOWN
        }
    }

    /** Categories that are a standing obligation get a fixed behaviour. */
    fun behaviourForCommitment(essentiality: Essentiality): ExpenseBehaviour =
        if (essentiality == Essentiality.FINANCIAL_COMMITMENT) {
            ExpenseBehaviour.FIXED
        } else {
            ExpenseBehaviour.UNKNOWN
        }

    private fun key(category: String?): String =
        category?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: ""

    private const val RECURRING_OCCURRENCES = 3
}
