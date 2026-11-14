/*
 * HttpFetch.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.script.ScriptTimers
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.ScheduledFuture

/**
 * What `Http.request` and `Http.fetch` take as their first argument: the URL, or an object of options. Both read the
 * options on the thread the script called from, as an engine's objects may only be read there.
 */
internal object RequestOptions {

    /** The first argument: [url] for the string form, or [options] for an object. */
    class Param(val url: String?, val options: Any?)

    fun split(param: Any?): Param {
        val value = ScriptValues.unwrap(param)
        return when {
            value is CharSequence          -> Param(value.toString(), null)
            ScriptValues.isObject(value)   -> Param(null, value)
            else -> throw IllegalArgumentException("Parameter must be a URL or request options")
        }
    }

    /** The `url` option, which an object must have as a string. */
    fun url(options: Any?): String {
        val raw = ScriptValues.member(options, "url") ?: throw IllegalArgumentException("url is required")
        return (raw as? CharSequence)?.toString() ?: throw IllegalArgumentException("url must be a string")
    }

    /** An option that must be a whole number that fits an `Int`; [default] when absent or `null`. */
    fun int(options: Any?, name: String, default: Int): Int {
        if (options == null)
            return default
        val raw = ScriptValues.member(options, name) ?: return default
        val result = when (raw) {
            is Int, is Short, is Byte -> (raw as Number).toInt()
            is Long                   -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            is Double, is Float       -> raw.toDouble().takeIf {
                it.isFinite() && it == Math.floor(it) && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE
            }?.toInt()
            else                      -> null
        }
        return result ?: throw IllegalArgumentException("$name must be an integer")
    }

    /** An option that must be a string; [default] when absent or `null`. */
    fun string(options: Any?, name: String, default: String): String {
        if (options == null)
            return default
        val raw = ScriptValues.member(options, name) ?: return default
        return (raw as? CharSequence)?.toString() ?: throw IllegalArgumentException("$name must be a string")
    }

    /** An option that must be an object of strings, numbers and booleans, read as text, in the object's order. */
    fun map(options: Any?, name: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        if (options == null)
            return result
        val raw = ScriptValues.member(options, name) ?: return result
        if (!ScriptValues.isObject(raw))
            throw IllegalArgumentException("$name must be an object")
        for (key in ScriptValues.keys(raw)) {
            val value = ScriptValues.member(raw, key)
            result[key] = when (value) {
                is CharSequence, is Boolean -> value.toString()
                is Number                   -> ScriptArgs.stringify(value)
                else -> throw IllegalArgumentException("$name.$key must be a string, number, or boolean")
            }
        }
        return result
    }
}

