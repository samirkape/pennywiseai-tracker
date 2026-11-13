package com.spendly.tracker.connector.data.remote

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * Minimal Server-Sent Events reader.
 *
 * Both providers stream their answer as `text/event-stream`; this helper isolates the
 * framing so each adapter only has to deal with the JSON payload.
 */
internal object SseReader {

    /**
     * Reads the channel until it is closed, invoking [onData] for every `data:` payload.
     */
    suspend fun read(channel: ByteReadChannel, onData: suspend (String) -> Unit) {
        var buffer = StringBuilder()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            when {
                // Blank line terminates an event.
                line.isEmpty() -> {
                    if (buffer.isNotEmpty()) {
                        onData(buffer.toString())
                        buffer = StringBuilder()
                    }
                }
                line.startsWith("data:") -> {
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(line.removePrefix("data:").trim())
                }
                // "event:", "id:", "retry:" and comment lines carry no payload for us.
                else -> Unit
            }
        }
        if (buffer.isNotEmpty()) onData(buffer.toString())
    }
}
