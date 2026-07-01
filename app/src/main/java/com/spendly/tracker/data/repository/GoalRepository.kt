package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.dao.GoalContributionDao
import com.spendly.tracker.data.database.dao.GoalDao
import com.spendly.tracker.data.database.entity.ContributionSource
import com.spendly.tracker.data.database.entity.GoalContributionEntity
import com.spendly.tracker.data.database.entity.GoalEntity
import com.spendly.tracker.data.database.entity.GoalStatus
import com.spendly.tracker.data.database.entity.GoalTrackingMode
import com.spendly.tracker.data.database.entity.GoalType
import com.spendly.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val goalContributionDao: GoalContributionDao,
) {

    // ── Goals ─────────────────────────────────────────────────────────────────

    fun getActiveGoals(): Flow<List<GoalEntity>> = goalDao.getActiveGoals()

    fun getAllGoals(): Flow<List<GoalEntity>> = goalDao.getAllGoals()

    fun getArchivedGoals(): Flow<List<GoalEntity>> = goalDao.getArchivedGoals()

    fun getGoalByIdFlow(id: Long): Flow<GoalEntity?> = goalDao.getGoalByIdFlow(id)

    suspend fun getGoalById(id: Long): GoalEntity? = goalDao.getGoalById(id)

    fun getActiveGoalCount(): Flow<Int> = goalDao.getActiveGoalCount()

    suspend fun createGoal(
        name: String,
        description: String?,
        goalType: GoalType,
        targetAmount: BigDecimal,
        targetDate: LocalDate,
        currency: String,
        color: String,
        trackingMode: GoalTrackingMode,
        autoTrackCategories: List<String>,
        customTypeName: String? = null
    ): Long {
        val entity = GoalEntity(
            name = name,
            description = description,
            goalType = goalType,
            targetAmount = targetAmount,
            targetDate = targetDate,
            currency = currency,
            color = color,
            trackingMode = trackingMode,
            autoTrackCategories = autoTrackCategories.joinToString(","),
            customTypeName = customTypeName,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        return goalDao.insertGoal(entity)
    }

    suspend fun updateGoal(
        goalId: Long,
        name: String,
        description: String?,
        goalType: GoalType,
        targetAmount: BigDecimal,
        targetDate: LocalDate,
        currency: String,
        color: String,
        trackingMode: GoalTrackingMode,
        autoTrackCategories: List<String>,
        customTypeName: String? = null
    ) {
        val existing = goalDao.getGoalById(goalId) ?: return
        goalDao.updateGoal(
            existing.copy(
                name = name,
                description = description,
                goalType = goalType,
                targetAmount = targetAmount,
                targetDate = targetDate,
                currency = currency,
                color = color,
                trackingMode = trackingMode,
                autoTrackCategories = autoTrackCategories.joinToString(","),
                customTypeName = customTypeName,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun deleteGoal(goalId: Long) = goalDao.deleteGoalById(goalId)

    suspend fun updateGoalStatus(goalId: Long, status: GoalStatus) {
        goalDao.updateStatus(goalId, status.name)
    }

    // ── Contributions ─────────────────────────────────────────────────────────

    fun getContributionsForGoal(goalId: Long): Flow<List<GoalContributionEntity>> =
        goalContributionDao.getContributionsForGoal(goalId)

    suspend fun addManualDeposit(
        goalId: Long,
        amount: BigDecimal,
        note: String?
    ): Long {
        val contribution = GoalContributionEntity(
            goalId = goalId,
            amount = amount,
            note = note,
            contributedAt = LocalDateTime.now(),
            source = ContributionSource.MANUAL_DEPOSIT
        )
        val id = goalContributionDao.insertContribution(contribution)
        goalDao.adjustCurrentAmount(goalId, amount.toPlainString())
        return id
    }

    suspend fun linkTransaction(
        goalId: Long,
        transactionId: Long,
        amount: BigDecimal,
        note: String?
    ): Long {
        val existing = goalContributionDao.getLinkedContributionsForTransaction(transactionId)
            .firstOrNull { it.goalId == goalId }
        if (existing != null) return existing.id
        val contribution = GoalContributionEntity(
            goalId = goalId,
            transactionId = transactionId,
            amount = amount,
            note = note,
            contributedAt = LocalDateTime.now(),
            source = ContributionSource.TRANSACTION_LINKED
        )
        val id = goalContributionDao.insertContribution(contribution)
        goalDao.adjustCurrentAmount(goalId, amount.toPlainString())
        return id
    }

    suspend fun replaceTransactionLinks(
        transactionId: Long,
        splits: List<Pair<Long, BigDecimal>>
    ) {
        val existing = goalContributionDao.getLinkedContributionsForTransaction(transactionId)
        existing.forEach { contribution ->
            goalContributionDao.deleteContributionById(contribution.id)
            goalDao.adjustCurrentAmount(contribution.goalId, contribution.amount.negate().toPlainString())
        }
        splits.forEach { (goalId, amount) ->
            val contribution = GoalContributionEntity(
                goalId = goalId,
                transactionId = transactionId,
                amount = amount,
                contributedAt = LocalDateTime.now(),
                source = ContributionSource.TRANSACTION_LINKED
            )
            goalContributionDao.insertContribution(contribution)
            goalDao.adjustCurrentAmount(goalId, amount.toPlainString())
        }
    }

    suspend fun unlinkTransaction(contributionId: Long) {
        val contribution = goalContributionDao.getContributionById(contributionId) ?: return
        goalContributionDao.deleteContributionById(contributionId)
        goalDao.adjustCurrentAmount(contribution.goalId, contribution.amount.negate().toPlainString())
    }

    suspend fun autoContribute(
        goalId: Long,
        transactionId: Long,
        amount: BigDecimal
    ) {
        val existing = goalContributionDao.getAutoContributionForTransaction(transactionId)
        if (existing != null) return
        val contribution = GoalContributionEntity(
            goalId = goalId,
            transactionId = transactionId,
            amount = amount,
            contributedAt = LocalDateTime.now(),
            source = ContributionSource.AUTO
        )
        goalContributionDao.insertContribution(contribution)
        goalDao.adjustCurrentAmount(goalId, amount.toPlainString())
    }

    suspend fun revokeAutoContributionForTransaction(transactionId: Long) {
        val contrib = goalContributionDao.getAutoContributionForTransaction(transactionId) ?: return
        goalContributionDao.deleteContribution(contrib)
        goalDao.adjustCurrentAmount(contrib.goalId, contrib.amount.negate().toPlainString())
    }

    suspend fun getGoalsEligibleForTransaction(
        category: String,
        transactionType: TransactionType
    ): List<GoalEntity> {
        if (transactionType != TransactionType.INCOME) return emptyList()
        return goalDao.getActiveGoalsForCategory(category)
    }

    suspend fun getLinkedGoalForTransaction(transactionId: Long): GoalContributionEntity? =
        goalContributionDao.getContributionByTransactionId(transactionId)

    suspend fun getLinkedGoalsForTransaction(transactionId: Long): List<GoalContributionEntity> =
        goalContributionDao.getLinkedContributionsForTransaction(transactionId)
}
