package dev.mooner.starlight.agent.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** An HTTP error response. */
class HttpStatusException(
    val code: Int,
    val body: String,
    /** From `retry-after`, if the server sent it. */
    val retryAfterMillis: Long?,
) : IOException("HTTP $code: ${body.take(500)}") {

    /** Rate limits, overloads and server errors, which are worth trying again. */
    val isRetryable: Boolean
        get() = code == 408 || code == 409 || code == 429 || code >= 500
}

/** One server-sent event. */
class SseEvent(val event: String?, val data: String)

/**
 * Plain [HttpURLConnection] requests. Blocking reads run on [Dispatchers.IO], and cancelling the calling coroutine
 * disconnects the connection, which ends a read that is waiting for the server.
 */
object Http {

    private const val CONNECT_TIMEOUT_MILLIS = 20_000
    /** Long enough for a model that thinks for a while before its first token; streams send pings meanwhile. */
    private const val READ_TIMEOUT_MILLIS = 300_000

    suspend fun get(url: String, headers: Map<String, String>): String =
        request("GET", url, headers, body = null) { it.readText() }

    suspend fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>): String {
        val body = form.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return request("POST", url, headers + ("Content-Type" to "application/x-www-form-urlencoded"), body.toByteArray()) { it.readText() }
    }

    /** POSTs [body] and calls [onEvent] for each server-sent event of the response until the stream ends. */
    suspend fun postSse(url: String, headers: Map<String, String>, body: String, onEvent: suspend (SseEvent) -> Unit) {
        val allHeaders = headers + mapOf("Content-Type" to "application/json", "Accept" to "text/event-stream")
        request("POST", url, allHeaders, body.toByteArray()) { input ->
            readSse(input.bufferedReader(), onEvent)
        }
    }

    /** Parses server-sent events from [reader]; see the WHATWG event stream format. */
    suspend fun readSse(reader: BufferedReader, onEvent: suspend (SseEvent) -> Unit) {
        var event: String? = null
        val data = StringBuilder()
        var hasData = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    if (hasData)
                        onEvent(SseEvent(event, data.toString()))
                    event = null
                    data.setLength(0)
                    hasData = false
                }
                line.startsWith(":") -> Unit
                else -> {
                    val colon = line.indexOf(':')
                    val field = if (colon < 0) line else line.substring(0, colon)
                    var value = if (colon < 0) "" else line.substring(colon + 1)
                    if (value.startsWith(" "))
                        value = value.substring(1)
                    when (field) {
                        "event" -> event = value
                        "data"  -> {
                            if (hasData)
                                data.append('\n')
                            data.append(value)
                            hasData = true
                        }
                    }
                }
            }
        }
        if (hasData)
            onEvent(SseEvent(event, data.toString()))
    }

    private suspend fun <T> request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        read: suspend (InputStream) -> T,
    ): T = coroutineScope {
        val connection = withContext(Dispatchers.IO) { URL(url).openConnection() as HttpURLConnection }
        // Disconnecting is the only way to stop a blocking read of HttpURLConnection.
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                for ((name, value) in headers)
                    connection.setRequestProperty(name, value)
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = runCatching { connection.errorStream?.readText() }.getOrNull().orEmpty()
                    throw HttpStatusException(code, error, parseRetryAfter(connection.getHeaderField("retry-after")))
                }
                connection.inputStream.use { read(it) }
            }
        } finally {
            watcher.cancel()
            connection.disconnect()
        }
    }

    private fun InputStream.readText(): String =
        bufferedReader().use { it.readText() }

    private fun parseRetryAfter(value: String?): Long? =
        value?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() }

    fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
