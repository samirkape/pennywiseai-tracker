package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.repository.GoalRepository
import javax.inject.Inject

class ProcessAutoGoalContributionsUseCase @Inject constructor(
    private val goalRepository: GoalRepository
) {
    suspend fun execute(transaction: TransactionEntity) {
        if (transaction.transactionType != TransactionType.INCOME) return
        // Loan repayments are money coming back, not new income to allocate
        if (transaction.loanId != null) return
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
