package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import com.pennywiseai.tracker.data.export.ExportResult
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExporter: CsvExporter,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> = flow {
        val ids = transactions.map { it.id }
        val categoryEntities = transactionRepository.getCategoriesForTransactions(ids)
        val additionalCategories = categoryEntities
            .groupBy { it.transactionId }
            .mapValues { (_, entities) -> entities.map { it.categoryName } }

        csvExporter.exportTransactions(transactions, fileName, additionalCategories)
            .collect { emit(it) }
    }.flowOn(Dispatchers.IO)
}