/** A `Http.fetch` request, checked and ready to send. */
internal class FetchSpec(
    val url: URL,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val contentType: String?,
    val timeoutMillis: Int,
    val maxResponseBytes: Int,
) {

    companion object {

        const val DEFAULT_TIMEOUT = 10_000
        const val MAX_TIMEOUT = 120_000
        const val DEFAULT_MAX_BYTES = 8 * 1024 * 1024
        const val MAX_MAX_BYTES = 32 * 1024 * 1024

        private val METHODS = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")

        /**
         * Reads the first argument of `fetch` and `fetchSync`.
         *
         * @throws IllegalArgumentException With the messages the options are checked with, in the order they are checked.
         */
        fun parse(param: Any?, json: ScriptJson): FetchSpec {
            val (urlText, options) = RequestOptions.split(param).let { it.url to it.options }
            val rawUrl = urlText ?: RequestOptions.url(options)
            val timeout = RequestOptions.int(options, "timeout", DEFAULT_TIMEOUT)
            val maxBytes = RequestOptions.int(options, "maxResponseBytes", DEFAULT_MAX_BYTES)
            val methodText = RequestOptions.string(options, "method", "GET")
            val headers = RequestOptions.map(options, "headers")
            val query = RequestOptions.map(options, "query")
            val form = options?.let { ScriptValues.member(it, "form") }?.let { RequestOptions.map(options, "form") }
            val bodyRaw = options?.let { ScriptValues.member(it, "body") }
            val jsonRaw = options?.let { ScriptValues.member(it, "json") }

            if (listOf(bodyRaw, jsonRaw, form).count { it != null } > 1)
                throw IllegalArgumentException("Use only one of body, json, or form")

            var defaultType = "text/plain; charset=utf-8"
            var body: ByteArray? = null
            when {
                bodyRaw != null -> {
                    if (bodyRaw !is CharSequence)
                        throw IllegalArgumentException("body must be a string; use json for objects")
                    body = bodyRaw.toString().toByteArray(Charsets.UTF_8)
                }
                jsonRaw != null -> {
                    val text = json.stringify(jsonRaw)
                        ?: throw IllegalArgumentException("json must be serializable")
                    body = text.toByteArray(Charsets.UTF_8)
                    defaultType = "application/json; charset=utf-8"
                }
                form != null -> {
                    body = form.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
                        .toByteArray(Charsets.UTF_8)
                    defaultType = "application/x-www-form-urlencoded; charset=utf-8"
                }
            }
            var contentType: String? = null
            if (body != null)
                contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
                    ?: defaultType

            // The checks of the call itself.
            if (timeout !in 1..MAX_TIMEOUT)
                throw IllegalArgumentException("timeout must be between 1 and $MAX_TIMEOUT ms")
            if (maxBytes !in 1..MAX_MAX_BYTES)
                throw IllegalArgumentException("maxResponseBytes must be between 1 and $MAX_MAX_BYTES")
            val url = buildUrl(rawUrl, query)
            val method = methodText.uppercase(Locale.ROOT)
            if (method !in METHODS)
                throw IllegalArgumentException("Unsupported HTTP method: $methodText")
            if (body != null && (method == "GET" || method == "HEAD"))
                throw IllegalArgumentException("$method cannot have a request body")
            if (contentType != null && !isMediaType(contentType))
                throw IllegalArgumentException("Invalid Content-Type: $contentType")
            if (body == null && (method == "POST" || method == "PUT" || method == "PATCH"))
                body = ByteArray(0)

            return FetchSpec(url, method, headers, body, contentType, timeout, maxBytes)
        }

        internal fun buildUrl(raw: String, query: Map<String, String>): URL {
            var text = raw.trim().substringBefore('#').replace(" ", "%20")
            var url = try {
                URL(text)
            } catch (_: java.net.MalformedURLException) {
                throw IllegalArgumentException("url must be an HTTP or HTTPS URL")
            }
            if ((url.protocol != "http" && url.protocol != "https") || url.host.isNullOrEmpty())
                throw IllegalArgumentException("url must be an HTTP or HTTPS URL")
            // An empty path is "/", as OkHttp's HttpUrl writes it: "https://host?q=1" becomes "https://host/?q=1".
            if (url.path.isEmpty()) {
                val at = text.indexOf('?').let { if (it < 0) text.length else it }
                text = text.substring(0, at) + "/" + text.substring(at)
                url = URL(text)
            }
            if (query.isEmpty())
                return url
            val added = query.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
            val separator = when {
                !text.contains('?')                 -> "?"
                text.endsWith("?") || text.endsWith("&") -> ""
                else                                -> "&"
            }
            return URL(text + separator + added.replace("+", "%20"))
        }

        private fun encode(text: String): String =
            URLEncoder.encode(text, "UTF-8")

        /** `type/subtype` with optional parameters, the types made of token characters. */
        internal fun isMediaType(value: String): Boolean {
            val main = value.substringBefore(';').trim()
            val slash = main.indexOf('/')
            if (slash <= 0 || slash == main.length - 1)
                return false
            return main.all { it == '/' || isToken(it) } && main.count { it == '/' } == 1
        }

        private fun isToken(c: Char): Boolean =
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "!#$%&'*+-.^_`|~"
    }
}

/** What came back from a call: the body is whole, so reading it never waits. */
internal class RawResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
)

/** One call in flight, which can be cancelled from any thread. */
internal class CallHandle {

    @Volatile var canceled = false
        private set
    @Volatile var timedOut = false
        private set
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        canceled = true
        connection?.disconnect()
    }

    fun timeOut() {
        timedOut = true
        connection?.disconnect()
    }

    fun attach(connection: HttpURLConnection?) {
        this.connection = connection
        if (connection != null && (canceled || timedOut))
            connection.disconnect()
    }

    /** @throws IOException When the call was cancelled or ran out of time. */
    fun check() {
        if (canceled)
            throw IOException("Canceled")
        if (timedOut)
            throw SocketTimeoutException("timeout")
    }
}

/** Sends [FetchSpec]s on `HttpURLConnection`. */
internal object HttpExecutor {

    private const val MAX_REDIRECTS = 20

