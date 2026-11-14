package com.spendly.tracker.connector.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * What the brief could and could not see.
 *
 * The point of this section is honesty: if a total does not add up, the gap is
 * printed rather than smoothed over, and every exclusion is named.
 */
data class DataQualityReport(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Days in the reporting period. */
    val periodDays: Int,
    /** Calendar months the period touches. */
    val periodMonths: Int,
    /** How many of those months are only partly covered. */
    val partialMonths: Int,
    /** Rows read for the period, before any exclusion. */
    val sourceTransactions: Int,
    /** Rows that contributed to the figures in this file. */
    val includedTransactions: Int,
    /** Rows dropped because the user excluded them from tracking. */
    val excludedTransactions: Int,
    /** Rows that look like a copy of another row in the same period. */
    val duplicateTransactions: Int,
    /** Included rows without a category. */
    val uncategorizedTransactions: Int,
    /** Refunds that could not be tied to an original inside the period. */
    val unmatchedRefundCount: Int,
    val unmatchedRefundAmount: BigDecimal,
    /** Rows whose payment method could not be worked out. */
    val unknownPaymentMethodCount: Int,
    /** Rows without a merchant name. */
    val missingMerchantCount: Int,
    /** Reversed transactions: reported apart because the original debit may be outside the period. */
    val reversalTransactions: Int = 0,
    val reversalAmount: BigDecimal = BigDecimal.ZERO,
    /** netExpense minus (grossExpense - refundsReceived). Zero means the totals agree. */
    val summaryToTransactionsDifference: BigDecimal,
    /** Net spending of all categories minus the net spending total. */
    val categoryToSummaryDifference: BigDecimal,
    /** Categories that exist but are not listed in the breakdown. */
    val categoriesOmitted: Int,
    /** Merchants that exist but are not listed in the top list. */
    val merchantsOmitted: Int,
    /** Rows that carry no amount and were therefore left out of the figures. */
    val zeroAmountTransactions: Int = 0,
    /** Month and category rows left out of the monthly category breakdown. */
    val monthlyCategoryRowsOmitted: Int = 0
)
