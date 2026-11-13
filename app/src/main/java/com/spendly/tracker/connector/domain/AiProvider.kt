package com.spendly.tracker.connector.domain

/**
 * Remote LLM providers the connector can talk to.
 *
 * The rest of the app never sees provider-specific payloads: each provider is
 * translated to/from the neutral contract in this package by an adapter living in
 * `com.spendly.tracker.connector.data.remote`.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val apiKeyHint: String
) {
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
        apiKeyHint = "sk-…"
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
        apiKeyHint = "sk-ant-…"
    );

    companion object {
        fun fromId(id: String?): AiProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OPENAI
    }
}
