/*
 * HttpResponse.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.languages.ScriptJson
import java.nio.charset.Charset
import java.util.Collections
import java.util.Locale

/**
 * The answer to `Http.fetch` or `Http.fetchSync`: its status, headers and whole body.
 *
 * The body has arrived completely when a script gets the response, so `text()`, `json()` and `bytes()` never wait. Any
 * status is an answer, including 404 and 500: check [ok] or [status] rather than expecting an error. Read the
 * properties as `res.status` and `res.ok`, or call `res.getStatus()` and `res.getOk()`.
 *
 * ```js
 * const res = Http.fetchSync({ url: "https://example.com/api", query: { q: "star" } });
 * if (res.ok) Log.i("api", res.json().name);
 * ```
 */
class HttpResponse internal constructor(
    private val raw: RawResponse,
    private val scriptJson: ScriptJson,
) {

    /** Whether [status] is from 200 to 299. */
    val ok: Boolean
        get() = raw.status in 200..299

    /** The HTTP status code, such as `200` or `404`. */
    val status: Int
        get() = raw.status

    /** The reason phrase the server sent with the status, such as `"OK"`; empty when it sent none, as HTTP/2 doesn't. */
    val statusText: String
        get() = raw.statusText

    /** The address the response came from: the last one after redirects, with the `query` option added. */
    val url: String
        get() = raw.url

    /**
     * The first value of the header [name], or `null` when the response has none.
     *
     * @param name The header's name; capital letters don't matter.
     */
    fun header(name: String): String? =
        valuesOf(name).firstOrNull()

    /**
     * Every value of the header [name], one for each line the server sent; empty when the response has none.
     *
     * @param name The header's name; capital letters don't matter.
     */
    fun headers(name: String): List<String> =
        valuesOf(name)

    /**
     * All the headers: each name, in lower case, with the list of its values.
     *
     * The map can't be changed.
     */
    fun headers(): Map<String, List<String>> =
        Collections.unmodifiableMap(raw.headers)

    /**
     * The body decoded as text, in the charset of the `Content-Type` header, or UTF-8 when it names none. A byte order
     * mark is kept.
     */
    fun text(): String =
        String(raw.body, charset())

    /**
     * The body as the engine's own object, array or value, as `JSON.parse(res.text())` makes it; it is parsed again on
     * each call.
     *
     * Call it in the callback or after `fetchSync` returns, as the script itself would.
     *
     * @throws Error An engine error when the body isn't valid JSON.
     */
    fun json(): Any? =
        scriptJson.parse(text())

    /** The body as a Java `byte[]`; a new copy on each call. */
    fun bytes(): ByteArray =
        raw.body.copyOf()

    override fun toString(): String =
        "HttpResponse($status $url)"

    private fun valuesOf(name: String): List<String> =
        raw.headers[name.lowercase(Locale.ROOT)] ?: emptyList()

    private fun charset(): Charset {
        val type = raw.headers["content-type"]?.firstOrNull() ?: return Charsets.UTF_8
        val match = CHARSET.find(type) ?: return Charsets.UTF_8
        return try {
            Charset.forName(match.groupValues[1])
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    private companion object {
        val CHARSET = Regex("""charset\s*=\s*"?([^;"\s]+)""", RegexOption.IGNORE_CASE)
    }
}
