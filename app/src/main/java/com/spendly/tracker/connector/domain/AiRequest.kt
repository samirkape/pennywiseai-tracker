package com.spendly.tracker.connector.domain

/**
 * Neutral request contract. Adapters translate this into vendor payloads and back.
 */
data class AiRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val model: String,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val temperature: Double = DEFAULT_TEMPERATURE
) {
    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1400
        const val DEFAULT_TEMPERATURE = 0.3
    }
}

/** Normalised response returned by a provider adapter. */
data class AiResponse(
    val text: String,
    val provider: AiProvider,
    val model: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
)
