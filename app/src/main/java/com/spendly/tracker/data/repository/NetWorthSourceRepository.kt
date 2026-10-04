package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.dao.NetWorthSourceDao
import com.spendly.tracker.data.database.entity.NetWorthSourceEntity
import com.spendly.tracker.data.database.entity.NetWorthSourceOrigin
import com.spendly.tracker.data.database.entity.NetWorthSourceType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetWorthSourceRepository @Inject constructor(
    private val dao: NetWorthSourceDao
) {

    fun getAll(): Flow<List<NetWorthSourceEntity>> = dao.getAll()

    suspend fun getById(id: Long): NetWorthSourceEntity? = dao.getById(id)

    suspend fun addManual(
        name: String,
        type: NetWorthSourceType,
        value: BigDecimal,
        currency: String,
        notes: String?
    ): Long = dao.insert(
        NetWorthSourceEntity(
            name = name.trim(),
            type = type,
            origin = NetWorthSourceOrigin.MANUAL,
            value = value,
            currency = currency,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() }
        )
    )

    suspend fun updateManual(
        id: Long,
        name: String,
        type: NetWorthSourceType,
        value: BigDecimal,
        notes: String?
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                name = name.trim(),
                type = type,
                value = value,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Replaces all CAS-imported holdings so repeated imports never double count. */
    suspend fun replaceCasHoldings(holdings: List<NetWorthSourceEntity>) =
        dao.replaceByOrigin(NetWorthSourceOrigin.CAS.name, holdings)
}
