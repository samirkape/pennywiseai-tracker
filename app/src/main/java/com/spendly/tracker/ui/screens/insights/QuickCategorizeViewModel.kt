package com.spendly.tracker.ui.screens.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.repository.CategoryRepository
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class QuickCategorizeScope {
    /** Transactions from today that still need a category. */
    TODAY,

    /** Uncategorized transactions from before today. */
    HISTORICAL,

    /** Every uncategorized transaction. */
    ALL;

    companion object {
        fun fromRoute(value: String?): QuickCategorizeScope =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALL
    }
}

@HiltViewModel
class QuickCategorizeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val scope: QuickCategorizeScope =
        QuickCategorizeScope.fromRoute(savedStateHandle.get<String>("scope"))

    private val _uncategorizedTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val uncategorizedTransactions: StateFlow<List<TransactionEntity>> = _uncategorizedTransactions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allUsedTags = MutableStateFlow<List<String>>(emptyList())
    val allUsedTags: StateFlow<List<String>> = _allUsedTags.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = combine(
        categoryRepository.getExpenseCategories(),
        categoryRepository.getIncomeCategories()
    ) { expense, income ->
        (expense + income).sortedBy { it.displayOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadUncategorized()
        loadTags()
    }

    private fun loadUncategorized() {
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now()
            val transactions = when (scope) {
                QuickCategorizeScope.TODAY -> transactionRepository.getUncategorizedTransactions(
                    from = today,
                    untilExclusive = today.plusDays(1),
                )
                QuickCategorizeScope.HISTORICAL -> transactionRepository.getUncategorizedTransactions(
                    untilExclusive = today,
                )
                QuickCategorizeScope.ALL -> transactionRepository.getUncategorizedTransactions()
            }
            _uncategorizedTransactions.value = transactions
            _isLoading.value = false
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            _allUsedTags.value = transactionRepository.getAllUsedTags()
        }
    }

    fun categorizeTransaction(
        transactionId: Long,
        category: String,
        tags: List<String>,
        bulkUpdate: Boolean = false,
    ) {
        viewModelScope.launch {
            val transaction = _uncategorizedTransactions.value.find { it.id == transactionId } ?: return@launch
            val normalizedTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()

            if (bulkUpdate) {
                transactionRepository.updateCategoryForMerchant(transaction.merchantName, category)
            } else {
                transactionRepository.updateTransaction(
                    transaction.copy(
                        category = category,
                        tags = normalizedTags.joinToString(","),
                        categoryManuallyEdited = true,
                        updatedAt = LocalDateTime.now(),
                    )
                )
            }

            // Remove from local list
            _uncategorizedTransactions.value = _uncategorizedTransactions.value.filter {
                if (bulkUpdate) it.merchantName != transaction.merchantName else it.id != transactionId
            }
        }
    }

    fun skipTransaction(transactionId: Long) {
        // Just remove from local session list
        _uncategorizedTransactions.value = _uncategorizedTransactions.value.filter { it.id != transactionId }
    }
}
