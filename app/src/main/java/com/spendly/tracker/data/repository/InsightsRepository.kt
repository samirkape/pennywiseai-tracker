package com.spendly.tracker.data.repository

import com.google.gson.Gson
import com.spendly.tracker.data.database.dao.InsightsCacheDao
import com.spendly.tracker.data.database.entity.InsightsCacheEntity
import com.spendly.tracker.domain.model.InsightScope
import com.spendly.tracker.domain.model.SmartInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsRepository @Inject constructor(
    private val insightsCacheDao: InsightsCacheDao,
    private val gson: Gson
) {
    fun getAllInsights(): Flow<List<SmartInsight>> {
        return insightsCacheDao.getAllInsights().map { entities ->
            entities.map { entity ->
                gson.fromJson(entity.payload, SmartInsight::class.java)
            }
        }
    }

    /**
     * All-time (lifetime) insights, e.g. lifetime total spend or all-time top merchant. These are
     * computed once against full history by [com.spendly.tracker.domain.usecase.ComputeInsightsUseCase]
     * and only change when new transactions arrive — not when the user flips the month selector.
     */
    fun getLifetimeInsights(): Flow<List<SmartInsight>> {
        return getAllInsights().map { insights -> insights.filter { it.scope == InsightScope.LIFETIME } }
    }

    fun getPeriodInsights(): Flow<List<SmartInsight>> {
        return getAllInsights().map { insights -> insights.filter { it.scope == InsightScope.PERIOD } }
    }

    suspend fun cacheInsight(insight: SmartInsight, dataWindowMonths: Int, transactionCount: Int) {
        val entity = InsightsCacheEntity(
            key = insight.id,
            payload = gson.toJson(insight),
            computedAtEpoch = System.currentTimeMillis(),
            dataWindowMonths = dataWindowMonths,
            transactionCount = transactionCount
        )
        insightsCacheDao.insertInsight(entity)
    }

    suspend fun getCachedEntity(key: String): InsightsCacheEntity? {
        return insightsCacheDao.getInsightByKey(key)
    }

    suspend fun clearCache() {
        insightsCacheDao.clearAll()
    }
}

