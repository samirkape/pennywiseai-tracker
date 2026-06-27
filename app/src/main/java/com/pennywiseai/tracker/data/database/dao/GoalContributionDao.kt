package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.ContributionSource
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {

    @Query("SELECT * FROM goal_contributions WHERE goal_id = :goalId ORDER BY contributed_at DESC")
    fun getContributionsForGoal(goalId: Long): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE goal_id = :goalId ORDER BY contributed_at DESC")
    suspend fun getContributionsForGoalSync(goalId: Long): List<GoalContributionEntity>

    @Query("SELECT * FROM goal_contributions WHERE id = :contributionId LIMIT 1")
    suspend fun getContributionById(contributionId: Long): GoalContributionEntity?

    @Query("SELECT * FROM goal_contributions WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun getContributionByTransactionId(transactionId: Long): GoalContributionEntity?

    @Query("SELECT * FROM goal_contributions WHERE transaction_id = :transactionId AND source = 'TRANSACTION_LINKED'")
    suspend fun getLinkedContributionsForTransaction(transactionId: Long): List<GoalContributionEntity>

    @Query("SELECT * FROM goal_contributions WHERE transaction_id = :transactionId AND source = 'AUTO' LIMIT 1")
    suspend fun getAutoContributionForTransaction(transactionId: Long): GoalContributionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: GoalContributionEntity): Long

    @Delete
    suspend fun deleteContribution(contribution: GoalContributionEntity)

    @Query("DELETE FROM goal_contributions WHERE id = :contributionId")
    suspend fun deleteContributionById(contributionId: Long)

    @Query("DELETE FROM goal_contributions WHERE transaction_id = :transactionId")
    suspend fun deleteContributionsForTransaction(transactionId: Long)

    @Query("DELETE FROM goal_contributions")
    suspend fun deleteAllContributions()
}
