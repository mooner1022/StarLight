/*
 * UtilsSupport.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

/** The pure parts of legacy `Utils`: text helpers, user agents and XML. */
internal object UtilsSupport {

    private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private const val RLO = '‮'

    /** [length] random letters and digits; empty for `0` or less. */
    fun randomAlphanumeric(length: Int, random: Random = Random.Default): String =
        buildString {
            repeat(length.coerceAtLeast(0)) { append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)]) }
        }

    /** [text] without any right-to-left override characters. */
    fun removeRLO(text: String): String =
        text.replace(RLO.toString(), "")

    // Browser families and how likely each is, in percent. The sum is below 100: the rest falls to Chrome.
    private val browserWeights: List<Pair<String, Double>> = listOf(
        "Internet Explorer" to 11.8,
        "Firefox" to 28.2,
        "Chrome" to 52.9,
        "Safari" to 3.9,
        "Opera" to 1.8,
    )

    private val userAgents: Map<String, List<String>> = mapOf(
        "Internet Explorer" to listOf(
            "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; AS; rv:11.0) like Gecko",
            "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.2; Trident/6.0)",
            "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)",
        ),
        "Firefox" to listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0",
            "Mozilla/5.0 (X11; Linux x86_64; rv:102.0) Gecko/20100101 Firefox/102.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/118.0",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/54.0",
        ),
        "Chrome" to listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36",
        ),
        "Safari" to listOf(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Safari/605.1.15",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.2 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/534.57.2 (KHTML, like Gecko) Version/5.1.7 Safari/534.57.2",
        ),
        "Opera" to listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 OPR/105.0.0.0",
            "Opera/9.80 (Windows NT 6.1; WOW64) Presto/2.12.388 Version/12.18",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.85 Safari/537.36 OPR/32.0.1948.25",
        ),
    )

    /**
     * A user agent: the family is chosen by weight from [percent] (`0` to `100`), and [pick] (`0` up to, not including,
     * `1`) chooses one of its strings.
     */
    fun userAgent(percent: Double, pick: Double): String {
        var total = 0.0
        var family = "Chrome"
        for ((candidate, weight) in browserWeights) {
            total += weight
            if (percent <= total) {
                family = candidate
                break
            }
        }
        val list = userAgents.getValue(family)
        return list[(pick * list.size).toInt().coerceIn(0, list.lastIndex)]
    }

    fun randomUserAgent(random: Random = Random.Default): String =
        userAgent(random.nextDouble() * 100, random.nextDouble())

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }

    /**
     * [xml] as a JSON text that mirrors its structure: each element is an object with `namespace` (when it has one),
     * `localname`, `attributes` (when it has some) and `content` (when it has children).
     *
     * Whitespace between elements is dropped; other text becomes a string in `content`.
     *
     * @throws IllegalArgumentException When [xml] isn't well-formed.
     */
    fun xmlToJson(xml: String): String {
        val root = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
                .documentElement
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid XML: ${e.message}", e)
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root.toJson())
    }

    private fun Element.toJson(): JsonElement {
        val fields = linkedMapOf<String, JsonElement>()
        namespaceURI?.takeIf(String::isNotEmpty)?.let { fields["namespace"] = JsonPrimitive(it) }
        fields["localname"] = JsonPrimitive(localName ?: tagName)

        val attrs = linkedMapOf<String, JsonElement>()
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.namespaceURI == XMLNS || attr.nodeName == "xmlns" || attr.nodeName.startsWith("xmlns:"))
                continue
            attrs[attr.localName ?: attr.nodeName] = JsonPrimitive(attr.nodeValue)
        }
        if (attrs.isNotEmpty())
            fields["attributes"] = JsonObject(attrs)

        val content = mutableListOf<JsonElement>()
        var child: Node? = firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.ELEMENT_NODE -> content += (child as Element).toJson()
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                    child.nodeValue?.takeIf { it.isNotBlank() }?.let { content += JsonPrimitive(it) }
            }
            child = child.nextSibling
        }
        if (content.isNotEmpty())
            fields["content"] = JsonArray(content)
        return JsonObject(fields)
    }

    private const val XMLNS = "http://www.w3.org/2000/xmlns/"
}
