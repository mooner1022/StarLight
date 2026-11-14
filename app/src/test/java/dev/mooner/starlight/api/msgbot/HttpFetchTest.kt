package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.languages.ScriptJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpFetchTest {

    private val json = ScriptJson.Plain

    private fun parse(param: Any?) = FetchSpec.parse(param, json)

    private fun fails(message: String, param: Any?) {
        val e = assertThrows(IllegalArgumentException::class.java) { parse(param) }
        assertEquals(message, e.message)
    }

    @Test
    fun aUrlStringHasTheDefaults() {
        val spec = parse("https://example.com/a")
        assertEquals("GET", spec.method)
        assertEquals(10_000, spec.timeoutMillis)
        assertEquals(8 * 1024 * 1024, spec.maxResponseBytes)
        assertNull(spec.body)
    }

    @Test
    fun queryIsAppendedAndEncoded() {
        val spec = parse(mapOf("url" to "https://example.com/a?x=1", "query" to mapOf("q" to "a b&c", "n" to 2.0, "t" to true)))
        assertEquals("https://example.com/a?x=1&q=a%20b%26c&n=2&t=true", spec.url.toString())
    }

    @Test
    fun jsonBodyGetsItsDefaultContentType() {
        val spec = parse(mapOf("url" to "https://example.com", "method" to "post", "json" to mapOf("a" to 1L)))
        assertEquals("POST", spec.method)
        assertEquals("{\"a\":1}", String(spec.body!!))
        assertEquals("application/json; charset=utf-8", spec.contentType)
    }

    @Test
    fun formIsUrlEncodedAndAHeaderOverridesTheType() {
        val spec = parse(mapOf("url" to "https://example.com", "method" to "POST",
            "form" to mapOf("a" to "x y", "b" to 1.5), "headers" to mapOf("content-type" to "text/plain")))
        assertEquals("a=x+y&b=1.5", String(spec.body!!))
        assertEquals("text/plain", spec.contentType)
    }

    @Test
    fun postWithoutABodySendsAnEmptyOne() {
        assertArrayEquals(ByteArray(0), parse(mapOf("url" to "http://e.com", "method" to "PUT")).body)
        assertNull(parse(mapOf("url" to "http://e.com", "method" to "DELETE")).body)
    }

    @Test
    fun optionsAreChecked() {
        fails("Parameter must be a URL or request options", 5.0)
        fails("url is required", mapOf<String, Any>())
        fails("url must be a string", mapOf("url" to 1.0))
        fails("url must be an HTTP or HTTPS URL", "ftp://example.com")
        fails("url must be an HTTP or HTTPS URL", "not a url")
        fails("timeout must be an integer", mapOf("url" to "http://e.com", "timeout" to "5000"))
        fails("timeout must be an integer", mapOf("url" to "http://e.com", "timeout" to 1.5))
        fails("timeout must be between 1 and 120000 ms", mapOf("url" to "http://e.com", "timeout" to 0.0))
        fails("maxResponseBytes must be between 1 and 33554432", mapOf("url" to "http://e.com", "maxResponseBytes" to 40_000_000L))
        fails("Unsupported HTTP method: TRACE", mapOf("url" to "http://e.com", "method" to "TRACE"))
        fails("headers must be an object", mapOf("url" to "http://e.com", "headers" to "x"))
        fails("headers.a must be a string, number, or boolean", mapOf("url" to "http://e.com", "headers" to mapOf("a" to listOf(1))))
        fails("method must be a string", mapOf("url" to "http://e.com", "method" to 1.0))
        fails("body must be a string; use json for objects", mapOf("url" to "http://e.com", "method" to "POST", "body" to mapOf("a" to 1)))
        fails("Use only one of body, json, or form", mapOf("url" to "http://e.com", "method" to "POST", "body" to "x", "json" to "y"))
        fails("GET cannot have a request body", mapOf("url" to "http://e.com", "body" to "x"))
        fails("HEAD cannot have a request body", mapOf("url" to "http://e.com", "method" to "head", "json" to 1L))
        fails("Invalid Content-Type: nonsense", mapOf("url" to "http://e.com", "method" to "POST", "body" to "x",
            "headers" to mapOf("Content-Type" to "nonsense")))
    }

    @Test
    fun mediaTypes() {
        assertTrue(FetchSpec.isMediaType("text/plain; charset=utf-8"))
        assertTrue(FetchSpec.isMediaType("application/vnd.api+json"))
        assertFalse(FetchSpec.isMediaType("text"))
        assertFalse(FetchSpec.isMediaType("/x"))
        assertFalse(FetchSpec.isMediaType("a/b/c"))
    }

    @Test
    fun responseHeadersAreLowerCasedAndPlatformOnesDropped() {
        val headers = HttpExecutor.lowerCased(mapOf(
            null to listOf("HTTP/1.1 200 OK"),
            "Content-Type" to listOf("text/html"),
            "Set-Cookie" to listOf("a=1", "b=2"),
            "X-Android-Sent-Millis" to listOf("1"),
        ))
        assertEquals(listOf("content-type", "set-cookie"), headers.keys.toList())
        assertEquals(listOf("a=1", "b=2"), headers["set-cookie"])
    }

    @Test
    fun responseReadsHeadersTextAndJson() {
        val raw = RawResponse(201, "Created", "https://e.com/x", mapOf(
            "content-type" to listOf("application/json; charset=ISO-8859-1"),
            "x-a" to listOf("1", "2"),
        ), "{\"k\":\"é\"}".toByteArray(Charsets.ISO_8859_1))
        val res = HttpResponse(raw, ScriptJson.Plain)
        assertTrue(res.ok)
        assertEquals(201, res.status)
        assertEquals("1", res.header("X-A"))
        assertNull(res.header("missing"))
        assertEquals(listOf("1", "2"), res.headers("x-A"))
        assertTrue(res.headers("none").isEmpty())
        assertEquals(mapOf("k" to "é"), res.json())
        assertFalse(res.bytes() === res.bytes())
    }
}
