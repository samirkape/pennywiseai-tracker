package com.spendly.tracker.connector.domain

/**
 * Events emitted while a provider streams its answer back. Adapters normalise
 * vendor specific SSE frames into these.
 */
sealed interface AiStreamEvent {
    /** A chunk of generated text. */
    data class TextDelta(val text: String) : AiStreamEvent

    /** Token accounting, when the provider reports it. */
    data class Usage(val inputTokens: Int?, val outputTokens: Int?) : AiStreamEvent

    /** The provider finished (successfully or not). */
    data object Done : AiStreamEvent
}
