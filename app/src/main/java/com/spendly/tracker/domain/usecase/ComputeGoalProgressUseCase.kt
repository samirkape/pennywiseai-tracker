package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.GoalContributionEntity
import com.spendly.tracker.data.database.entity.GoalEntity
import com.spendly.tracker.domain.model.GoalProgress
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ComputeGoalProgressUseCase @Inject constructor() {

    fun compute(
        goal: GoalEntity,
        contributions: List<GoalContributionEntity> = emptyList()
    ): GoalProgress {
        val today = LocalDate.now()
        val daysRemaining = ChronoUnit.DAYS.between(today, goal.targetDate).toInt()
        val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(BigDecimal.ZERO)

        val progressPercent = if (goal.targetAmount > BigDecimal.ZERO) {
            (goal.currentAmount.toFloat() / goal.targetAmount.toFloat() * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }

        val dailySavingsNeeded = if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(daysRemaining), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val daysSinceCreated = ChronoUnit.DAYS.between(goal.createdAt.toLocalDate(), today)
            .coerceAtLeast(1)
        val dailyRate = goal.currentAmount.divide(BigDecimal(daysSinceCreated), 4, RoundingMode.HALF_UP)
        val projectedCompletionDate = if (dailyRate > BigDecimal.ZERO && remaining > BigDecimal.ZERO) {
            val daysNeeded = remaining.divide(dailyRate, 0, RoundingMode.CEILING).toLong()
            today.plusDays(daysNeeded)
        } else if (remaining <= BigDecimal.ZERO) {
            today
        } else {
            null
        }

        return GoalProgress(
            goal = goal,
            progressPercent = progressPercent,
            daysRemaining = daysRemaining,
            dailySavingsNeeded = dailySavingsNeeded,
            projectedCompletionDate = projectedCompletionDate,
            totalContributed = goal.currentAmount,
            recentContributions = contributions
        )
    }
}
