package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface GoalDao {

    @Query("SELECT * FROM financial_goals WHERE status = 'ACTIVE' ORDER BY target_date ASC")
    fun getActiveGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM financial_goals ORDER BY created_at DESC")
    fun getAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM financial_goals WHERE status IN ('COMPLETED','ABANDONED') ORDER BY completed_at DESC")
    fun getArchivedGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM financial_goals WHERE id = :goalId")
    suspend fun getGoalById(goalId: Long): GoalEntity?

    @Query("SELECT * FROM financial_goals WHERE id = :goalId")
    fun getGoalByIdFlow(goalId: Long): Flow<GoalEntity?>

    @Query("SELECT COUNT(*) FROM financial_goals WHERE status = 'ACTIVE'")
    fun getActiveGoalCount(): Flow<Int>

    @Query("""
        SELECT * FROM financial_goals
        WHERE status = 'ACTIVE'
        AND tracking_mode = 'CATEGORY_AUTO'
        AND auto_track_categories LIKE '%' || :categoryName || '%'
    """)
    suspend fun getActiveGoalsForCategory(categoryName: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)

    @Query("DELETE FROM financial_goals WHERE id = :goalId")
    suspend fun deleteGoalById(goalId: Long)

    @Query("""
        UPDATE financial_goals
        SET current_amount = CAST((CAST(current_amount AS REAL) + CAST(:delta AS REAL)) AS TEXT),
            status = CASE
                WHEN CAST(current_amount AS REAL) + CAST(:delta AS REAL) >= CAST(target_amount AS REAL) THEN 'COMPLETED'
                ELSE status
            END,
            completed_at = CASE
                WHEN CAST(current_amount AS REAL) + CAST(:delta AS REAL) >= CAST(target_amount AS REAL) AND completed_at IS NULL
                    THEN datetime('now')
                ELSE completed_at
            END,
            updated_at = datetime('now')
        WHERE id = :goalId
    """)
    suspend fun adjustCurrentAmount(goalId: Long, delta: String)

    @Query("""
        UPDATE financial_goals
        SET status = :status, updated_at = datetime('now'),
            completed_at = CASE WHEN :status = 'COMPLETED' THEN datetime('now') ELSE completed_at END
        WHERE id = :goalId
    """)
    suspend fun updateStatus(goalId: Long, status: String)

    @Query("DELETE FROM financial_goals")
    suspend fun deleteAllGoals()
}
