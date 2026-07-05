package com.spendly.tracker.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

/** One historical transaction eligible for merchant rename review. */
data class TransactionRenameCandidate(
    val transactionId: Long,
    val currentMerchantName: String,
    val similarityScore: Double,
    val amount: BigDecimal,
    val currency: String,
    val dateTime: LocalDateTime,
    val category: String,
)
