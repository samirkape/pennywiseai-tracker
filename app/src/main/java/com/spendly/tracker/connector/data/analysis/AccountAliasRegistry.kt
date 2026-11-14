package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.data.database.entity.TransactionEntity

/**
 * Hands out privacy-safe names for the user's accounts.
 *
 * The real account number never leaves the device. Each distinct account gets a
 * stable label — `Account 1`, `Account 2` — so the model can still tell two
 * accounts apart and follow a transfer from one to the other.
 */
class AccountAliasRegistry {

    private val aliases = linkedMapOf<String, String>()

    /** Alias for the account a row belongs to, or null when it cannot be told. */
    fun aliasFor(transaction: TransactionEntity): String? {
        val key = accountKeyOf(transaction) ?: return null
        return aliases.getOrPut(key) { "Account ${aliases.size + 1}" }
    }

    /** Alias for a raw account identifier, for example a transfer destination. */
    fun aliasFor(rawAccount: String?, bankName: String? = null): String? {
        val key = accountKeyOf(rawAccount, bankName) ?: return null
        return aliases.getOrPut(key) { "Account ${aliases.size + 1}" }
    }

    /**
     * The key is built from the last four digits plus the bank, so the full
     * number is never held in memory here and can never be exported.
     */
    private fun accountKeyOf(transaction: TransactionEntity): String? {
        val raw = transaction.fromAccount?.trim()?.takeIf { it.isNotEmpty() }
            ?: transaction.accountNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: transaction.toAccount?.trim()?.takeIf { it.isNotEmpty() }
        return accountKeyOf(raw, transaction.bankName)
    }

    private fun accountKeyOf(rawAccount: String?, bankName: String?): String? {
        val account = rawAccount?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val bank = bankName?.trim()?.takeIf { it.isNotEmpty() } ?: ""
        return "$bank#${account.takeLast(4)}"
    }
}
