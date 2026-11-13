package com.spendly.tracker.connector.data.repository

import com.spendly.tracker.connector.data.remote.ProviderClient
import com.spendly.tracker.connector.domain.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a provider to its protocol adapter.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    clients: Map<AiProvider, ProviderClient>
) {
    private val clients: Map<AiProvider, ProviderClient> = clients.toMap()

    fun clientFor(provider: AiProvider): ProviderClient =
        clients[provider] ?: error("No client registered for provider ${provider.id}")
}
