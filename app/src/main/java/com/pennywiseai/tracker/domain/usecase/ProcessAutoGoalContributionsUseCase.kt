package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.GoalRepository
import javax.inject.Inject

class ProcessAutoGoalContributionsUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    suspend fun execute(transaction: TransactionEntity) {
        if (transaction.transactionType != TransactionType.INCOME) return
        if (transaction.isDeleted) return
        if (transaction.category.isBlank()) return

        val eligibleGoals = goalRepository.getGoalsEligibleForTransaction(
            category = transaction.category,
            transactionType = transaction.transactionType
        )
        eligibleGoals.forEach { goal ->
            goalRepository.autoContribute(
                goalId = goal.id,
                transactionId = transaction.id,
                amount = transaction.amount
            )
        }
    }

    suspend fun revokeContribution(transactionId: Long) {
        goalRepository.revokeAutoContributionForTransaction(transactionId)
    }
}
