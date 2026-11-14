package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.Essentiality
import com.spendly.tracker.connector.domain.ExpenseBehaviour
import com.spendly.tracker.connector.domain.FinancialType
import com.spendly.tracker.connector.domain.PaymentMethod
import com.spendly.tracker.connector.domain.RefundKind
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType

/**
 * Works out what a transaction *is* and how it was paid for.
 *
 * The two questions are kept apart on purpose. A credit card purchase is an
 * expense paid with a credit card; the card bill payment that settles it is a
 * transfer and must never be counted as a second expense.
 *
 * Every rule here is deliberately conservative. When the evidence is thin the
 * answer is `unknown` rather than a confident guess.
 */
object TransactionClassifier {

    // ---- Payment method -----------------------------------------------------

    private val UPI_MARKERS = listOf(
        "upi", "paytm", "phonepe", "google pay", "googlepay", "gpay", "bhim",
        "@paytm", "@ybl", "@oksbi", "@ibl", "@axl", "@upi", "vpa", "upi intent"
    )

    private val CREDIT_CARD_MARKERS = listOf("credit card", "creditcard", "cc payment")

    private val DEBIT_CARD_MARKERS = listOf(
        "debit card", "debitcard", "debit txn", "debitcard txn", "debit card txn"
    )

    private val CASH_MARKERS = listOf("atm", "cash withdrawal", "cash wdl", "cash deposit")

    private val BANK_TRANSFER_MARKERS = listOf(
        "neft", "imps", "rtgs", "bank transfer", "fund transfer", "net banking",
        "wire transfer", "funds transfer"
    )

    private val WALLET_MARKERS = listOf("wallet", "money wallet")

    private val ATM_WORD = Regex("""\batm\b""")

    // ---- Refund wording -----------------------------------------------------

    private val REFUND_MARKERS = listOf(
        "refund", "reimbursement", "reimbursed", "cashback", "cash back", "chargeback",
        "money back", "moneyback", "purchase return", "goods returned", "amount returned"
    )

    private val RETURN_MARKERS = listOf("returned", "return against")

    private val REVERSAL_MARKERS = listOf("reversed", "reversal", "transaction reversed")

    private val HEALTHCARE_MARKERS = listOf(
        "health", "healthcare", "hospital", "clinic", "medical", "medic", "pharmacy",
        "insurance", "insur", "policy", "diagnostic", "dental", "physio", "optical"
    )

    // ---- Public API ---------------------------------------------------------

    /** What the money did, independent of how it was paid. */
    fun financialType(
        transaction: TransactionEntity,
        loanDirection: LoanDirection?
    ): FinancialType = when (transaction.transactionType) {
        TransactionType.EXPENSE, TransactionType.CREDIT ->
            if (transaction.loanId != null) {
                loanType(transaction, loanDirection)
            } else {
                FinancialType.EXPENSE
            }

        TransactionType.INCOME -> when {
            transaction.loanId != null -> loanType(transaction, loanDirection)
            isLinkedRefund(transaction) -> FinancialType.REFUND
            isReversal(transaction) -> FinancialType.REVERSAL
            looksLikeRefund(transaction) -> FinancialType.REFUND
            else -> FinancialType.INCOME
        }

        TransactionType.TRANSFER -> FinancialType.TRANSFER
        TransactionType.INVESTMENT -> FinancialType.INVESTMENT
    }

    /** How the money moved. */
    fun paymentMethod(transaction: TransactionEntity): PaymentMethod {
        if (transaction.transactionType == TransactionType.CREDIT) {
            return PaymentMethod.CREDIT_CARD
        }
        val text = searchableText(transaction)
        return when {
            containsAny(text, UPI_MARKERS) -> PaymentMethod.UPI
            containsAny(text, CREDIT_CARD_MARKERS) -> PaymentMethod.CREDIT_CARD
            containsAny(text, DEBIT_CARD_MARKERS) -> PaymentMethod.DEBIT_CARD
            containsAny(text, CASH_MARKERS) || ATM_WORD.containsMatchIn(text) -> PaymentMethod.CASH
            containsAny(text, BANK_TRANSFER_MARKERS) -> PaymentMethod.BANK_TRANSFER
            containsAny(text, WALLET_MARKERS) -> PaymentMethod.OTHER
            transaction.transactionType == TransactionType.TRANSFER -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.UNKNOWN
        }
    }

    /** True when the row is a refund that is already tied to its original. */
    fun isLinkedRefund(transaction: TransactionEntity): Boolean =
        transaction.transactionType == TransactionType.INCOME &&
            transaction.refundOfTransactionId != null

    /** True when the row looks like money coming back without a link to an original. */
    fun looksLikeRefund(transaction: TransactionEntity): Boolean {
        if (transaction.transactionType != TransactionType.INCOME) return false
        if (transaction.refundOfTransactionId != null) return false
        val text = searchableText(transaction)
        return containsAny(text, REFUND_MARKERS) || containsAny(text, RETURN_MARKERS)
    }

    /** True when the row undoes an earlier debit rather than refunding a purchase. */
    fun isReversal(transaction: TransactionEntity): Boolean {
        if (transaction.transactionType != TransactionType.INCOME) return false
        if (transaction.refundOfTransactionId != null) return false
        return containsAny(searchableText(transaction), REVERSAL_MARKERS)
    }

