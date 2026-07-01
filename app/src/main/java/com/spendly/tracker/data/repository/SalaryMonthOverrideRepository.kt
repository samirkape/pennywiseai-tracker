package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.dao.SalaryMonthOverrideDao
import com.spendly.tracker.data.database.entity.SalaryMonthOverrideEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalaryMonthOverrideRepository @Inject constructor(
    private val dao: SalaryMonthOverrideDao
) {
    /** Emits all overrides as a map of "YYYY-MM" → startDay for easy lookup. */
    val overridesMap: Flow<Map<String, Int>> = dao.getAllOverrides()
        .map { list -> list.associate { it.yearMonth to it.startDay } }

    suspend fun setOverride(yearMonth: String, startDay: Int) {
        dao.upsert(SalaryMonthOverrideEntity(yearMonth, startDay))
    }

    suspend fun clearOverride(yearMonth: String) {
        dao.deleteOverride(yearMonth)
    }
}
