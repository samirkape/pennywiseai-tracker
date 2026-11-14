package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.FinancialType
import com.spendly.tracker.connector.domain.MerchantPrivacy
import com.spendly.tracker.connector.domain.RefundMatchStatus
import com.spendly.tracker.connector.domain.TransactionBrief
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransferKind

/**
 * Builds the per-transaction list for the shared brief.
 *
 * Every row of the period is listed — spending, income, refunds and the money that
 * only moved — so the model can check the totals in the brief against the rows it
 * was given. Rows the user excluded from tracking are never listed.
 */
object TransactionBriefMapper {

    fun from(
        transactions: List<TransactionEntity>,
        loanDirections: Map<Long, LoanDirection> = emptyMap()
    ): List<TransactionBrief> {
        val rows = transactions
            .filter { !it.isExcludedFromTracking && it.amount.signum() > 0 }
        val knownIds = rows.map { it.id }.toSet()
        val aliasRegistry = AccountAliasRegistry()
        val merchantOccurrences = rows
            .groupingBy { MerchantNormalizer.normalizeOrFallback(privacyMerchant(it)) }
            .eachCount()

        return rows
            .sortedByDescending { it.dateTime }
            .map { entity ->
                val type = TransactionClassifier.financialType(
                    entity,
                    entity.loanId?.let { loanDirections[it] }
                )
                val merchantOriginal = privacyMerchant(entity)
                val merchantNormalized = MerchantNormalizer.normalizeOrFallback(merchantOriginal)
                val isRefund = type == FinancialType.REFUND
                val originalId = entity.refundOfTransactionId
                val matchStatus = if (!isRefund) {
                    null
                } else if (originalId != null && originalId in knownIds) {
                    RefundMatchStatus.MATCHED
                } else {
                    RefundMatchStatus.UNMATCHED
                }
                val refundKind = if (!isRefund) {
                    null
                } else {
                    val original = originalId?.let { id -> rows.firstOrNull { it.id == id } }
                    if (original != null) {
                        TransactionClassifier.kindOfLinkedRefund(entity, original)
                    } else {
                        TransactionClassifier.kindOfUnlinkedRefund(entity)
                    }
                }

                TransactionBrief(
                    transactionId = TransactionBrief.idOf(entity.id),
                    date = entity.dateTime.toLocalDate(),
                    type = type,
                    amount = entity.amount,
                    currency = entity.currency,
                    category = entity.category.takeIf { it.isNotBlank() } ?: UNCATEGORISED,
                    subcategory = null,
                    merchantOriginal = merchantOriginal,
                    merchantNormalized = merchantNormalized,
                    paymentMethod = TransactionClassifier.paymentMethod(entity),
                    accountAlias = aliasRegistry.aliasFor(entity),
                    tag = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString(";").takeIf { it.isNotEmpty() },
                    linkedTransactionId = originalId?.let { TransactionBrief.idOf(it) },
                    notes = purposeOf(entity, type),
                    essentiality = TransactionClassifier.essentialityOf(entity, type),
                    behaviour = TransactionClassifier.behaviourOf(
                        entity,
                        type,
                        merchantOccurrences[merchantNormalized] ?: 1
                    ),
                    refundKind = refundKind,
                    refundMatchStatus = matchStatus
                )
            }
    }

    /**
     * Short purpose label for rows that are not consumption.
     *
     * It never contains anything private — only the kind of movement.
     */
    private fun purposeOf(entity: TransactionEntity, type: FinancialType): String? = when (type) {
        FinancialType.EXPENSE -> null
        FinancialType.INCOME -> "income"
        FinancialType.REFUND -> "refund"
        FinancialType.REVERSAL -> "reversal"
        FinancialType.INVESTMENT -> "investment"
        FinancialType.LOAN_PAYMENT -> "loan_payment"
        FinancialType.LOAN_DISBURSEMENT -> "loan_disbursement"
        FinancialType.TRANSFER -> when {
            entity.transferKind == TransferKind.CC_BILL_PAYMENT -> "credit_card_bill_payment"
            entity.transferKind == TransferKind.SELF_TRANSFER ||
                entity.transferKind == TransferKind.SELF_TRANSFER_PENDING -> "self_transfer"
            else -> "transfer_to_other"
        }
    }

    private fun privacyMerchant(entity: TransactionEntity): String =
        MerchantPrivacy.safeMerchantName(entity.merchantName).takeIf { it.isNotBlank() }
            ?: UNKNOWN_MERCHANT

    private const val UNCATEGORISED = "Uncategorised"
    private const val UNKNOWN_MERCHANT = "Unknown"
}
