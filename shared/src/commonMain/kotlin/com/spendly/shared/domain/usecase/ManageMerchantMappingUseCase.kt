package com.spendly.shared.domain.usecase

import com.spendly.shared.data.local.entity.SharedMerchantMappingEntity
import com.spendly.shared.data.repository.SharedMerchantMappingRepository
import com.spendly.shared.data.util.currentTimeMillis

class ManageMerchantMappingUseCase(
    private val repository: SharedMerchantMappingRepository
) {
    suspend fun map(merchantName: String, category: String) {
        val now = currentTimeMillis()
        repository.upsert(
            SharedMerchantMappingEntity(
                merchantName = merchantName.trim(),
                category = category.trim(),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }
}
