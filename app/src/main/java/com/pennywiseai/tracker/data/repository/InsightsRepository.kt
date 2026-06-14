package com.pennywiseai.tracker.data.repository

import com.google.gson.Gson
import com.pennywiseai.tracker.data.database.dao.InsightsCacheDao
import com.pennywiseai.tracker.data.database.entity.InsightsCacheEntity
import com.pennywiseai.tracker.domain.model.SmartInsight
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

