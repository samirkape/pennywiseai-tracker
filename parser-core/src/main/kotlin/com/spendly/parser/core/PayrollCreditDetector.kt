package com.spendly.parser.core

/**
 * Detects payroll / salary bank credits that mention ACH or NACH routing text.
 * Prevents false [TransactionType.INVESTMENT] classification from generic "ach"/"nach" keywords.
 */
object PayrollCreditDetector {

    fun isPayrollCreditMessage(message: String): Boolean {
        val lower = message.lowercase()
        val inbound = lower.contains("deposited") ||
            lower.contains("credited") ||
            lower.contains("received")
        if (!inbound) return false
        return lower.contains("c-sal") ||
            lower.contains("salary") ||
            lower.contains("payroll") ||
            lower.contains("sal-") ||
            lower.contains("neft cr") ||
            lower.contains("rtgs cr")
    }
}
