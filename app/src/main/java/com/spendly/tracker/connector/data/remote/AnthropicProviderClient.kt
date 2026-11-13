package com.spendly.tracker.connector.data.remote

import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.domain.AiRequest
import com.spendly.tracker.connector.domain.AiResponse
import com.spendly.tracker.connector.domain.AiStreamEvent
import com.spendly.tracker.connector.domain.ConnectorError
import com.spendly.tracker.connector.domain.ConnectorException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Adapter for the Anthropic Messages API.
 */
class AnthropicProviderClient(
    private val httpClient: HttpClient
) : ProviderClient {

    override val provider: AiProvider = AiProvider.ANTHROPIC

    override suspend fun complete(request: AiRequest, apiKey: String): AiResponse {
        val response = httpClient.post(ENDPOINT) {
            header(API_KEY_HEADER, apiKey)
            header(API_VERSION_HEADER, API_VERSION)
            contentType(ContentType.Application.Json)
            setBody(buildBody(request, stream = false))
        }

        val status = response.status.value
        val raw = response.bodyAsText()
        if (status !in 200..299) {
            throw ConnectorException(mapError(status, raw))
        }

        val root = parseOrNull(raw) ?: throw ConnectorException(ConnectorError.Unknown())
        val text = extractText(root)
        val usage = root["usage"]?.jsonObject
        return AiResponse(
            text = text,
            provider = provider,
            model = request.model,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNullSafe(),
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNullSafe()
        )
    }

    override fun stream(request: AiRequest, apiKey: String): Flow<AiStreamEvent> = flow {
        httpClient.prepareRequest {
            method = HttpMethod.Post
            url(ENDPOINT)
            header(API_KEY_HEADER, apiKey)
            header(API_VERSION_HEADER, API_VERSION)
            contentType(ContentType.Application.Json)
            setBody(buildBody(request, stream = true))
        }.execute { response ->
            val status = response.status.value
            if (status !in 200..299) {
                val raw = response.bodyAsText()
                throw ConnectorException(mapError(status, raw))
            }

            val channel = response.bodyAsChannel()
            SseReader.read(channel) { payload ->
                val event = parseOrNull(payload) ?: return@read
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "content_block_delta" -> {
                        val delta = event["delta"]?.jsonObject
                        delta?.get("text")?.jsonPrimitive?.contentOrNull
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { emit(AiStreamEvent.TextDelta(it)) }
                    }
                    "message_start" -> {
                        val usage = event["message"]?.jsonObject?.get("usage")?.jsonObject
                        emit(
                            AiStreamEvent.Usage(
                                inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNullSafe(),
                                outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNullSafe()
                            )
                        )
                    }
                    "message_delta" -> {
                        val usage = event["usage"]?.jsonObject
                        emit(
                            AiStreamEvent.Usage(
                                inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNullSafe(),
                                outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNullSafe()
                            )
                        )
                    }
                    else -> Unit
                }
            }
        }
        emit(AiStreamEvent.Done)
    }.flowOn(Dispatchers.IO)

    private fun buildBody(request: AiRequest, stream: Boolean): String = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxOutputTokens)
        put("system", request.systemPrompt)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", request.userPrompt)
            })
        })
        put("temperature", request.temperature)
        if (stream) put("stream", true)
    }.toString()

    private fun extractText(root: JsonObject): String {
        val content = root["content"]?.jsonArray ?: return ""
        val builder = StringBuilder()
        content.forEach { part ->
            val obj = part.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                obj["text"]?.jsonPrimitive?.contentOrNull?.let { builder.append(it) }
            }
        }
        return builder.toString()
    }

    private fun mapError(status: Int, raw: String): ConnectorError {
        val message = parseOrNull(raw)
            ?.get("error")
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull

        return when {
            status == 401 || status == 403 -> ConnectorError.InvalidApiKey
            status == 429 -> ConnectorError.RateLimited()
            status == 400 || status == 404 || status == 422 ->
                ConnectorError.BadRequest(message ?: "The provider rejected the request.")
            status >= 500 -> ConnectorError.Server(status)
            else -> ConnectorError.Unknown(message)
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_KEY_HEADER = "x-api-key"
        private const val API_VERSION_HEADER = "anthropic-version"
        private const val API_VERSION = "2023-06-01"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun parseOrNull(raw: String): JsonObject? = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }

        private fun JsonPrimitive.intOrNullSafe(): Int? = contentOrNull?.toDoubleOrNull()?.toInt()
    }
}
