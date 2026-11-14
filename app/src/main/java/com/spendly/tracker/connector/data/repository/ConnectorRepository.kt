package com.spendly.tracker.connector.data.repository

import com.spendly.tracker.connector.data.analysis.SpendAnalysisRepository
import com.spendly.tracker.connector.data.preferences.ConnectorPreferencesRepository
import com.spendly.tracker.connector.data.remote.ProviderClient
import com.spendly.tracker.connector.data.security.SecureKeyStore
import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.domain.AiRequest
import com.spendly.tracker.connector.domain.AiStreamEvent
import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.ConnectorError
import com.spendly.tracker.connector.domain.ConnectorException
import com.spendly.tracker.connector.domain.SpendAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the UI needs to render the connector state. */
data class ConnectorConfig(
    val enabled: Boolean,
    val provider: AiProvider,
    val model: String,
    val hasApiKey: Boolean
)

/**
 * Entry point of the AI connector.
 *
 * Orchestrates: policy check (is the connector switched on?), context building
 * (privacy-safe spending brief), and the provider call.
 */
@Singleton
class ConnectorRepository @Inject constructor(
    private val preferencesRepository: ConnectorPreferencesRepository,
    private val secureKeyStore: SecureKeyStore,
    private val analysisRepository: SpendAnalysisRepository,
    private val providerRegistry: ProviderRegistry
) {

    val config: Flow<ConnectorConfig> = combine(
        preferencesRepository.isEnabled,
        preferencesRepository.provider,
        preferencesRepository.model
    ) { enabled, provider, model ->
        ConnectorConfig(
            enabled = enabled,
            provider = provider,
            model = model,
            hasApiKey = secureKeyStore.hasApiKey()
        )
    }

    suspend fun currentConfig(): ConnectorConfig = config.first()

    /** Builds the brief without calling any provider — used for the local preview. */
    suspend fun buildAnalysis(
        window: AnalysisWindow,
        useCalendarMonths: Boolean = false
    ): SpendAnalysis =
        analysisRepository.buildAnalysis(window, useCalendarMonths = useCalendarMonths)

    /** Brief plus the optional per-transaction rows used by the share sheet. */
    suspend fun buildBrief(
        window: AnalysisWindow,
        includeTransactions: Boolean = false,
        useCalendarMonths: Boolean = false
    ): SpendAnalysisRepository.SpendBrief =
        analysisRepository.buildBrief(
            window,
            includeTransactions = includeTransactions,
            useCalendarMonths = useCalendarMonths
        )

    /**
     * Runs an analysis against the configured provider.
     *
     * The stream falls back to a single non-streaming request when the provider
     * refuses to stream, so the user always gets an answer.
     */
    fun analyze(
        window: AnalysisWindow,
        question: String,
        useCalendarMonths: Boolean = false
    ): Flow<AiStreamEvent> = flow {
        val config = currentConfig()
        if (!config.enabled) {
            throw ConnectorException(ConnectorError.Disabled)
        }
        val apiKey = secureKeyStore.getApiKey()
        if (apiKey.isNullOrBlank()) {
            throw ConnectorException(ConnectorError.MissingApiKey)
        }

        val analysis = analysisRepository.buildAnalysis(window, useCalendarMonths = useCalendarMonths)
        val request = AiRequest(
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = buildUserPrompt(analysis, question),
            model = config.model
        )

        val client = providerRegistry.clientFor(config.provider)

        var receivedText = false
        try {
            client.stream(request, apiKey).collect { event ->
                if (event is AiStreamEvent.TextDelta && event.text.isNotEmpty()) {
                    receivedText = true
                }
                emit(event)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConnectorException) {
            if (receivedText) throw e
            // Streaming was refused — fall back to a single request.
            emitFallback(client, request, apiKey)
        } catch (e: Exception) {
            if (receivedText) throw ConnectorException(ConnectorError.Unknown(e.message))
            emitFallback(client, request, apiKey)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<AiStreamEvent>.emitFallback(
        client: ProviderClient,
        request: AiRequest,
        apiKey: String
    ) {
        val response = client.complete(request, apiKey)
        if (response.text.isNotBlank()) {
            emit(AiStreamEvent.TextDelta(response.text))
        }
        emit(AiStreamEvent.Done)
    }

    private fun buildUserPrompt(analysis: SpendAnalysis, question: String): String {
        val trimmed = question.trim()
        return buildString {
            appendLine(analysis.renderPromptPayload())
            appendLine()
            appendLine("QUESTION: ${if (trimmed.isEmpty()) DEFAULT_QUESTION else trimmed}")
        }
    }

    companion object {
        const val DEFAULT_QUESTION =
            "Analyse my spending over this period: what patterns stand out, where is my money going, and what should I change?"

        val SYSTEM_PROMPT = """
            You are Spendly AI, a personal finance analyst built into the Spendly expense tracker.
            You receive an aggregated spending brief for one period plus the user's question.

            Rules:
            - Base every statement on the brief. Never invent transactions, merchants, categories or amounts.
            - Use the currency code given in the brief for all amounts.
            - If the brief lacks the data needed for an answer, say so plainly.
            - Lead with the three most important findings, then add supporting detail.
            - Look for patterns: category concentration, recurring charges, weekday vs weekend behaviour, spikes, merchants visited repeatedly, and change versus the previous period.
            - Close with concrete actions the user can take this month.
            - Format with short markdown headings and bullet lists, bolding key figures. Stay under 400 words.
        """.trimIndent()
    }
}