    /**
     * Kind of a refund that is already linked to its original.
     *
     * The original tells us what the money was for, which is more reliable than
     * the wording on the refund row itself.
     */
    fun kindOfLinkedRefund(
        refund: TransactionEntity,
        original: TransactionEntity
    ): RefundKind {
        val refundText = searchableText(refund)
        if (containsAny(refundText, CASHBACK_MARKERS)) return RefundKind.CREDIT_CARD_CASHBACK
        if (containsAny(searchableText(original), HEALTHCARE_MARKERS) ||
            containsAny(original.category.lowercase(), HEALTHCARE_MARKERS)
        ) {
            return RefundKind.HEALTHCARE_INSURANCE_REIMBURSEMENT
        }
        if (containsAny(refundText, RETURN_MARKERS)) return RefundKind.PURCHASE_RETURN
        return RefundKind.MERCHANT_REFUND
    }

    /**
     * Kind of a refund that could not be tied to an original.
     *
     * Only the refund row itself is read here — nothing is guessed about which
     * purchase it belongs to.
     */
    fun kindOfUnlinkedRefund(refund: TransactionEntity): RefundKind {
        val text = searchableText(refund)
        return when {
            containsAny(text, CASHBACK_MARKERS) -> RefundKind.CREDIT_CARD_CASHBACK
            containsAny(text, RETURN_MARKERS) -> RefundKind.PURCHASE_RETURN
            containsAny(text, HEALTHCARE_MARKERS) &&
                containsAny(text, REFUND_MARKERS) -> RefundKind.HEALTHCARE_INSURANCE_REIMBURSEMENT
            containsAny(text, HEALTHCARE_MARKERS) &&
                containsAny(text, HEALTHCARE_CONTEXT_MARKERS) -> RefundKind.HEALTHCARE_INSURANCE_REIMBURSEMENT
            else -> RefundKind.OTHER_REIMBURSEMENT
        }
    }

    /**
     * How essential a row is, and how its amount behaves.
     *
     * The category carries the decision. The merchant only breaks the tie for
     * categories the app does not know about.
     */
    fun essentialityOf(
        transaction: TransactionEntity,
        type: FinancialType
    ): Essentiality = when (type) {
        FinancialType.EXPENSE -> SpendingCategoryClassifier.essentiality(transaction.category)
        FinancialType.INVESTMENT -> Essentiality.INVESTMENT
        FinancialType.LOAN_PAYMENT -> Essentiality.FINANCIAL_COMMITMENT
        FinancialType.TRANSFER ->
            if (isCreditCardSettlement(transaction)) Essentiality.FINANCIAL_COMMITMENT
            else Essentiality.UNKNOWN
        else -> Essentiality.UNKNOWN
    }

    /** How the amount of a row behaves from month to month. */
    fun behaviourOf(
        transaction: TransactionEntity,
        type: FinancialType,
        merchantOccurrences: Int
    ): ExpenseBehaviour = when (type) {
        FinancialType.EXPENSE -> SpendingCategoryClassifier.behaviour(
            category = transaction.category,
            occurrences = merchantOccurrences
        )

        FinancialType.INVESTMENT ->
            if (merchantOccurrences >= RECURRING_OCCURRENCES) ExpenseBehaviour.FIXED
            else ExpenseBehaviour.VARIABLE

        FinancialType.LOAN_PAYMENT -> ExpenseBehaviour.FIXED
        FinancialType.TRANSFER ->
            if (isCreditCardSettlement(transaction)) ExpenseBehaviour.FIXED
            else ExpenseBehaviour.UNKNOWN

        else -> ExpenseBehaviour.UNKNOWN
    }

    /** True when the row settles a credit card bill rather than buying something. */
    fun isCreditCardSettlement(transaction: TransactionEntity): Boolean =
        transaction.transactionType == TransactionType.TRANSFER &&
            transaction.transferKind == com.spendly.tracker.data.database.entity.TransferKind.CC_BILL_PAYMENT

    // ---- Internals ----------------------------------------------------------

    private fun loanType(
        transaction: TransactionEntity,
        direction: LoanDirection?
    ): FinancialType {
        val moneyCameIn = transaction.transactionType == TransactionType.INCOME
        return when (direction) {
            // Money lent out: the lending is the disbursement, the repayment is a payment.
            LoanDirection.LENT ->
                if (moneyCameIn) FinancialType.LOAN_PAYMENT else FinancialType.LOAN_DISBURSEMENT

            // Money borrowed: the borrowing is the disbursement, the repayment is a payment.
            LoanDirection.BORROWED ->
                if (moneyCameIn) FinancialType.LOAN_DISBURSEMENT else FinancialType.LOAN_PAYMENT

            null -> if (moneyCameIn) FinancialType.LOAN_DISBURSEMENT else FinancialType.LOAN_PAYMENT
        }
    }

    /**
     * Text used for classification only. It never reaches the exported file.
     */
    private fun searchableText(transaction: TransactionEntity): String =
        listOfNotNull(
            transaction.merchantName,
            transaction.description,
            transaction.reference,
            transaction.smsBody
        ).joinToString(" ").lowercase()

    private fun containsAny(text: String, markers: List<String>): Boolean =
        markers.any { text.contains(it) }

    private val CASHBACK_MARKERS = listOf("cashback", "cash back", "cash-back")

    /** Words that turn a healthcare word into a reimbursement rather than a bill. */
    private val HEALTHCARE_CONTEXT_MARKERS = listOf(
        "claim", "settlement", "settled", "reimburs", "refund", "payout"
    )

    private const val RECURRING_OCCURRENCES = 3
}
