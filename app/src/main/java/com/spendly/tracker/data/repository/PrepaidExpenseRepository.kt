package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.dao.PrepaidAllocationDao
import com.spendly.tracker.data.database.dao.PrepaidAllocationWithCurrency
import com.spendly.tracker.data.database.dao.PrepaidExpenseDao
import com.spendly.tracker.data.database.dao.TransactionDao
import com.spendly.tracker.data.database.entity.PrepaidAllocationEntity
import com.spendly.tracker.data.database.entity.PrepaidAllocationStatus
import com.spendly.tracker.data.database.entity.PrepaidExpenseEntity
import com.spendly.tracker.data.database.entity.PrepaidExpenseStatus
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.domain.usecase.ComputePrepaidAllocationScheduleUseCase
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrepaidExpenseRepository @Inject constructor(
    private val prepaidExpenseDao: PrepaidExpenseDao,
    private val prepaidAllocationDao: PrepaidAllocationDao,
    private val transactionDao: TransactionDao,
    private val computeScheduleUseCase: ComputePrepaidAllocationScheduleUseCase
) {

    fun getActivePlans(): Flow<List<PrepaidExpenseEntity>> = prepaidExpenseDao.getActivePrepaidExpenses()

    fun getAllPlans(): Flow<List<PrepaidExpenseEntity>> = prepaidExpenseDao.getAllPrepaidExpenses()

    fun getPlanByIdFlow(id: Long): Flow<PrepaidExpenseEntity?> = prepaidExpenseDao.getByIdFlow(id)

    suspend fun getPlanById(id: Long): PrepaidExpenseEntity? = prepaidExpenseDao.getById(id)

    fun getAllocationsForPlan(planId: Long): Flow<List<PrepaidAllocationEntity>> =
        prepaidAllocationDao.getAllocationsForPlan(planId)

    suspend fun getUpcomingRenewals(withinDays: Int = 14): List<PrepaidExpenseEntity> {
        val today = LocalDate.now()
        return prepaidExpenseDao.getUpcomingRenewals(today, today.plusDays(withinDays.toLong()))
    }

    /** The join point consumed by Analytics/Budget/Home aggregation. */
    fun getAllocationsBetween(start: LocalDate, end: LocalDate): Flow<List<PrepaidAllocationEntity>> =
        prepaidAllocationDao.getAllocationsBetweenMonths(
            YearMonth.from(start).toString(), YearMonth.from(end).toString()
        )

    /** Same as [getAllocationsBetween] but with each row's plan currency attached, for multi-currency rollups. */
    fun getAllocationsWithCurrencyBetween(start: LocalDate, end: LocalDate): Flow<List<PrepaidAllocationWithCurrency>> =
        prepaidAllocationDao.getAllocationsWithCurrencyBetweenMonths(
            YearMonth.from(start).toString(), YearMonth.from(end).toString()
        )

    /**
     * Creates the plan: inserts the real payment as a TransactionEntity flagged
     * The payment counts as a normal EXPENSE transaction on its real date, same as any
     * other transaction — cash flow and all spend reports (Today, Analytics, Budgets,
     * Home) see the full amount on the day it happened. The generated
     * PrepaidAllocationEntity schedule is not counted as spend anywhere; it only powers
     * the plan's progress display and its monthly/yearly equivalent-cost figure shown on
     * the Subscriptions screen for planning purposes.
     */
    suspend fun createPlan(
        merchantName: String,
        category: String,
        totalAmount: BigDecimal,
        currency: String,
        startDate: LocalDate,
        totalMonths: Int,
        paymentDateTime: LocalDateTime,
        accountNumber: String?,
        notes: String?
    ): Long {
        val transactionId = transactionDao.insertTransaction(
            TransactionEntity(
                amount = totalAmount,
                merchantName = merchantName,
                category = category,
                transactionType = TransactionType.EXPENSE,
                dateTime = paymentDateTime,
                accountNumber = accountNumber,
                transactionHash = "",
                currency = currency,
                description = notes
            )
        )

        val endDate = YearMonth.from(startDate).plusMonths((totalMonths - 1).toLong()).atEndOfMonth()
        val planId = prepaidExpenseDao.insert(
            PrepaidExpenseEntity(
                sourceTransactionId = transactionId,
                merchantName = merchantName,
                category = category,
                totalAmount = totalAmount,
                currency = currency,
                startDate = startDate,
                totalMonths = totalMonths,
                endDate = endDate,
                notes = notes
            )
        )

        transactionDao.updateTransaction(
            transactionDao.getTransactionById(transactionId)!!.copy(prepaidExpenseId = planId)
        )

        insertScheduleFrom(planId, totalAmount, startDate, totalMonths, category)
        return planId
    }

    /**
     * Converts an existing transaction (e.g. one auto-parsed from an SMS) into a
     * prepaid plan retroactively, instead of creating a brand-new payment. Reuses the
     * transaction's own amount/merchant/category/currency/date. The transaction keeps
     * counting as normal spend on its real date — only the plan's own progress display
     * and equivalent-cost figure are derived from the generated allocation schedule.
     */
    suspend fun createPlanFromTransaction(
        transactionId: Long,
        totalMonths: Int,
        category: String? = null,
        notes: String? = null
    ): Long {
        val transaction = transactionDao.getTransactionById(transactionId)
            ?: throw IllegalArgumentException("Transaction $transactionId not found")
        require(transaction.prepaidExpenseId == null) { "Transaction is already part of a prepaid plan" }

        val startDate = transaction.dateTime.toLocalDate()
        val effectiveCategory = category ?: transaction.category
        val endDate = YearMonth.from(startDate).plusMonths((totalMonths - 1).toLong()).atEndOfMonth()

        val planId = prepaidExpenseDao.insert(
            PrepaidExpenseEntity(
                sourceTransactionId = transactionId,
                merchantName = transaction.merchantName,
                category = effectiveCategory,
                totalAmount = transaction.amount,
                currency = transaction.currency,
                startDate = startDate,
                totalMonths = totalMonths,
                endDate = endDate,
                notes = notes
            )
        )

        transactionDao.updateTransaction(
            transaction.copy(prepaidExpenseId = planId)
        )

        insertScheduleFrom(planId, transaction.amount, startDate, totalMonths, effectiveCategory)
        return planId
    }

    private suspend fun insertScheduleFrom(
        planId: Long,
        amount: BigDecimal,
        fromDate: LocalDate,
        months: Int,
        category: String
    ) {
        if (months <= 0) return
        val now = LocalDateTime.now()
        val drafts = computeScheduleUseCase.compute(amount, fromDate, months)
        prepaidAllocationDao.insertAll(
            drafts.map { draft ->
                PrepaidAllocationEntity(
                    prepaidExpenseId = planId,
                    periodYearMonth = draft.periodYearMonth.toString(),
                    allocatedAmount = draft.amount,
                    category = category,
                    status = PrepaidAllocationStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    /**
     * Edits the plan. If no month has elapsed yet, the whole schedule is regenerated.
     * If some months have already elapsed, past rows are untouched and only rows for
     * the current month onward are replaced, spreading (new total - already recognized)
     * over (new total months - months elapsed). The linked payment transaction's
     * amount/date/category are updated in step.
     */
    suspend fun updatePlan(
        planId: Long,
        newMerchantName: String,
        newCategory: String,
        newTotalAmount: BigDecimal,
        newStartDate: LocalDate,
        newTotalMonths: Int
    ) {
        val plan = prepaidExpenseDao.getById(planId) ?: return
        val currentMonth = YearMonth.now()
        val newEndDate = YearMonth.from(newStartDate).plusMonths((newTotalMonths - 1).toLong()).atEndOfMonth()

        if (currentMonth < YearMonth.from(plan.startDate)) {
            // Nothing has elapsed yet — safe to fully regenerate.
            prepaidAllocationDao.deleteAllForPlan(planId)
            insertScheduleFrom(planId, newTotalAmount, newStartDate, newTotalMonths, newCategory)
        } else {
            val pastAllocations = prepaidAllocationDao.getAllocationsForPlanSync(planId)
                .filter { it.status != PrepaidAllocationStatus.REVERSED && YearMonth.parse(it.periodYearMonth) < currentMonth }
            val recognizedSoFar = pastAllocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.allocatedAmount }
            val monthsElapsed = pastAllocations.size
            val remainingMonths = (newTotalMonths - monthsElapsed).coerceAtLeast(0)
            val remainingAmount = (newTotalAmount - recognizedSoFar).coerceAtLeast(BigDecimal.ZERO)

            prepaidAllocationDao.deleteAllocationsFrom(planId, currentMonth.toString())
            if (remainingMonths > 0) {
                insertScheduleFrom(planId, remainingAmount, currentMonth.atDay(1), remainingMonths, newCategory)
            } else if (remainingAmount > BigDecimal.ZERO) {
                // Not enough future months left for the new total — true-up in the current month.
                insertScheduleFrom(planId, remainingAmount, currentMonth.atDay(1), 1, newCategory)
            }
        }

        prepaidExpenseDao.update(
            plan.copy(
                merchantName = newMerchantName,
                category = newCategory,
                totalAmount = newTotalAmount,
                startDate = newStartDate,
                totalMonths = newTotalMonths,
                endDate = newEndDate,
                updatedAt = LocalDateTime.now()
            )
        )

        transactionDao.getTransactionById(plan.sourceTransactionId)?.let { transaction ->
            transactionDao.updateTransaction(
                transaction.copy(
                    merchantName = newMerchantName,
                    category = newCategory,
                    amount = newTotalAmount
                )
            )
        }
    }

    /** Adds trailing months without disturbing existing rows. */
    suspend fun extendPlan(planId: Long, additionalMonths: Int, additionalAmount: BigDecimal) {
        val plan = prepaidExpenseDao.getById(planId) ?: return
        if (plan.status != PrepaidExpenseStatus.ACTIVE) return

        val extensionStart = YearMonth.from(plan.endDate).plusMonths(1)
        insertScheduleFrom(planId, additionalAmount, extensionStart.atDay(1), additionalMonths, plan.category)

        val newEndDate = extensionStart.plusMonths((additionalMonths - 1).toLong()).atEndOfMonth()
        prepaidExpenseDao.update(
            plan.copy(
                totalAmount = plan.totalAmount + additionalAmount,
                totalMonths = plan.totalMonths + additionalMonths,
                endDate = newEndDate,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    /**
     * Stops future recognition, keeps history. Past months remain RECOGNIZED for
     * historical accuracy; months from the current one onward are REVERSED. No
     * refund transaction is created — cancellation is not the same as a refund.
     */
    suspend fun cancelPlan(planId: Long, effectiveFromMonth: YearMonth = YearMonth.now()) {
        val now = LocalDateTime.now()
        prepaidAllocationDao.reverseFutureAllocations(planId, effectiveFromMonth.minusMonths(1).toString(), now)
        prepaidExpenseDao.updateStatus(planId, PrepaidExpenseStatus.CANCELLED.name, now, now)
    }

    /**
     * Partial or full refund. Allocations for months after the refund month are
     * reversed and the remaining unrecognized amount is re-spread over the rest of
     * the plan. Months already recognized before the refund date are untouched. A
     * full refund (nothing left unrecognized) marks the plan REFUNDED.
     */
    suspend fun refundPlan(planId: Long, refundAmount: BigDecimal, refundDate: LocalDate) {
        val plan = prepaidExpenseDao.getById(planId) ?: return
        val refundMonth = YearMonth.from(refundDate)
        val now = LocalDateTime.now()

        val keptAllocations = prepaidAllocationDao.getAllocationsForPlanSync(planId)
            .filter { it.status != PrepaidAllocationStatus.REVERSED && YearMonth.parse(it.periodYearMonth) <= refundMonth }
        val recognizedSoFar = keptAllocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.allocatedAmount }

        prepaidAllocationDao.reverseFutureAllocations(planId, refundMonth.toString(), now)

        val newTotal = (plan.totalAmount - refundAmount).coerceAtLeast(BigDecimal.ZERO)
        val remainingAmount = (newTotal - recognizedSoFar).coerceAtLeast(BigDecimal.ZERO)
        val remainingMonths = YearMonth.from(plan.endDate).let { end ->
            if (end <= refundMonth) 0 else java.time.temporal.ChronoUnit.MONTHS.between(refundMonth, end).toInt()
        }

        if (remainingMonths > 0 && remainingAmount > BigDecimal.ZERO) {
            insertScheduleFrom(planId, remainingAmount, refundMonth.plusMonths(1).atDay(1), remainingMonths, plan.category)
        }

        val isFullRefund = remainingAmount <= BigDecimal.ZERO
        prepaidExpenseDao.update(
            plan.copy(
                totalAmount = newTotal,
                status = if (isFullRefund) PrepaidExpenseStatus.REFUNDED else PrepaidExpenseStatus.ACTIVE,
                updatedAt = now
            )
        )
    }

    /** Cascades: delete allocations (DB FK CASCADE) + hard-delete the source transaction. */
    suspend fun deletePlan(planId: Long) {
        val plan = prepaidExpenseDao.getById(planId) ?: return
        prepaidExpenseDao.deleteById(planId) // CASCADE removes prepaid_allocations rows
        transactionDao.getTransactionById(plan.sourceTransactionId)?.let {
            transactionDao.deleteTransaction(it)
        }
    }

    /** Called when the *source payment* transaction itself is deleted directly. */
    suspend fun onSourceTransactionDeleted(transactionId: Long) {
        val plan = prepaidExpenseDao.getByTransactionId(transactionId) ?: return
        prepaidExpenseDao.deleteById(plan.id) // CASCADE removes prepaid_allocations rows
    }
}
