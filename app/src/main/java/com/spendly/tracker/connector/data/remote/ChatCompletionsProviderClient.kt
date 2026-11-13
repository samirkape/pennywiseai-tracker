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
import io.ktor.http.HttpHeaders
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
 * Adapter for the `POST {base}/chat/completions` shape.
 *
 * Google AI Studio (Gemini), Groq and OpenRouter all expose this OpenAI-compatible
 * endpoint, so one adapter covers them. `temperature` is deliberately not sent:
 * several free models reject it, and the system prompt already shapes the answer.
 */
class ChatCompletionsProviderClient(
    private val httpClient: HttpClient,
    aiProvider: AiProvider,
    baseUrl: String,
    private val extraHeaders: Map<String, String> = emptyMap()
) : ProviderClient {

    override val provider: AiProvider = aiProvider

    private val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"

    override suspend fun complete(request: AiRequest, apiKey: String): AiResponse {
        val response = httpClient.post(endpoint) {
            authorize(apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildBody(request, stream = false))
        }

        val status = response.status.value
        val raw = response.bodyAsText()
        if (status !in 200..299) {
            throw ConnectorException(mapError(status, raw))
        }

        val root = parseOrNull(raw) ?: throw ConnectorException(ConnectorError.Unknown())
        val text = root["choices"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()

        val usage = root["usage"]?.jsonObject
        return AiResponse(
            text = text,
            provider = provider,
            model = request.model,
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNullSafe(),
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNullSafe()
        )
    }

    override fun stream(request: AiRequest, apiKey: String): Flow<AiStreamEvent> = flow {
        httpClient.prepareRequest {
            method = HttpMethod.Post
            url(endpoint)
            authorize(apiKey)
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
                if (payload == "[DONE]") return@read
                val event = parseOrNull(payload) ?: return@read

                // Some providers report a mid-stream error inside the SSE payload.
                event["error"]?.let { error ->
                    val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                        ?: error.jsonPrimitive.contentOrNull
                    throw ConnectorException(
                        ConnectorError.BadRequest(message ?: "The provider reported an error.")
                    )
                }

                val choice = event["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val delta = choice?.get("delta")?.jsonObject
                delta?.get("content")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { emit(AiStreamEvent.TextDelta(it)) }

                event["usage"]?.jsonObject?.let { usage ->
                    emit(
                        AiStreamEvent.Usage(
                            inputTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNullSafe(),
                            outputTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNullSafe()
                        )
                    )
                }
            }
        }
        emit(AiStreamEvent.Done)
    }.flowOn(Dispatchers.IO)

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(apiKey: String) {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        extraHeaders.forEach { (name, value) -> header(name, value) }
    }

    private fun buildBody(request: AiRequest, stream: Boolean): String = buildJsonObject {
        put("model", request.model)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", request.systemPrompt)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", request.userPrompt)
            })
        })
        put("max_tokens", request.maxOutputTokens)
        if (stream) put("stream", true)
    }.toString()

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
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun parseOrNull(raw: String): JsonObject? = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }

        private fun JsonPrimitive.intOrNullSafe(): Int? = contentOrNull?.toDoubleOrNull()?.toInt()
    }
}
