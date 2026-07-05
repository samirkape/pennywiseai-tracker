package com.spendly.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import com.spendly.tracker.data.database.PennyWiseDatabase
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.export.CsvExporter
import com.spendly.tracker.data.export.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExporter: CsvExporter,
    private val database: PennyWiseDatabase
) : ViewModel() {

    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> = flow {
        val additionalCategories: Map<Long, List<String>> = transactions
            .filter { it.tags.isNotBlank() }
            .associate { tx -> tx.id to tx.tags.split(",").filter { it.isNotBlank() } }

        val allReceipts = database.transactionReceiptDao().getAllReceipts()
        val transactionReceipts: Map<Long, List<String>> = allReceipts
            .groupBy { it.transactionId }
            .mapValues { (_, receipts) -> receipts.map { it.filePath } }

        csvExporter.exportTransactions(transactions, fileName, additionalCategories, transactionReceipts)
            .collect { emit(it) }
    }.flowOn(Dispatchers.IO)
}
