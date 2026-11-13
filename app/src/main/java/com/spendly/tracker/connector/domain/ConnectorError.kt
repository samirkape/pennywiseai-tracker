package com.spendly.tracker.connector.domain

/**
 * Normalised failures. Provider adapters map their vendor error payloads onto these
 * so the UI never has to know which provider was used.
 */
sealed class ConnectorError(
    val userMessage: String,
    val retriable: Boolean
) {
    /** No API key configured for the selected provider. */
    data object MissingApiKey : ConnectorError(
        userMessage = "Add your API key in Settings → AI Connector to run an analysis.",
        retriable = false
    )

    /** The connector is switched off in Settings. */
    data object Disabled : ConnectorError(
        userMessage = "The AI connector is turned off. Enable it in Settings.",
        retriable = false
    )

    /** The provider rejected the key. */
    data object InvalidApiKey : ConnectorError(
        userMessage = "That API key was rejected. Double-check it and try again.",
        retriable = false
    )

    /** Provider quota exhausted. */
    data class RateLimited(val retryAfterSeconds: Long? = null) : ConnectorError(
        userMessage = "Rate limit or quota reached. Wait a moment and try again.",
        retriable = true
    )

    /** The request never reached the provider. */
    data class Network(val cause: String? = null) : ConnectorError(
        userMessage = "Could not reach the provider. Check your connection and try again.",
        retriable = true
    )

    /** Provider-side failure. */
    data class Server(val statusCode: Int) : ConnectorError(
        userMessage = "The provider returned an error ($statusCode). Please try again.",
        retriable = true
    )

    /** The request was rejected (bad model id, malformed payload, …). */
    data class BadRequest(val providerMessage: String) : ConnectorError(
        userMessage = providerMessage,
        retriable = false
    )

    /** Anything we could not classify. */
    data class Unknown(val cause: String? = null) : ConnectorError(
        userMessage = "Something went wrong while talking to the provider.",
        retriable = true
    )
}

/** Carrier for a [ConnectorError] so it can travel through coroutine boundaries. */
class ConnectorException(val error: ConnectorError) : Exception(error.userMessage)