    /**
     * Sends [spec] and reads the whole response; any status is a response.
     *
     * The time limit covers the whole call. Redirects are followed, between `http` and `https` too.
     *
     * @throws IOException When the call fails, times out, is cancelled, or the body is over the limit.
     */
    fun execute(spec: FetchSpec, handle: CallHandle): RawResponse {
        val deadline = System.nanoTime() + spec.timeoutMillis * 1_000_000L
        val watchdog: ScheduledFuture<*> = ScriptTimers.schedule(spec.timeoutMillis.toLong()) { handle.timeOut() }
        try {
            return run(spec, handle, deadline)
        } catch (e: IOException) {
            handle.check()
            throw e
        } catch (e: RuntimeException) {
            handle.check()
            throw e
        } finally {
            watchdog.cancel(false)
            handle.attach(null)
        }
    }

    private fun run(spec: FetchSpec, handle: CallHandle, deadline: Long): RawResponse {
        var url = spec.url
        var method = spec.method
        var body = spec.body
        var contentType = spec.contentType
        var headers = spec.headers
        var hops = 0
        while (true) {
            handle.check()
            val remaining = ((deadline - System.nanoTime()) / 1_000_000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            val conn = url.openConnection() as HttpURLConnection
            try {
                handle.attach(conn)
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connectTimeout = remaining
                conn.readTimeout = remaining
                val override = applyMethod(conn, method)
                for ((name, value) in headers)
                    conn.setRequestProperty(name, value)
                if (override != null && headers.keys.none { it.equals("X-HTTP-Method-Override", true) })
                    conn.setRequestProperty("X-HTTP-Method-Override", override)
                if (body != null && contentType != null)
                    conn.setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body!!) }
                }
                val status = conn.responseCode
                if (status < 0)
                    throw IOException("Invalid HTTP response")
                val location = conn.getHeaderField("Location")
                if (status in REDIRECTS && location != null) {
                    val next = try { URL(url, location) } catch (_: java.net.MalformedURLException) { null }
                    if (next != null && (next.protocol == "http" || next.protocol == "https")) {
                        if (++hops > MAX_REDIRECTS)
                            throw ProtocolException("Too many follow-up requests: $hops")
                        if (next.host != url.host || next.port != url.port)
                            headers = headers.filterKeys { !it.equals("Authorization", true) }
                        if (status != 307 && status != 308 && method != "GET" && method != "HEAD") {
                            method = "GET"
                            body = null
                            contentType = null
                            headers = headers.filterKeys { !it.equals("Content-Type", true) }
                        }
                        url = next
                        continue
                    }
                }
                return read(conn, status, url, spec.maxResponseBytes, method)
            } finally {
                conn.disconnect()
            }
        }
    }

    private val REDIRECTS = setOf(301, 302, 303, 307, 308)

    /** Sets the method; returns the method to ask for in a header when the platform can't send it. */
    private fun applyMethod(conn: HttpURLConnection, method: String): String? =
        try {
            conn.requestMethod = method
            null
        } catch (e: ProtocolException) {
            if (method != "PATCH")
                throw e
            conn.requestMethod = "POST"
            "PATCH"
        }

    private fun read(conn: HttpURLConnection, status: Int, url: URL, max: Int, method: String): RawResponse {
        // HEAD, 204 and 304 have no body, whatever Content-Length says.
        val hasBody = method != "HEAD" && status != 204 && status != 304
        val declared = conn.contentLengthLong
        if (hasBody && declared > max)
            throw IOException("Response exceeds maxResponseBytes ($max)")
        val bytes = if (!hasBody) ByteArray(0) else {
            val stream = if (status >= 400) conn.errorStream else try {
                conn.inputStream
            } catch (_: IOException) {
                conn.errorStream
            }
            stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > max)
                        throw IOException("Response exceeds maxResponseBytes ($max)")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: ByteArray(0)
        }
        return RawResponse(status, conn.responseMessage ?: "", url.toString(), lowerCased(conn.headerFields), bytes)
    }

    /** Header names in lower case, in name order, one list entry per header line; the platform's own are left out. */
    internal fun lowerCased(fields: Map<String?, List<String>>): Map<String, List<String>> {
        val result = TreeMap<String, MutableList<String>>()
        for ((name, values) in fields) {
            if (name == null)
                continue
            val key = name.lowercase(Locale.ROOT)
            if (key.startsWith("x-android-"))
                continue
            result.getOrPut(key) { ArrayList() }.addAll(values)
        }
        return result
    }
}
