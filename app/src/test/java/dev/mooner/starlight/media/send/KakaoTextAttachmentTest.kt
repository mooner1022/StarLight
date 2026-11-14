package dev.mooner.starlight.media.send

import dev.mooner.starlight.media.script.TextOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The attachment KakaoTalk's share screen reads beside a shared text, as checked on KakaoTalk 26.9.1. */
class KakaoTextAttachmentTest {

    private fun parse(json: String?) = Json.parseToJsonElement(json!!).jsonObject

    @Test
    fun `nothing to add gives no attachment`() {
        assertNull(KakaoTextAttachment.of("hi", false, emptyList(), emptyList()).toJson())
    }

    @Test
    fun `markdown is a flag`() {
        assertEquals("""{"markdown":true}""", KakaoTextAttachment(markdown = true).toJson())
    }

    @Test
    fun `spoilers are written as loc and len, counted from 0`() {
        val json = parse(KakaoTextAttachment.of("스포일러: 범인은 집사다", false, listOf(KakaoTextAttachment.Spoiler(6, 7)), emptyList()).toJson())
        val spoiler = json["spoilers"]!!.jsonArray.single().jsonObject
        assertEquals(6, spoiler["loc"]!!.jsonPrimitive.int)
        assertEquals(7, spoiler["len"]!!.jsonPrimitive.int)
    }

    @Test
    fun `spoilers in a markdown text are refused, as KakaoTalk drops them`() {
        assertThrows(IllegalArgumentException::class.java) {
            KakaoTextAttachment.of("**a** secret", true, listOf(KakaoTextAttachment.Spoiler(6, 6)), emptyList())
        }
    }

    @Test
    fun `a spoiler past the text is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            KakaoTextAttachment.of("short", false, listOf(KakaoTextAttachment.Spoiler(3, 5)), emptyList())
        }
    }

    @Test
    fun `emoticons take the next placeholders, and totals count every place`() {
        val text = "hi (heart) and (heart) and (star)"
        val attachment = KakaoTextAttachment.of(text, false, emptyList(), listOf(
            KakaoTextAttachment.Placement("1234567_1"),
            KakaoTextAttachment.Placement("1234567_1"),
            KakaoTextAttachment.Placement("7654321_2"),
        ))
        val emojis = parse(attachment.toJson())["emojis"]!!.jsonObject
        assertEquals(3, emojis["total_item"]!!.jsonPrimitive.int)
        assertEquals(7 + 7 + 6, emojis["total_len"]!!.jsonPrimitive.int)
        val items = emojis["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals("1234567_1", items[0]["id"]!!.jsonPrimitive.content)
        assertEquals(listOf(1, 2), items[0]["at"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(7, items[0]["len"]!!.jsonPrimitive.int)
        assertEquals(listOf(3), items[1]["at"]!!.jsonArray.map { it.jsonPrimitive.int })
    }

    @Test
    fun `an emoticon can name its placeholder and length`() {
        val attachment = KakaoTextAttachment.of("(a) (bb)", false, emptyList(),
            listOf(KakaoTextAttachment.Placement("1_1", at = 2, length = 4)))
        assertEquals(listOf(KakaoTextAttachment.Emoticon("1_1", listOf(2), 4)), attachment.emoticons)
    }

    @Test
    fun `bad emoticons are refused with a reason`() {
        val bad = listOf(
            listOf(KakaoTextAttachment.Placement("heart")),                                   // not item_number
            listOf(KakaoTextAttachment.Placement("1_1"), KakaoTextAttachment.Placement("1_2")), // one placeholder only
            listOf(KakaoTextAttachment.Placement("1_1", at = 1), KakaoTextAttachment.Placement("1_2", at = 1)),
        )
        for (placements in bad)
            assertThrows(IllegalArgumentException::class.java) {
                KakaoTextAttachment.of("x (heart)", false, emptyList(), placements)
            }
        assertThrows(IllegalArgumentException::class.java) {
            KakaoTextAttachment.of("(a very long placeholder)", false, emptyList(), listOf(KakaoTextAttachment.Placement("1_1")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KakaoTextAttachment.of("(open", false, emptyList(), listOf(KakaoTextAttachment.Placement("1_1")))
        }
    }

    @Test
    fun `script options read markdown, spoilers in both forms, emoticons and leave`() {
        val options = TextOptions.read(mapOf(
            "markdown" to true,
            "spoilers" to listOf(mapOf("start" to 0.0, "length" to 2.0), listOf(3.0, 1.0)),
            "emoticons" to listOf("1_1", mapOf("id" to "2_2", "at" to 2.0)),
            "leave" to "home",
        ))
        assertTrue(options.markdown)
        assertEquals(listOf(KakaoTextAttachment.Spoiler(0, 2), KakaoTextAttachment.Spoiler(3, 1)), options.spoilers)
        assertEquals(listOf(KakaoTextAttachment.Placement("1_1"), KakaoTextAttachment.Placement("2_2", at = 2)), options.emoticons)
        assertEquals(LeaveTo.Home, options.leaveTo)
    }

    @Test
    fun `numbers too large for an Int are refused, not wrapped around`() {
        val huge = 4_294_967_297.0  // 2^32 + 1, which would wrap to 1
        assertThrows(IllegalArgumentException::class.java) {
            TextOptions.read(mapOf("spoilers" to listOf(listOf(huge, 1.0))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TextOptions.read(mapOf("emoticons" to listOf(mapOf("id" to "1_1", "at" to huge))))
        }
    }

    @Test
    fun `unknown text options are refused`() {
        assertThrows(IllegalArgumentException::class.java) { TextOptions.read(mapOf("bold" to true)) }
    }
}
