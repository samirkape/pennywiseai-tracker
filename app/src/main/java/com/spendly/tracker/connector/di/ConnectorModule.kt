package com.spendly.tracker.connector.di

import com.spendly.tracker.connector.data.remote.AnthropicProviderClient
import com.spendly.tracker.connector.data.remote.ChatCompletionsProviderClient
import com.spendly.tracker.connector.data.remote.OpenAiProviderClient
import com.spendly.tracker.connector.data.remote.ProviderClient
import com.spendly.tracker.connector.data.remote.spec
import com.spendly.tracker.connector.data.repository.AnthropicClient
import com.spendly.tracker.connector.data.repository.GeminiClient
import com.spendly.tracker.connector.data.repository.GroqClient
import com.spendly.tracker.connector.data.repository.OpenAiClient
import com.spendly.tracker.connector.data.repository.OpenRouterClient
import com.spendly.tracker.connector.data.repository.ProviderRegistry
import com.spendly.tracker.connector.domain.AiProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConnectorModule {

    @Provides
    @Singleton
    fun provideConnectorHttpClient(): HttpClient = HttpClient(Android) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    @Provides
    @Singleton
    @OpenAiClient
    fun provideOpenAiClient(httpClient: HttpClient): ProviderClient =
        OpenAiProviderClient(httpClient)

    @Provides
    @Singleton
    @AnthropicClient
    fun provideAnthropicClient(httpClient: HttpClient): ProviderClient =
        AnthropicProviderClient(httpClient)

    @Provides
    @Singleton
    @GeminiClient
    fun provideGeminiClient(httpClient: HttpClient): ProviderClient {
        val spec = AiProvider.GEMINI.spec()
        return ChatCompletionsProviderClient(httpClient, spec.provider, spec.baseUrl, spec.extraHeaders)
    }

    @Provides
    @Singleton
    @GroqClient
    fun provideGroqClient(httpClient: HttpClient): ProviderClient {
        val spec = AiProvider.GROQ.spec()
        return ChatCompletionsProviderClient(httpClient, spec.provider, spec.baseUrl, spec.extraHeaders)
    }

    @Provides
    @Singleton
    @OpenRouterClient
    fun provideOpenRouterClient(httpClient: HttpClient): ProviderClient {
        val spec = AiProvider.OPENROUTER.spec()
        return ChatCompletionsProviderClient(httpClient, spec.provider, spec.baseUrl, spec.extraHeaders)
    }

    @Provides
    @Singleton
    fun provideProviderRegistry(
        @OpenAiClient openAiClient: ProviderClient,
        @AnthropicClient anthropicClient: ProviderClient,
        @GeminiClient geminiClient: ProviderClient,
        @GroqClient groqClient: ProviderClient,
        @OpenRouterClient openRouterClient: ProviderClient
    ): ProviderRegistry = ProviderRegistry(
        mapOf(
            AiProvider.OPENAI to openAiClient,
            AiProvider.ANTHROPIC to anthropicClient,
            AiProvider.GEMINI to geminiClient,
            AiProvider.GROQ to groqClient,
            AiProvider.OPENROUTER to openRouterClient
        )
    )
}
