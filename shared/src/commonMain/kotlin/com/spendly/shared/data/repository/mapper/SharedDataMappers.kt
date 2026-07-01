package com.spendly.shared.data.repository.mapper

import com.spendly.shared.data.local.entity.SharedCategoryEntity
import com.spendly.shared.data.local.entity.SharedTransactionEntity
import com.spendly.shared.data.model.SharedCategory
import com.spendly.shared.data.model.SharedTransaction
import com.spendly.shared.data.model.SharedTransactionType

internal fun SharedTransactionEntity.toDomain(): SharedTransaction =
    SharedTransaction(
        id = id,
        amountMinor = amountMinor,
        merchantName = merchantName,
        category = category,
        transactionType = SharedTransactionType.fromStorage(transactionType),
        occurredAtEpochMillis = occurredAtEpochMillis,
        note = note,
        currency = currency,
        transactionHash = transactionHash,
        reference = reference,
        bankName = bankName,
        accountLast4 = accountLast4,
        balanceAfterMinor = balanceAfterMinor,
        isDeleted = isDeleted,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

internal fun SharedTransaction.toEntity(): SharedTransactionEntity =
    SharedTransactionEntity(
        id = id,
        amountMinor = amountMinor,
        merchantName = merchantName,
        category = category,
        transactionType = transactionType.name,
        occurredAtEpochMillis = occurredAtEpochMillis,
        note = note,
        currency = currency,
        transactionHash = transactionHash,
        reference = reference,
        bankName = bankName,
        accountLast4 = accountLast4,
        balanceAfterMinor = balanceAfterMinor,
        isDeleted = isDeleted,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

internal fun SharedCategoryEntity.toDomain(): SharedCategory =
    SharedCategory(
        id = id,
        name = name,
        colorHex = colorHex,
        isSystem = isSystem,
        isIncome = isIncome,
        displayOrder = displayOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

internal fun SharedCategory.toEntity(): SharedCategoryEntity =
    SharedCategoryEntity(
        id = id,
        name = name,
        colorHex = colorHex,
        isSystem = isSystem,
        isIncome = isIncome,
        displayOrder = displayOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
