package com.pennywiseai.tracker.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()
    
    // Categories list
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Dialog states
    private val _showAddEditDialog = MutableStateFlow(false)
    val showAddEditDialog: StateFlow<Boolean> = _showAddEditDialog.asStateFlow()
    
    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    val editingCategory: StateFlow<CategoryEntity?> = _editingCategory.asStateFlow()
    
    // Snackbar message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
    
    // Delete-with-replacement dialog state
    private val _pendingDeleteCategory = MutableStateFlow<CategoryEntity?>(null)
    val pendingDeleteCategory: StateFlow<CategoryEntity?> = _pendingDeleteCategory.asStateFlow()

    fun showAddDialog() {
        _editingCategory.value = null
        _showAddEditDialog.value = true
    }
    
    fun showEditDialog(category: CategoryEntity) {
        _editingCategory.value = category
        _showAddEditDialog.value = true
    }
    
    fun hideDialog() {
        _showAddEditDialog.value = false
        _editingCategory.value = null
    }
    
    fun saveCategory(
        name: String,
        color: String,
        isIncome: Boolean,
        icon: String,
    ) {
        viewModelScope.launch {
            try {
                val editingCat = _editingCategory.value
                
                if (editingCat != null) {
                    // Update existing category
                    categoryRepository.updateCategory(
                        editingCat.copy(
                            // Keep core system category attributes stable; only icon is user-editable.
                            name = if (editingCat.isSystem) editingCat.name else name,
                            color = if (editingCat.isSystem) editingCat.color else color,
                            isIncome = if (editingCat.isSystem) editingCat.isIncome else isIncome,
                            icon = icon,
                        )
                    )
                    _snackbarMessage.value = "Category updated successfully"
                } else {
                    // Check if category already exists
                    if (categoryRepository.categoryExists(name)) {
                        _snackbarMessage.value = "Category '$name' already exists"
                        return@launch
                    }
                    
                    // Create new category
                    categoryRepository.createCategory(
                        name = name,
                        color = color,
                        isIncome = isIncome,
                        icon = icon,
                    )
                    _snackbarMessage.value = "Category created successfully"
                }
                
                hideDialog()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error saving category: ${e.message}"
            }
        }
    }
    
    fun requestDeleteCategory(category: CategoryEntity) {
        if (category.isSystem) {
            _snackbarMessage.value = "System categories cannot be deleted"
            return
        }
        _pendingDeleteCategory.value = category
    }

    fun cancelDeleteRequest() {
        _pendingDeleteCategory.value = null
    }

    fun deleteCategory(category: CategoryEntity, replacementCategory: CategoryEntity? = null) {
        if (category.isSystem) {
            _snackbarMessage.value = "System categories cannot be deleted"
            return
        }
        _pendingDeleteCategory.value = null

        viewModelScope.launch {
            try {
                if (replacementCategory != null) {
                    transactionRepository.reassignTransactionsByCategory(
                        category.name, replacementCategory.name
                    )
                }
                val deleted = categoryRepository.deleteCategory(category.id)
                if (deleted) {
                    _snackbarMessage.value = if (replacementCategory != null) {
                        "Category deleted, transactions moved to '${replacementCategory.name}'"
                    } else {
                        "Category deleted successfully"
                    }
                } else {
                    _snackbarMessage.value = "Cannot delete this category"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Error deleting category: ${e.message}"
            }
        }
    }
    
    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }
}

data class CategoriesUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)