package com.spendly.tracker.data.mapper

import com.spendly.parser.core.ParsedTransaction
import com.spendly.parser.core.TransferKinds
import com.spendly.tracker.core.Constants
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.shared.domain.mapping.SharedCategoryMapping
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Maps ParsedTransaction from parser-core to TransactionEntity
 */
fun ParsedTransaction.toEntity(): TransactionEntity {
    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    )

    // Normalize merchant name to proper case
    val normalizedMerchant = merchant?.let { normalizeMerchantName(it) }

    // Map TransactionType from parser-core to database entity
    val entityType = when (type) {
        com.spendly.parser.core.TransactionType.INCOME -> TransactionType.INCOME
        com.spendly.parser.core.TransactionType.EXPENSE -> TransactionType.EXPENSE
        com.spendly.parser.core.TransactionType.CREDIT -> TransactionType.CREDIT
        com.spendly.parser.core.TransactionType.TRANSFER -> TransactionType.TRANSFER
        com.spendly.parser.core.TransactionType.INVESTMENT -> TransactionType.INVESTMENT
        com.spendly.parser.core.TransactionType.BALANCE_UPDATE -> TransactionType.EXPENSE
    }

    // Credit card bill payments always belong in the "Credit Card Payment" bucket
    // regardless of merchant-string heuristics.
    val resolvedCategory = if (transferKind == TransferKinds.CC_BILL_PAYMENT) {
        "Credit Card Payment"
    } else {
        determineCategory(merchant, entityType)
    }

    return TransactionEntity(
        id = 0, // Auto-generated
        amount = amount,
        merchantName = normalizedMerchant ?: "Unknown Merchant",
        category = resolvedCategory,
        transactionType = entityType,
        dateTime = dateTime,
        description = null,
        smsBody = smsBody,
        bankName = bankName,
        smsSender = sender,
        accountNumber = accountLast4,
        balanceAfter = balance,
        transactionHash = transactionHash?.takeIf { it.isNotBlank() } ?: generateTransactionId(),
        isRecurring = false, // Will be determined later
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        currency = currency,
        fromAccount = fromAccount,
        toAccount = toAccount,
        reference = reference,
        transferKind = transferKind
    )
}

/**
 * Normalizes merchant name to consistent format.
 * Converts all-caps words to proper case, but preserves short all-letter tokens
 * as acronyms (e.g. ICCL, HDFC, NSE stay uppercased).
 * Already mixed-case names are kept as-is.
 */
private fun normalizeMerchantName(name: String): String {
    val trimmed = name.trim()

    // If already mixed case, keep as-is
    if (trimmed != trimmed.uppercase()) return trimmed

    // All-uppercase: convert word by word, preserving short all-letter tokens as acronyms
    return trimmed.split(" ").joinToString(" ") { word ->
        when {
            word.isEmpty() -> word
            // Short all-letter tokens (≤5 chars) are likely acronyms — keep them uppercased
            word.length <= 5 && word.all { it.isLetter() } -> word
            else -> word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Determines the category based on merchant name and transaction type.
 * Delegates to SharedCategoryMapping (single source of truth).
 */
private fun determineCategory(merchant: String?, type: TransactionType): String {
    val merchantName = merchant ?: return "Others"
    return SharedCategoryMapping.determineCategory(merchantName, type.name)
}

/**
 * Extension to map parser-core TransactionType to database entity TransactionType
 */
fun com.spendly.parser.core.TransactionType.toEntityType(): TransactionType {
    return when (this) {
        com.spendly.parser.core.TransactionType.INCOME -> TransactionType.INCOME
        com.spendly.parser.core.TransactionType.EXPENSE -> TransactionType.EXPENSE
        com.spendly.parser.core.TransactionType.CREDIT -> TransactionType.CREDIT
        com.spendly.parser.core.TransactionType.TRANSFER -> TransactionType.TRANSFER
        com.spendly.parser.core.TransactionType.INVESTMENT -> TransactionType.INVESTMENT
        com.spendly.parser.core.TransactionType.BALANCE_UPDATE -> TransactionType.EXPENSE
    }
}