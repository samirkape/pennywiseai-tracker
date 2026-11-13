package com.spendly.tracker.connector.di

import com.spendly.tracker.connector.data.remote.AnthropicProviderClient
import com.spendly.tracker.connector.data.remote.OpenAiProviderClient
import com.spendly.tracker.connector.data.remote.ProviderClient
import com.spendly.tracker.connector.data.repository.AnthropicClient
import com.spendly.tracker.connector.data.repository.OpenAiClient
import com.spendly.tracker.connector.data.repository.ProviderRegistry
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
    fun provideProviderRegistry(
        @OpenAiClient openAiClient: ProviderClient,
        @AnthropicClient anthropicClient: ProviderClient
    ): ProviderRegistry = ProviderRegistry(openAiClient, anthropicClient)
}
