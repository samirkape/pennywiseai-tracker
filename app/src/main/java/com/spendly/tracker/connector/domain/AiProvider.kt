package com.spendly.tracker.connector.domain

/**
 * Remote LLM providers the connector can talk to.
 *
 * The rest of the app never sees provider-specific payloads: each provider is
 * translated to/from the neutral contract in this package by an adapter living in
 * `com.spendly.tracker.connector.data.remote`.
 *
 * [freeTier] marks providers that hand out an API key without payment — those are
 * listed first in the UI because they work for everyone.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val apiKeyHint: String,
    val keyUrl: String,
    val freeTier: Boolean,
    val freeTierNote: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini (Google AI Studio)",
        defaultModel = "gemini-3.8-flash",
        suggestedModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash"
        ),
        apiKeyHint = "AIza…",
        keyUrl = "https://aistudio.google.com/apikey",
        freeTier = true,
        freeTierNote = "Free tier — no credit card needed"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        defaultModel = "openai/gpt-oss-120b",
        suggestedModels = listOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3.8-27b"
        ),
        apiKeyHint = "gsk_…",
        keyUrl = "https://console.groq.com/keys",
        freeTier = true,
        freeTierNote = "Free plan — rate limited, very fast"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultModel = "nvidia/nemotron-3.5-lightning:free",
        suggestedModels = listOf(
            "nvidia/nemotron-3.5-lightning:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "google/gemma-4-31b-it:free"
        ),
        apiKeyHint = "sk-or-v1-…",
        keyUrl = "https://openrouter.ai/keys",
        freeTier = true,
        freeTierNote = "Free models (:free) — 20 requests/min, 50/day"
    ),
    OPENAI(
        id = "openai",
        displayName = "ChatGPT (OpenAI)",
        defaultModel = "gpt-6.1-terra",
        suggestedModels = listOf(
            "gpt-6.1-terra",
            "gpt-6-luna",
            "gpt-6.1-sol",
            "gpt-6-astra"
        ),
        apiKeyHint = "sk-…",
        keyUrl = "https://platform.openai.com/api-keys",
        freeTier = false,
        freeTierNote = "Requires a paid OpenAI account"
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Claude (Anthropic)",
        defaultModel = "claude-sonnet-5",
        suggestedModels = listOf(
            "claude-sonnet-5",
            "claude-opus-5",
            "claude-sonnet-4-6",
            "claude-haiku-4-5"
        ),
        apiKeyHint = "sk-ant-…",
        keyUrl = "https://console.anthropic.com/settings/keys",
        freeTier = false,
        freeTierNote = "Requires a paid Anthropic account"
    );

    companion object {
        fun fromId(id: String?): AiProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI
    }
}
