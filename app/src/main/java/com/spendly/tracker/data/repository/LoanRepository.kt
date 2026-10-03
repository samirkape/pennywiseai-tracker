package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.dao.LoanDao
import com.spendly.tracker.data.database.dao.TransactionDao
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.LoanStatus
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao
) {
    fun getActiveLoans(): Flow<List<LoanEntity>> = loanDao.getActiveLoans()

    fun getAllLoans(): Flow<List<LoanEntity>> = loanDao.getAllLoans()

    fun getActiveLoanCount(): Flow<Int> = loanDao.getActiveLoanCount()

    fun getTotalLentRemaining(): Flow<BigDecimal> = loanDao.getTotalLentRemaining()

    fun getTotalBorrowedRemaining(): Flow<BigDecimal> = loanDao.getTotalBorrowedRemaining()

    fun getTransactionsForLoan(loanId: Long): Flow<List<TransactionEntity>> =
        loanDao.getTransactionsForLoan(loanId)

    fun getRecentUnlinkedRepayments(direction: LoanDirection, limit: Int = 500): Flow<List<TransactionEntity>> {
        val repaymentType = if (direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        return loanDao.getRecentUnlinkedTransactionsByType(repaymentType, limit)
    }

    fun getUnlinkedLoanCategoryTransactions(): Flow<List<TransactionEntity>> =
        loanDao.getUnlinkedLoanCategoryTransactions()

    fun getRecentPersonNames(): Flow<List<String>> = loanDao.getRecentPersonNames()

    suspend fun getLoanById(loanId: Long): LoanEntity? = loanDao.getLoanById(loanId)

    suspend fun findActiveLoanForPerson(personName: String, direction: LoanDirection): LoanEntity? =
        loanDao.getActiveLoanByPersonAndDirection(personName, direction.name)

    suspend fun addToExistingLoan(loanId: Long, amount: BigDecimal, transactionId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                originalAmount = loan.originalAmount + amount,
                remainingAmount = loan.remainingAmount + amount,
                updatedAt = LocalDateTime.now()
            )
        )
        loanDao.linkTransaction(transactionId, loanId)
    }

    suspend fun createLoan(
        personName: String,
        direction: LoanDirection,
        amount: BigDecimal,
        currency: String,
        note: String?,
        sourceTransactionId: Long
    ): Long {
        val loan = LoanEntity(
            personName = personName,
            direction = direction,
            originalAmount = amount,
            remainingAmount = amount,
            currency = currency,
            note = note
        )
        val loanId = loanDao.insertLoan(loan)
        loanDao.linkTransaction(sourceTransactionId, loanId)
        return loanId
    }

    /**
     * Groups unlinked Loan-category transactions by person and currency and turns each group
     * into a loan. Returns the number of transactions that were linked.
     */
    suspend fun backfillFromLoanCategory(): Int {
        val unlinked = loanDao.getUnlinkedLoanCategoryTransactions().first()
        var linked = 0
        unlinked
            .groupBy { personKey(it) to it.currency }
            .forEach { (_, txs) ->
                linked += importTransactionsAsLoan(txs.first().merchantName.trim().ifBlank { "Unknown" }, txs)
            }
        return linked
    }

    /**
     * Links [transactions] to a loan for [personName]. Direction is inferred from the earliest
     * transaction (expense = lent, income = borrowed). Reuses an active loan for the same person
     * and direction, otherwise creates one; status is derived from what has been repaid.
     */
    suspend fun importTransactionsAsLoan(
        personName: String,
        transactions: List<TransactionEntity>,
        directionOverride: LoanDirection? = null
    ): Int {
        val txs = transactions.filter { it.loanId == null && !it.isDeleted }.sortedBy { it.dateTime }
        if (txs.isEmpty()) return 0
        val first = txs.first()
        val direction = directionOverride ?: inferDirection(txs)

        val loanId = loanDao.getActiveLoanByPersonAndDirection(personName, direction.name)?.id
            ?: loanDao.insertLoan(
                LoanEntity(
                    personName = personName,
                    direction = direction,
                    originalAmount = first.amount,
                    remainingAmount = first.amount,
                    currency = first.currency,
                    createdAt = first.dateTime
                )
            )
        txs.forEach { loanDao.linkTransaction(it.id, loanId) }
        recalculateRemaining(loanId)

        val loan = loanDao.getLoanById(loanId)
        if (loan?.status == LoanStatus.SETTLED && loan.settledAt != null) {
            loanDao.updateLoan(loan.copy(settledAt = txs.last().dateTime))
        }
        return txs.size
    }

    // The side with the larger total is the original amount; ties fall back to the earliest transaction.
    private fun inferDirection(sortedTxs: List<TransactionEntity>): LoanDirection {
        val expenses = sortedTxs.filter { it.transactionType == TransactionType.EXPENSE }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
        val income = sortedTxs.filter { it.transactionType == TransactionType.INCOME }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
        return when {
            expenses > income -> LoanDirection.LENT
            income > expenses -> LoanDirection.BORROWED
            sortedTxs.first().transactionType == TransactionType.INCOME -> LoanDirection.BORROWED
            else -> LoanDirection.LENT
        }
    }

    suspend fun switchDirection(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        val flipped = if (loan.direction == LoanDirection.LENT) LoanDirection.BORROWED else LoanDirection.LENT
        loanDao.updateLoan(loan.copy(direction = flipped, updatedAt = LocalDateTime.now()))
        recalculateRemaining(loanId)
    }

    private fun personKey(tx: TransactionEntity) = tx.merchantName.trim().lowercase()

    suspend fun recordRepayment(loanId: Long, transactionId: Long) {
        loanDao.linkTransaction(transactionId, loanId)
        recalculateRemaining(loanId)
    }

    suspend fun recordManualRepayment(
        loanId: Long,
        amount: BigDecimal,
        personName: String,
        currency: String
    ): Long {
        val loan = loanDao.getLoanById(loanId) ?: return -1
        val txType = if (loan.direction == LoanDirection.LENT)
            TransactionType.INCOME else TransactionType.EXPENSE
        val transaction = TransactionEntity(
            amount = amount,
            merchantName = personName,
            category = if (txType == TransactionType.INCOME) "Income" else "Others",
            transactionType = txType,
            dateTime = LocalDateTime.now(),
            description = "Loan repayment – $personName",
            transactionHash = "loan_repayment_${loanId}_${System.currentTimeMillis()}",
            currency = currency,
            loanId = loanId
        )
        val txId = transactionDao.insertTransaction(transaction)
        recalculateRemaining(loanId)
        return txId
    }

    suspend fun unlinkTransaction(transactionId: Long, loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        val transaction = transactionDao.getTransactionById(transactionId)

        // If this is an "original" transaction (the one that created/added to the lent amount),
        // subtract its value from originalAmount so the loan total stays accurate.
        val isOriginal = when (loan.direction) {
            LoanDirection.LENT -> transaction?.transactionType == TransactionType.EXPENSE
            LoanDirection.BORROWED -> transaction?.transactionType == TransactionType.INCOME
        }

        loanDao.unlinkTransaction(transactionId)

        if (isOriginal && transaction != null) {
            val newOriginalAmount = (loan.originalAmount - transaction.amount).coerceAtLeast(BigDecimal.ZERO)
            loanDao.updateLoan(
                loan.copy(
                    originalAmount = newOriginalAmount,
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        recalculateRemaining(loanId)
    }

    suspend fun updateOriginalAmount(loanId: Long, newAmount: BigDecimal) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                originalAmount = newAmount,
                updatedAt = LocalDateTime.now()
            )
        )
        recalculateRemaining(loanId)
    }

    suspend fun settleLoan(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                status = LoanStatus.SETTLED,
                remainingAmount = BigDecimal.ZERO,
                settledAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun reopenLoan(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        val repaymentType = if (loan.direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        val totalRepaid = loanDao.getTotalRepaidByType(loanId, repaymentType)
        val remaining = (loan.originalAmount - totalRepaid).coerceAtLeast(BigDecimal.ZERO)
        loanDao.updateLoan(
            loan.copy(
                status = LoanStatus.ACTIVE,
                remainingAmount = remaining,
                settledAt = null,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun deleteLoan(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.getTransactionsForLoan(loanId).first()
            .filter { it.transactionHash.startsWith("loan_repayment_${loanId}_") }
            .forEach { transactionDao.deleteTransactionById(it.id) }
        loanDao.unlinkAllTransactions(loanId)
        loanDao.deleteLoan(loan)
    }

    suspend fun reconcileActiveLoans() {
        loanDao.getActiveLoans().first().forEach { recalculateRemaining(it.id) }
    }

    private suspend fun recalculateRemaining(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        val linkedTransactions = loanDao.getTransactionsForLoan(loanId).first()
            .filter { !it.isDeleted }

        val repaymentType = if (loan.direction == LoanDirection.LENT) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }

        val originalTotal = linkedTransactions
            .filter { it.transactionType != repaymentType }
            .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }

        val totalRepaid = linkedTransactions
            .filter { it.transactionType == repaymentType }
            .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }

        val effectiveOriginalAmount = if (originalTotal > BigDecimal.ZERO) originalTotal else loan.originalAmount
        val remaining = (effectiveOriginalAmount - totalRepaid).coerceAtLeast(BigDecimal.ZERO)
        val newStatus = if (remaining <= BigDecimal.ZERO) LoanStatus.SETTLED else LoanStatus.ACTIVE

        loanDao.updateLoan(
            loan.copy(
                originalAmount = effectiveOriginalAmount,
                remainingAmount = remaining,
                status = newStatus,
                settledAt = if (newStatus == LoanStatus.SETTLED) loan.settledAt ?: LocalDateTime.now() else null,
                updatedAt = LocalDateTime.now()
            )
        )
    }
}
