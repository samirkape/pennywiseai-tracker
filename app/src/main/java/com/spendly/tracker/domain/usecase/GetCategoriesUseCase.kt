package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    fun execute(): Flow<List<CategoryEntity>> {
        return categoryRepository.getAllCategories()
    }
}