package com.spendly.shared.domain.usecase

import com.spendly.shared.data.local.entity.SharedTransactionSplitEntity
import com.spendly.shared.data.repository.SharedSplitRepository
import com.spendly.shared.data.util.currentTimeMillis

class ManageSplitUseCase(
    private val repository: SharedSplitRepository
) {
    suspend fun replaceSplits(
        transactionId: Long,
        categoryAmounts: List<Pair<String, Long>>
    ) {
        val now = currentTimeMillis()
        repository.replaceSplits(
            transactionId = transactionId,
            splits = categoryAmounts.map { (category, amountMinor) ->
                SharedTransactionSplitEntity(
                    transactionId = transactionId,
                    category = category,
                    amountMinor = amountMinor,
                    createdAtEpochMillis = now
                )
            }
        )
    }
}
