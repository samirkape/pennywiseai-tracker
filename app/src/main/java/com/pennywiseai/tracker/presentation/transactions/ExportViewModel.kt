package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import com.pennywiseai.tracker.data.export.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExporter: CsvExporter
) : ViewModel() {

    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> = flow {
        val additionalCategories: Map<Long, List<String>> = transactions
            .filter { it.tags.isNotBlank() }
            .associate { tx -> tx.id to tx.tags.split(",").filter { it.isNotBlank() } }

        csvExporter.exportTransactions(transactions, fileName, additionalCategories)
            .collect { emit(it) }
    }.flowOn(Dispatchers.IO)
}
