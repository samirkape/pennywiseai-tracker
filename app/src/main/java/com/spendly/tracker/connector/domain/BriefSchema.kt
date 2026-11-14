package com.spendly.tracker.connector.domain

/**
 * The vocabulary shared by the spending brief.
 *
 * The central rule is that a transaction's *type* (what the money did) and its
 * *payment method* (how it moved) are two separate facts. A credit card purchase
 * is an `expense` paid with a `credit_card`; the credit card bill payment that
 * settles it is a `transfer`, never a second expense.
 */

/** Financial nature of a transaction. */
enum class FinancialType(val label: String) {
    /** Consumption: money left the user's pocket for goods or services. */
    EXPENSE("expense"),

    /** Money received that is not a refund, a loan movement or a transfer. */
    INCOME("income"),

    /** Money received back for a previous expense (refund, reimbursement, cashback). */
    REFUND("refund"),

    /** Movement between accounts. Never spending, never income. */
    TRANSFER("transfer"),

    /** Allocation to an investment vehicle. */
    INVESTMENT("investment"),

    /** Repayment of borrowed money, or a repayment received on money lent out. */
    LOAN_PAYMENT("loan_payment"),

    /** The principal of a loan moving: borrowed money in, or money lent out. */
    LOAN_DISBURSEMENT("loan_disbursement"),

    /** A cancelled or reversed transaction that undoes an earlier row. */
    REVERSAL("reversal")
}

/** How the money moved. */
enum class PaymentMethod(val label: String) {
    CASH("cash"),
    UPI("UPI"),
    DEBIT_CARD("debit_card"),
    CREDIT_CARD("credit_card"),
    BANK_TRANSFER("bank_transfer"),
    OTHER("other"),

    /** The transaction does not carry enough signal to say how it was paid. */
    UNKNOWN("unknown")
}

/** Whether the spend was needed to keep the household running. */
enum class Essentiality(val label: String) {
    ESSENTIAL("essential"),
    DISCRETIONARY("discretionary"),

    /** A standing obligation: rent, EMI, card settlement, insurance premium. */
    FINANCIAL_COMMITMENT("financial_commitment"),
    INVESTMENT("investment"),

    /** Not enough context to say. */
    UNKNOWN("unknown")
}

/** How the amount behaves from month to month. */
enum class ExpenseBehaviour(val label: String) {
    /** The same amount shows up every period. */
    FIXED("fixed"),

    /** Moves with usage: groceries, fuel, eating out. */
    VARIABLE("variable"),

    /** Irregular: medical bills, repairs, one-off purchases. */
    OCCASIONAL("occasional"),

    /** Not enough context to say. */
    UNKNOWN("unknown")
}

/** Where a refund came from. */
enum class RefundKind(val label: String) {
    /** An insurer paying back a healthcare bill. */
    HEALTHCARE_INSURANCE_REIMBURSEMENT("healthcare_insurance_reimbursement"),

    /** A merchant giving money back for a purchase. */
    MERCHANT_REFUND("merchant_refund"),

    /** Goods handed back to the seller. */
    PURCHASE_RETURN("purchase_return"),

    /** A card issuer crediting cashback. */
    CREDIT_CARD_CASHBACK("credit_card_cashback"),

    /** Money back that fits none of the above. */
    OTHER_REIMBURSEMENT("other_reimbursement")
}

/** Whether a refund could be tied back to the expense it reverses. */
enum class RefundMatchStatus(val label: String) {
    /** Linked to an original transaction in the reporting period. */
    MATCHED("matched"),

    /** No original could be resolved inside the reporting period. */
    UNMATCHED("unmatched")
}
