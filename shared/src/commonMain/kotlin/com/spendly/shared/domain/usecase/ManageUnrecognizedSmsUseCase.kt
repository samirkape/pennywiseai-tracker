package com.spendly.shared.domain.usecase

import com.spendly.shared.data.local.entity.SharedUnrecognizedSmsEntity
import com.spendly.shared.data.repository.SharedUnrecognizedSmsRepository
import com.spendly.shared.data.util.currentTimeMillis

class ManageUnrecognizedSmsUseCase(
    private val repository: SharedUnrecognizedSmsRepository
) {
    suspend fun add(sender: String, smsBody: String): Long {
        val now = currentTimeMillis()
        return repository.insert(
            SharedUnrecognizedSmsEntity(
                sender = sender,
                smsBody = smsBody,
                receivedAtEpochMillis = now,
                createdAtEpochMillis = now
            )
        )
    }
}
