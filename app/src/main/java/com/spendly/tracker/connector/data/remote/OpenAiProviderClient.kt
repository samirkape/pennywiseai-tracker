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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Adapter for the OpenAI Responses API.
 *
 * The app never exposes OpenAI's payload shape outside of this file — callers work
 * with [AiRequest]/[AiStreamEvent] only.
 */
class OpenAiProviderClient(
    private val httpClient: HttpClient
) : ProviderClient {

    override val provider: AiProvider = AiProvider.OPENAI

    override suspend fun complete(request: AiRequest, apiKey: String): AiResponse {
        val response = httpClient.post(ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
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
            header(HttpHeaders.Authorization, "Bearer $apiKey")
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
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "response.output_text.delta" -> {
                        event["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                            emit(AiStreamEvent.TextDelta(it))
                        }
                    }
                    "response.completed" -> {
                        val usage = event["response"]?.jsonObject?.get("usage")?.jsonObject
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
        put("instructions", request.systemPrompt)
        put("input", request.userPrompt)
        put("max_output_tokens", request.maxOutputTokens)
        if (stream) put("stream", true)
    }.toString()

    /**
     * The Responses API nests the answer inside `output[]`; reasoning items may come
     * first, so every message item is scanned.
     */
    private fun extractText(root: JsonObject): String {
        val output = root["output"]?.jsonArray ?: return ""
        val builder = StringBuilder()
        output.forEach { item ->
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "message") return@forEach
            val content = obj["content"]?.jsonArray ?: return@forEach
            content.forEach { part ->
                val partObj = part.jsonObject
                if (partObj["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                    partObj["text"]?.jsonPrimitive?.contentOrNull?.let { builder.append(it) }
                }
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
        private const val ENDPOINT = "https://api.openai.com/v1/responses"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun parseOrNull(raw: String): JsonObject? = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }

        private fun JsonPrimitive.intOrNullSafe(): Int? =
            contentOrNull?.toDoubleOrNull()?.toInt() ?: doubleOrNull?.toInt()
    }
}
