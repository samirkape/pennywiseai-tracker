package com.spendly.shared.data.repository

import com.spendly.shared.data.local.dao.SharedSubscriptionDao
import com.spendly.shared.data.local.entity.SharedSubscriptionEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedSubscriptionRepository(
    private val dao: SharedSubscriptionDao
) : SharedSubscriptionRepository {
    override fun observeAll(): Flow<List<SharedSubscriptionEntity>> = dao.observeAll()
    override suspend fun upsert(subscription: SharedSubscriptionEntity): Long = dao.upsert(subscription)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
