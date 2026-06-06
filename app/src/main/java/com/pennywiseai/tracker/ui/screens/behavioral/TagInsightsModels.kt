package com.pennywiseai.tracker.ui.screens.behavioral

import java.math.BigDecimal
import java.time.LocalDateTime

data class TagData(
    val name: String,
    val transactionCount: Int,
    val totalAmount: BigDecimal
)

data class CategoryOverlapData(
    val categoryA: String,
    val categoryB: String,
    val coOccurrenceCount: Int
)

data class MultiCategoryTransactionData(
    val transactionId: Long,
    val merchantName: String,
    val amount: BigDecimal,
    val dateTime: LocalDateTime,
    val categories: List<String>,
    val currency: String
)
