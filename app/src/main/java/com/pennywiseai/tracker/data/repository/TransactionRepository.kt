package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.BulkCategoryPreviewDaoRow
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.TransactionReceiptDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.domain.model.MerchantRenameMatch
import com.pennywiseai.tracker.domain.model.TransactionRenameCandidate
import com.pennywiseai.tracker.utils.MerchantRenameMatcher
import com.pennywiseai.tracker.data.database.entity.TransactionReceiptEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionSplitDao: TransactionSplitDao,
    private val transactionReceiptDao: TransactionReceiptDao
) {
    companion object {
        /** Dummy lower bound when SQL `applySince` flag is off (date predicate ignored). */
        private val EPOCH_NOT_BEFORE: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

        private const val RENAME_CANDIDATE_SQL_LIMIT = 500

        /**
         * Up to six LIKE tokens (padded with "") for [TransactionDao.getDistinctMerchantNamesForRenameCandidates].
         * Uses word splits from the original label (min length 2), longer words from the new label (min 4),
         * and an optional letters-only prefix of the original for SMS-style strings.
         */
        internal fun buildRenameSearchTokens(originalMerchant: String, newMerchantName: String): List<String> {
            fun sanitize(part: String): String = part.replace("%", "").replace("_", "").trim()

            val ordered = LinkedHashSet<String>()
            fun collectWords(source: String, minLen: Int) {
                for (part in source.trim().split(Regex("[^A-Za-z0-9]+"))) {
                    val t = sanitize(part)
                    if (t.length >= minLen) ordered.add(t)
                }
            }
            collectWords(originalMerchant, minLen = 2)
            collectWords(newMerchantName, minLen = 4)

            val letters = originalMerchant.lowercase().filter { it.isLetter() }
            if (letters.length >= 4) {
                val chunk = sanitize(letters.take(8))
                if (chunk.length >= 4) ordered.add(chunk)
            }
            if (ordered.isEmpty()) {
                val fb = sanitize(originalMerchant)
                if (fb.length >= 2) ordered.add(fb.take(16))
            }
            val list = ordered.take(6).toMutableList()
            while (list.size < 6) list.add("")
            return list
        }
    }

    /**
     * In-memory undo stack for bulk category-by-merchant updates, capped at 3 entries.
     * Each entry represents one bulk operation's affected (id, oldCategory) pairs.
     */
    private val bulkCategoryUndoStack = ArrayDeque<List<Pair<Long, String>>>()
    private val undoLock = Any()

    suspend fun captureBulkCategoryUndoSnapshot(
        merchantName: String,
        excludeId: Long,
        notBefore: LocalDateTime?,
    ): List<Pair<Long, String>> {
        val trimmed = merchantName.trim()
        if (trimmed.isEmpty()) return emptyList()
        val applySince = if (notBefore == null) 0 else 1
        val sinceCutoff = notBefore ?: EPOCH_NOT_BEFORE
        return transactionDao.getIdCategoryPairsForBulkCategoryUpdate(
            trimmed,
            excludeId,
            applySince,
            sinceCutoff,
        ).map { it.id to it.category }
    }

    suspend fun getBulkCategoryPreviewForMerchant(
        merchantName: String,
        excludeId: Long,
        notBefore: LocalDateTime?,
        limit: Int = 20,
    ): List<BulkCategoryPreviewDaoRow> {
        val trimmed = merchantName.trim()
        if (trimmed.isEmpty()) return emptyList()
        val applySince = if (notBefore == null) 0 else 1
        val sinceCutoff = notBefore ?: EPOCH_NOT_BEFORE
        return transactionDao.getBulkCategoryPreviewRows(
            trimmed,
            excludeId,
            applySince,
            sinceCutoff,
            limit,
        )
    }

    fun rememberBulkCategoryUndo(pairs: List<Pair<Long, String>>) {
        if (pairs.isEmpty()) return
        synchronized(undoLock) {
            bulkCategoryUndoStack.addFirst(pairs)
            while (bulkCategoryUndoStack.size > 3) bulkCategoryUndoStack.removeLast()
        }
    }

    suspend fun undoLastBulkCategoryUpdate(): Boolean {
        val pairs = synchronized(undoLock) { bulkCategoryUndoStack.removeFirstOrNull() } ?: return false
        for ((id, category) in pairs) {
            transactionDao.updateTransactionCategoryById(id, category, LocalDateTime.now())
        }
        return true
    }

    fun getAllTransactions(): Flow<List<TransactionEntity>> = 
        transactionDao.getAllTransactions()
    
    suspend fun getTransactionById(id: Long): TransactionEntity? = 
        transactionDao.getTransactionById(id)
    
    fun getTransactionsBetweenDates(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsBetweenDates(startDate, endDate)
    
    fun getTransactionsBetweenDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsBetweenDates(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        )

    /**
     * Gets transactions filtered at the database level for better performance.
     * Combines date range, currency, and transaction type filters to reduce memory usage.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @param currency Currency code to filter by (e.g., "INR", "USD")
     * @param transactionType Optional transaction type filter (null means all types)
     * @return Flow of filtered transactions
     */
    fun getTransactionsFiltered(
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        transactionType: TransactionType? = null
    ): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsFiltered(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59),
            currency,
            transactionType
        )
    
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsByType(type)
    
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsByCategory(category)
    
    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        transactionDao.searchTransactions(query)

    fun getAllCurrencies(): Flow<List<String>> =
        transactionDao.getAllCurrencies()

    fun getCurrenciesForPeriod(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>> =
        transactionDao.getCurrenciesForPeriod(startDate, endDate)
    
    fun getAllCategories(): Flow<List<String>> =
        transactionDao.getAllCategories()

    fun getTodayCategories(): Flow<List<String>> {
        val start = LocalDate.now().atStartOfDay()
        val end = LocalDate.now().atTime(23, 59, 59)
        return transactionDao.getCategoriesUsedBetweenDates(start, end)
    }

    /**
     * Categories previously used for this merchant (saved mapping, past transactions, tags).
     */
    suspend fun getSuggestedCategoriesForMerchant(
        merchantName: String,
        excludeTransactionId: Long,
        merchantMappingCategory: String? = null
    ): List<String> {
        val fromTransactions = transactionDao.getCategoriesForMerchant(
            merchantName = merchantName,
            excludeTransactionId = excludeTransactionId
        )
        return buildList {
            merchantMappingCategory?.let { add(it) }
            addAll(fromTransactions)
        }.distinct()
    }

    /**
     * Gets the top N categories by usage count (number of transactions).
     * Useful for showing user's most frequently used categories in notifications.
     *
     * @param limit Maximum number of categories to return (default: 3)
     * @return List of category names ordered by usage count (most used first)
     */
    suspend fun getTopCategoriesByUsage(limit: Int = 3): List<String> =
        transactionDao.getTopCategoriesByUsage(limit)

    fun getAllMerchants(): Flow<List<String>> =
        transactionDao.getAllMerchants()
    
    suspend fun getTotalAmountByTypeAndPeriod(
        type: TransactionType,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double? = transactionDao.getTotalAmountByTypeAndPeriod(type, startDate, endDate)
    
    suspend fun insertTransaction(transaction: TransactionEntity): Long = 
        transactionDao.insertTransaction(transaction)
    
    suspend fun insertTransactions(transactions: List<TransactionEntity>) = 
        transactionDao.insertTransactions(transactions)
    
    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.updateTransaction(transaction)

    suspend fun updateExcludedFromTracking(transactionId: Long, excluded: Boolean) =
        transactionDao.updateExcludedFromTracking(transactionId, excluded)
    
    suspend fun deleteTransaction(transaction: TransactionEntity, hardDelete: Boolean = false) {
        // Drop any inbound pointers so the linked counterpart doesn't keep a
        // dangling linked_transaction_id reference.
        transactionDao.clearLinksTo(transaction.id)
        if (hardDelete) {
            transactionDao.deleteTransaction(transaction)
        } else {
            transactionDao.softDeleteTransaction(transaction.id)
        }
    }

    suspend fun deleteTransactionById(id: Long, hardDelete: Boolean = false) {
        transactionDao.clearLinksTo(id)
        if (hardDelete) {
            transactionDao.deleteTransactionById(id)
        } else {
            transactionDao.softDeleteTransaction(id)
        }
    }

    suspend fun deleteAllTransactions() =
        transactionDao.deleteAllTransactions()

    // Helper method to check if transaction exists by hash
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
        transactionDao.getTransactionByHash(transactionHash)

    suspend fun getTransactionByReference(reference: String): TransactionEntity? =
        transactionDao.getTransactionByReference(reference)

    suspend fun getTransactionByAmountAndDate(
        amount: BigDecimal,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity> =
        transactionDao.getTransactionByAmountAndDate(amount, dateStart, dateEnd)
    
    suspend fun undoDeleteTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction.copy(isDeleted = false))
    }
    
    suspend fun updateCategoryForMerchant(
        merchantName: String,
        newCategory: String,
        notBefore: LocalDateTime? = null,
    ) {
        val applySince = if (notBefore == null) 0 else 1
        val sinceCutoff = notBefore ?: EPOCH_NOT_BEFORE
        transactionDao.updateCategoryForMerchant(
            merchantName,
            newCategory,
            LocalDateTime.now(),
            applySince,
            sinceCutoff,
        )
    }

    suspend fun bulkUpdateTypeAndTransferKindForMerchant(
        merchantName: String,
        type: TransactionType,
        transferKind: String?,
        excludeId: Long,
        notBefore: LocalDateTime?,
    ): Int {
        val trimmed = merchantName.trim()
        if (trimmed.isEmpty()) return 0
        val applySince = if (notBefore == null) 0 else 1
        val sinceCutoff = notBefore ?: EPOCH_NOT_BEFORE
        return transactionDao.bulkUpdateTypeAndTransferKindForMerchant(
            trimmed,
            type,
            transferKind,
            excludeId,
            LocalDateTime.now(),
            applySince,
            sinceCutoff,
        )
    }

    suspend fun updateMerchantNameForMerchant(oldMerchantName: String, newMerchantName: String) {
        transactionDao.updateMerchantNameForMerchant(oldMerchantName, newMerchantName, LocalDateTime.now())
    }

    suspend fun getOtherTransactionCountForMerchant(
        merchantName: String,
        excludeId: Long,
        notBefore: LocalDateTime? = null,
    ): Int {
        val trimmed = merchantName.trim()
        if (trimmed.isEmpty()) return 0
        val applySince = if (notBefore == null) 0 else 1
        val sinceCutoff = notBefore ?: EPOCH_NOT_BEFORE
        return transactionDao.getActiveTransactionCountForMerchant(
            trimmed,
            excludeId,
            applySince,
            sinceCutoff,
        )
    }

    suspend fun findSimilarTransactionsForRename(
        originalMerchant: String,
        newMerchantName: String,
        excludeTransactionId: Long,
    ): List<TransactionRenameCandidate> {
        val merchantMatches = findSimilarMerchantMatches(
            originalMerchant,
            newMerchantName,
            excludeTransactionId,
        )
        return merchantMatches.flatMap { match ->
            transactionDao.getActiveTransactionsForMerchant(
                match.sourceMerchant,
                excludeTransactionId,
            ).map { txn ->
                TransactionRenameCandidate(
                    transactionId = txn.id,
                    currentMerchantName = txn.merchantName,
                    similarityScore = match.similarityScore,
                    amount = txn.amount,
                    currency = txn.currency,
                    dateTime = txn.dateTime,
                    category = txn.category,
                )
            }
        }.sortedByDescending { it.dateTime }
    }

    suspend fun updateMerchantNameForTransaction(transactionId: Long, newMerchantName: String) {
        transactionDao.updateMerchantNameById(
            transactionId = transactionId,
            newMerchantName = newMerchantName,
            updatedAt = LocalDateTime.now(),
        )
    }

    private suspend fun findSimilarMerchantMatches(
        originalMerchant: String,
        newMerchantName: String,
        excludeTransactionId: Long,
    ): List<MerchantRenameMatch> {
        val newTrim = newMerchantName.trim()
        val tokens = buildRenameSearchTokens(originalMerchant, newMerchantName)
        val candidates = transactionDao.getDistinctMerchantNamesForRenameCandidates(
            excludeId = excludeTransactionId,
            newName = newTrim,
            t0 = tokens[0],
            t1 = tokens[1],
            t2 = tokens[2],
            t3 = tokens[3],
            t4 = tokens[4],
            t5 = tokens[5],
            resultLimit = RENAME_CANDIDATE_SQL_LIMIT,
        )
        val merchantDetails = candidates.map { merchant ->
            MerchantRenameMatcher.MerchantMatchDetails(
                merchantName = merchant,
                transactionCount = 1,
                sample = MerchantRenameMatcher.MerchantSample(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    dateTime = LocalDateTime.now(),
                    category = "",
                ),
            )
        }
        return MerchantRenameMatcher.findCandidates(
            originalMerchant = originalMerchant,
            newMerchantName = newMerchantName,
            merchantDetails = merchantDetails,
        )
    }

    suspend fun getDistinctMerchantNames(): List<String> {
        return transactionDao.getDistinctMerchantNames()
    }

    suspend fun getAllUsedTags(): List<String> {
        val flattened = transactionDao.getDistinctTagStrings()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val counts = flattened.groupingBy { it }.eachCount()
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key },
            )
            .map { it.key }
    }
    
    // Additional methods for Home screen
    data class MonthlyBreakdown(
        val total: BigDecimal,
        val income: BigDecimal,
        val expenses: BigDecimal
    )
    
    fun getCurrentMonthBreakdown(): Flow<MonthlyBreakdown> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1).atStartOfDay()
        val endDate = now.atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                val nonLoan = transactions.filter { it.loanId == null }
                val income = nonLoan
                    .filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                val expenses = nonLoan
                    .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                MonthlyBreakdown(
                    total = income - expenses,
                    income = income,
                    expenses = expenses
                )
            }
    }

    fun getCurrentMonthTotal(): Flow<BigDecimal> {
        return getCurrentMonthBreakdown().map { it.total }
    }
    
    fun getLastMonthBreakdown(): Flow<MonthlyBreakdown> {
        val now = LocalDate.now()
        val dayOfMonth = now.dayOfMonth
        val lastMonth = now.minusMonths(1)

        // Compare same period: if today is 10th, compare 1st-10th of last month
        val startDate = lastMonth.withDayOfMonth(1).atStartOfDay()
        val lastMonthMaxDay = min(dayOfMonth, lastMonth.lengthOfMonth())
        val endDate = lastMonth.withDayOfMonth(lastMonthMaxDay).atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                val nonLoan = transactions.filter { it.loanId == null }
                val income = nonLoan
                    .filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                val expenses = nonLoan
                    .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                MonthlyBreakdown(
                    total = income - expenses,
                    income = income,
                    expenses = expenses
                )
            }
    }

    fun getLastMonthTotal(): Flow<BigDecimal> {
        return getLastMonthBreakdown().map { it.total }
    }

    // Currency-grouped breakdown methods
    fun getCurrentMonthBreakdownByCurrency(): Flow<Map<String, MonthlyBreakdown>> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1).atStartOfDay()
        val endDate = now.atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                transactions.filter { it.loanId == null }.groupBy { it.currency }.mapValues { (_, currencyTransactions) ->
                    val income = currencyTransactions
                        .filter { it.transactionType == TransactionType.INCOME }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    val expenses = currencyTransactions
                        .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    MonthlyBreakdown(
                        total = income - expenses,
                        income = income,
                        expenses = expenses
                    )
                }
            }
    }

    fun getLastMonthBreakdownByCurrency(): Flow<Map<String, MonthlyBreakdown>> {
        val now = LocalDate.now()
        val dayOfMonth = now.dayOfMonth
        val lastMonth = now.minusMonths(1)

        // Compare same period: if today is 10th, compare 1st-10th of last month
        val startDate = lastMonth.withDayOfMonth(1).atStartOfDay()
        val lastMonthMaxDay = min(dayOfMonth, lastMonth.lengthOfMonth())
        val endDate = lastMonth.withDayOfMonth(lastMonthMaxDay).atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                transactions.filter { it.loanId == null }.groupBy { it.currency }.mapValues { (_, currencyTransactions) ->
                    val income = currencyTransactions
                        .filter { it.transactionType == TransactionType.INCOME }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    val expenses = currencyTransactions
                        .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    MonthlyBreakdown(
                        total = income - expenses,
                        income = income,
                        expenses = expenses
                    )
                }
            }
    }
    
    fun getRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
            .map { transactions ->
                transactions.take(limit)
            }
    }

    fun getTransactionsByAccount(bankName: String, accountLast4: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccount(bankName, accountLast4)
    }
    
    fun getTransactionsByAccountAndDateRange(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccountAndDateRange(bankName, accountLast4, startDate, endDate)
    }

    // Methods for batch rule application
    suspend fun getAllTransactionsList(): List<TransactionEntity> {
        // Get all non-deleted transactions as a list (not Flow) for batch processing
        // Use a large date range to get all transactions
        val startDate = LocalDateTime.of(2000, 1, 1, 0, 0)
        val endDate = LocalDateTime.now().plusYears(10)
        return transactionDao.getTransactionsBetweenDatesList(startDate, endDate)
    }

    suspend fun getTransactionsBetweenDatesList(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
    ): List<TransactionEntity> =
        transactionDao.getTransactionsBetweenDatesList(startDate, endDate)

    suspend fun getUncategorizedTransactions(): List<TransactionEntity> {
        // Get all transactions without a category or with "Others" category
        return getAllTransactionsList().filter { transaction ->
            transaction.category.isNullOrBlank() || transaction.category == "Others"
        }
    }

    fun getUncategorizedTransactionSummary(): Flow<UncategorizedTransactionSummary> =
        combine(
            transactionDao.getUncategorizedTransactionCount(),
            transactionDao.getTrackedTransactionCount(),
        ) { uncategorizedCount, totalCount ->
            UncategorizedTransactionSummary(
                uncategorizedCount = uncategorizedCount,
                totalCount = totalCount,
            )
        }

    // ========== Transaction Split Methods ==========

    /**
     * Gets a transaction with its splits.
     */
    fun getTransactionWithSplits(transactionId: Long): Flow<TransactionWithSplits?> =
        transactionSplitDao.getTransactionWithSplits(transactionId)

    /**
     * Gets transactions with their splits for a date range and currency.
     * Useful for analytics that need to consider split amounts by category.
     */
    fun getTransactionsWithSplitsFiltered(
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String
    ): Flow<List<TransactionWithSplits>> =
        transactionSplitDao.getTransactionsWithSplitsFiltered(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59),
            currency
        )

    /**
     * Gets transactions with their splits for a date range across all currencies.
     * Used for unified currency mode where all currencies are loaded and converted.
     */
    fun getTransactionsWithSplitsFiltered(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionWithSplits>> =
        transactionSplitDao.getTransactionsWithSplitsAllCurrencies(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        )

    /**
     * Gets transactions with splits, including excluded rows, for analytics aggregates.
     */
    fun getTransactionsWithSplitsFilteredIncludingExcluded(
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String
    ): Flow<List<TransactionWithSplits>> =
        transactionSplitDao.getTransactionsWithSplitsFilteredIncludingExcluded(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59),
            currency
        )

    /**
     * Gets transactions with splits across all currencies, including excluded rows.
     */
    fun getTransactionsWithSplitsAllCurrenciesIncludingExcluded(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionWithSplits>> =
        transactionSplitDao.getTransactionsWithSplitsAllCurrenciesIncludingExcluded(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        )

    /**
     * Gets a transaction with its splits synchronously.
     */
    suspend fun getTransactionWithSplitsSync(transactionId: Long): TransactionWithSplits? =
        transactionSplitDao.getTransactionWithSplitsSync(transactionId)

    /**
     * Gets splits for a specific transaction.
     */
    fun getSplitsForTransaction(transactionId: Long): Flow<List<TransactionSplitEntity>> =
        transactionSplitDao.getSplitsForTransaction(transactionId)

    /**
     * Checks if a transaction has splits.
     */
    suspend fun hasSplits(transactionId: Long): Boolean =
        transactionSplitDao.hasSplits(transactionId)

    /**
     * Saves splits for a transaction, replacing any existing splits.
     */
    suspend fun saveSplits(transactionId: Long, splits: List<TransactionSplitEntity>) {
        // Delete existing splits
        transactionSplitDao.deleteSplitsForTransaction(transactionId)
        // Insert new splits
        if (splits.isNotEmpty()) {
            transactionSplitDao.insertSplits(splits.map { it.copy(transactionId = transactionId) })
        }
    }

    /**
     * Removes all splits from a transaction.
     */
    suspend fun removeSplits(transactionId: Long) {
        transactionSplitDao.deleteSplitsForTransaction(transactionId)
    }

    /**
     * Inserts a single split.
     */
    suspend fun insertSplit(split: TransactionSplitEntity): Long =
        transactionSplitDao.insertSplit(split)

    /**
     * Updates a split.
     */
    suspend fun updateSplit(split: TransactionSplitEntity) =
        transactionSplitDao.updateSplit(split)

    /**
     * Deletes a single split.
     */
    suspend fun deleteSplit(split: TransactionSplitEntity) =
        transactionSplitDao.deleteSplit(split)

    // ── Receipt methods ──

    suspend fun insertReceipts(transactionId: Long, filePaths: List<String>) {
        val entities = filePaths.map { path ->
            TransactionReceiptEntity(transactionId = transactionId, filePath = path)
        }
        transactionReceiptDao.insertReceipts(entities)
    }

    suspend fun getReceiptsForTransaction(transactionId: Long): List<TransactionReceiptEntity> =
        transactionReceiptDao.getReceiptsForTransaction(transactionId)

    suspend fun deleteReceipt(receiptId: Long) =
        transactionReceiptDao.deleteReceipt(receiptId)

    suspend fun deleteReceiptsForTransaction(transactionId: Long) =
        transactionReceiptDao.deleteReceiptsForTransaction(transactionId)

    fun getPendingSelfTransfers(): Flow<List<TransactionEntity>> =
        transactionDao.getPendingSelfTransfers()

    fun getPendingSelfTransferCount(): Flow<Int> =
        transactionDao.getPendingSelfTransferCount()

    suspend fun updateTransferKind(id: Long, transferKind: String) =
        transactionDao.updateTransferKind(id, transferKind, LocalDateTime.now())
}

data class UncategorizedTransactionSummary(
    val uncategorizedCount: Int,
    val totalCount: Int,
)
