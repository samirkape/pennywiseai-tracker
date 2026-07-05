package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.dao.CategoryDao
import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.ui.icons.CategoryIcons
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    
    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories()
    }
    
    fun getExpenseCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getExpenseCategories()
    }
    
    fun getIncomeCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getIncomeCategories()
    }
    
    suspend fun getCategoryById(categoryId: Long): CategoryEntity? {
        return categoryDao.getCategoryById(categoryId)
    }
    
    suspend fun getCategoryByName(categoryName: String): CategoryEntity? {
        return categoryDao.getCategoryByName(categoryName)
    }
    
    suspend fun createCategory(
        name: String,
        color: String,
        isIncome: Boolean = false,
        icon: String = CategoryIcons.DEFAULT_KEY,
    ): Long {
        val category = CategoryEntity(
            name = name,
            color = color,
            icon = icon,
            isSystem = false,
            isIncome = isIncome,
            displayOrder = 999
        )
        return categoryDao.insertCategory(category)
    }
    
    suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.updateCategory(
            category.copy(updatedAt = LocalDateTime.now())
        )
    }
    
    suspend fun deleteCategory(categoryId: Long): Boolean {
        // Only delete non-system categories
        val category = categoryDao.getCategoryById(categoryId)
        if (category != null && !category.isSystem) {
            categoryDao.deleteCategory(categoryId)
            return true
        }
        return false
    }
    
    suspend fun categoryExists(categoryName: String): Boolean {
        return categoryDao.categoryExists(categoryName)
    }
    
    suspend fun initializeDefaultCategories() {
        // Only initialize if no categories exist
        if (categoryDao.getCategoryCount() == 0) {
            val defaultCategories = CategoryIcons.defaultKeysByCategoryName.map { (name, iconKey) ->
                val (color, isIncome, displayOrder) = when (name) {
                    "Food & Dining" -> Triple("#FC8019", false, 1)
                    "Groceries" -> Triple("#5AC85A", false, 2)
                    "Transportation" -> Triple("#000000", false, 3)
                    "Shopping" -> Triple("#FF9900", false, 4)
                    "Bills & Utilities" -> Triple("#4CAF50", false, 5)
                    "Entertainment" -> Triple("#E50914", false, 6)
                    "Healthcare" -> Triple("#10847E", false, 7)
                    "Investments" -> Triple("#00D09C", false, 8)
                    "Banking" -> Triple("#004C8F", false, 9)
                    "Personal Care" -> Triple("#6A4C93", false, 10)
                    "Education" -> Triple("#673AB7", false, 11)
                    "Mobile" -> Triple("#2A3890", false, 12)
                    "Fitness" -> Triple("#FF3278", false, 13)
                    "Insurance" -> Triple("#0066CC", false, 14)
                    "Travel" -> Triple("#00BCD4", false, 15)
                    "Salary" -> Triple("#4CAF50", true, 16)
                    "Income" -> Triple("#4CAF50", true, 17)
                    "Others" -> Triple("#757575", false, 18)
                    "Credit Card Payment" -> Triple("#1976D2", false, 998)
                    "Tax" -> Triple("#795548", false, 997)
                    "Bank Charges" -> Triple("#9E9E9E", false, 996)
                    else -> Triple("#757575", false, 999)
                }
                CategoryEntity(
                    name = name,
                    color = color,
                    icon = iconKey,
                    isSystem = true,
                    isIncome = isIncome,
                    displayOrder = displayOrder,
                )
            }
            categoryDao.insertCategories(defaultCategories)
        }
    }
}