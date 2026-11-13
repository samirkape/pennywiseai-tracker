package com.spendly.tracker.connector.data.repository

import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.data.remote.ProviderClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a provider to its protocol adapter.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    @OpenAiClient private val openAiClient: ProviderClient,
    @AnthropicClient private val anthropicClient: ProviderClient
) {
    fun clientFor(provider: AiProvider): ProviderClient = when (provider) {
        AiProvider.OPENAI -> openAiClient
        AiProvider.ANTHROPIC -> anthropicClient
    }
}
