package com.spendly.tracker.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class QuickCategorizeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uncategorizedTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val uncategorizedTransactions: StateFlow<List<TransactionEntity>> = _uncategorizedTransactions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadUncategorized()
    }

    private fun loadUncategorized() {
        viewModelScope.launch {
            _isLoading.value = true
            val transactions = transactionRepository.getUncategorizedTransactions()
            _uncategorizedTransactions.value = transactions
            _isLoading.value = false
        }
    }

    fun categorizeTransaction(transactionId: Long, category: String, bulkUpdate: Boolean = false) {
        viewModelScope.launch {
            val transaction = _uncategorizedTransactions.value.find { it.id == transactionId } ?: return@launch

            if (bulkUpdate) {
                transactionRepository.updateCategoryForMerchant(transaction.merchantName, category)
            } else {
                transactionRepository.updateTransaction(transaction.copy(category = category, updatedAt = LocalDateTime.now()))
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

