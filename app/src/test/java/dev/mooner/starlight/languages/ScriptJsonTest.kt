package dev.mooner.starlight.languages

import dev.mooner.starlight.languages.rhino.topLevelConsts
import dev.mooner.starlight.script.Engines
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context

/** Each engine's JSON as ScriptJson gives it: values handed back are the engine's own arrays and objects. */
class ScriptJsonTest {

    @Suppress("unused")
    class Box(private val json: ScriptJson) {
        fun parse(text: String): Any? = json.parse(text)
        fun stringify(value: Any?): String? = json.stringify(value)
        fun roundTrip(value: Any?, transform: (Any?) -> Any?): String? =
            json.stringify(transform(json.parse(json.stringify(value)!!)))
        fun items(): Any? = json.array(arrayOf(Item("a"), Item("b")))
        fun none(): Any? = json.array(emptyArray<Item>())
    }

    /** A host object, as a `ChatRoom` is, that the engine's array holds as it is. */
    @Suppress("unused")
    class Item(@JvmField val name: String) {
        fun shout(): String = name.uppercase()
    }

    private val engines = Engines { json -> mapOf("box" to Box(json)) }

    @After
    fun tearDown() = engines.close()

    @Test
    fun `parsed values are the engine's own arrays and objects`() {
        engines.both("Array.isArray(box.parse('[1,2]'))", "true")
        engines.both("box.parse('{\"a\":[1,{\"b\":2}]}').a[1].b", "2")
        engines.both("box.parse('[1,2]').map(function (x) { return x * 2; }).join(',')", "2,4")
        engines.both("JSON.stringify(box.parse('{\"a\":[1,2]}'))", "{\"a\":[1,2]}")
        engines.both("box.parse('null') === null", "true")
        engines.both("typeof box.parse('\"s\"')", "string")
        engines.both("box.parse('1') + 1", "2")
    }

    @Test
    fun `values are written as the script's JSON_stringify writes them`() {
        engines.both("box.stringify({a: [1, 'x', null, true], n: 1.5, i: 3})", "{\"a\":[1,\"x\",null,true],\"n\":1.5,\"i\":3}")
        engines.both("box.stringify(new Date(0))", "\"1970-01-01T00:00:00.000Z\"")
        engines.both("box.stringify(function () {}) === null", "true")
        engines.both(
            "box.roundTrip({n: 1}, function (old) { return {n: old.n + 1, list: [old.n]}; })",
            "{\"n\":2,\"list\":[1]}",
        )
    }

    @Test
    fun `undefined reaches the host as null in NeonJS and as text in Rhino`() {
        // What StoreFile.set's KDoc tells scripts.
        assertEquals("null", engines.neon("box.stringify(undefined)"))
        assertEquals("\"undefined\"", engines.rhino("box.stringify(undefined)"))
    }

    @Test
    fun `arrays are the engine's own and hold the host objects`() {
        engines.both("Array.isArray(box.items())", "true")
        engines.both("box.items().length", "2")
        engines.both("box.items().map(function (x) { return x.shout(); }).join(',')", "A,B")
        engines.both("box.items().filter(function (x) { return x.name == 'b'; }).length", "1")
        engines.both("box.items()[0].name", "a")
        engines.both("Array.isArray(box.none()) && box.none().length === 0", "true")
    }

    @Test
    fun `Plain keeps the array as it is`() {
        val items = arrayOf("a", "b")
        assertTrue(ScriptJson.Plain.array(items) === items)
    }

    @Test
    fun `Plain keeps Java maps and lists`() {
        val value = ScriptJson.Plain.parse("{\"a\":[1,2.5,\"x\",null,true]}") as Map<*, *>
        assertEquals(listOf(1L, 2.5, "x", null, true), value["a"])
        assertEquals("{\"a\":[1,2.5,\"x\",null,true]}", ScriptJson.Plain.stringify(value))
        assertNull(ScriptJson.Plain.stringify(Unit))
    }

    @Test
    fun `Rhino's top-level consts are found, and nothing else`() {
        val code = """
            const Store = 1, Chat = 2;
            let Schedule = 3;
            var Log = 4;
            function f() { const Inner = 5; }
            if (true) { const Block = 6; }
        """.trimIndent()
        assertEquals(setOf("Store", "Chat"), topLevelConsts(code, Context.VERSION_ES6))
        assertTrue(topLevelConsts("const = ;", Context.VERSION_ES6).isEmpty())
    }
}
