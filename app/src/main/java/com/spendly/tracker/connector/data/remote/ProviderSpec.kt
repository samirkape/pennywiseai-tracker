package com.spendly.tracker.connector.data.remote

import com.spendly.tracker.connector.domain.AiProvider

/** Wire protocol a provider speaks. */
internal enum class AiProtocol {
    /** OpenAI Responses API (`/v1/responses`). */
    RESPONSES,

    /** Anthropic Messages API (`/v1/messages`). */
    MESSAGES,

    /** OpenAI-compatible Chat Completions (`/chat/completions`). */
    CHAT_COMPLETIONS
}

/** Transport details for a provider — kept out of the domain model. */
internal data class ProviderSpec(
    val provider: AiProvider,
    val protocol: AiProtocol,
    val baseUrl: String,
    val extraHeaders: Map<String, String> = emptyMap()
)

internal fun AiProvider.spec(): ProviderSpec = when (this) {
    AiProvider.OPENAI -> ProviderSpec(
        provider = this,
        protocol = AiProtocol.RESPONSES,
        baseUrl = "https://api.openai.com/v1"
    )

    AiProvider.ANTHROPIC -> ProviderSpec(
        provider = this,
        protocol = AiProtocol.MESSAGES,
        baseUrl = "https://api.anthropic.com/v1"
    )

    AiProvider.GEMINI -> ProviderSpec(
        provider = this,
        protocol = AiProtocol.CHAT_COMPLETIONS,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
    )

    AiProvider.GROQ -> ProviderSpec(
        provider = this,
        protocol = AiProtocol.CHAT_COMPLETIONS,
        baseUrl = "https://api.groq.com/openai/v1"
    )

    AiProvider.OPENROUTER -> ProviderSpec(
        provider = this,
        protocol = AiProtocol.CHAT_COMPLETIONS,
        baseUrl = "https://openrouter.ai/api/v1"
    )
}
