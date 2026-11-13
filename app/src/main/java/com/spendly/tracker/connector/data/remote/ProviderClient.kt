package com.spendly.tracker.connector.data.remote

import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.domain.AiRequest
import com.spendly.tracker.connector.domain.AiResponse
import com.spendly.tracker.connector.domain.AiStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Protocol adapter for a single provider.
 *
 * Implementations translate the neutral [AiRequest]/[AiStreamEvent] contract into the
 * vendor's wire format and back. Nothing outside this package may know how a
 * provider frames its payload.
 */
interface ProviderClient {
    val provider: AiProvider

    /** One-shot request; used as a fallback when streaming is not possible. */
    suspend fun complete(request: AiRequest, apiKey: String): AiResponse

    /** Streaming request. */
    fun stream(request: AiRequest, apiKey: String): Flow<AiStreamEvent>
}
