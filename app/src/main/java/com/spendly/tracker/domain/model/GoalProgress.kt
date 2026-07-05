package com.spendly.tracker.domain.model

import com.spendly.tracker.data.database.entity.GoalContributionEntity
import com.spendly.tracker.data.database.entity.GoalEntity
import java.math.BigDecimal
import java.time.LocalDate

data class GoalProgress(
    val goal: GoalEntity,
    val progressPercent: Float,
    val daysRemaining: Int,
    val dailySavingsNeeded: BigDecimal,
    val projectedCompletionDate: LocalDate?,
    val totalContributed: BigDecimal,
    val recentContributions: List<GoalContributionEntity> = emptyList()
)
