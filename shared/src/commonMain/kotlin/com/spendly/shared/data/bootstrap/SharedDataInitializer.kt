package com.spendly.shared.data.bootstrap

import com.spendly.shared.data.repository.SharedCategoryRepository
import com.spendly.shared.data.util.currentTimeMillis

class SharedDataInitializer(
    private val categoryRepository: SharedCategoryRepository
) {
    suspend fun seedDefaultCategoriesIfNeeded() {
        if (categoryRepository.countCategories() > 0) return
        categoryRepository.insertCategories(
            DefaultSharedCategories.create(
                nowEpochMillis = currentTimeMillis()
            )
        )
    }
}
