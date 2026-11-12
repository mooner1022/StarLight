package dev.mooner.starlight.script.store

import dev.mooner.starlight.api.original.StoreApi
import dev.mooner.starlight.script.Engines
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Home(override val directory: File) : StoreData.StoreHome {
        val warnings = mutableListOf<String>()
        override fun warn(message: String) { warnings += message }
        override fun error(message: String) { warnings += message }
    }

    private val project by lazy { tmp.newFolder("project") }
    private val home by lazy { Home(project) }

    /** One StoreData for each name, shared by both engines' Store objects, as a project's compiles share them. */
    private val stores = mutableMapOf<String, StoreData>()
    private fun dataOf(name: String) = stores.getOrPut(name) { StoreData(name, home) }

    private val engines by lazy { Engines { json -> mapOf("Store" to StoreApi.Store(json, ::dataOf)) } }

    private fun file(name: String) = File(project, "data/store/$name.json")

    @After
    fun tearDown() = engines.close()

    @Test
    fun `values come back as copies, in the engine's own form`() {
        engines.both("(function(){ var s = Store.open('a'); s.set('k', {x: [1, 2]}); return s.get('k').x.length; })()", "2")
        engines.both("Array.isArray(Store.open('a').get('k').x)", "true")
        engines.both("(function(){ var s = Store.open('a'); var v = s.get('k'); v.x.push(3); return s.get('k').x.length; })()", "2")
        engines.both("Store.open('a').get('missing')", "null")
        engines.both("Store.open('a').get('missing', 7)", "7")
        engines.both("Store.open('a').set('n', null).get('n', 7)", "null")
        engines.both("Store.open('a').has('n')", "true")
        engines.both("Array.isArray(Store.open('a').keys()) + ' ' + Store.open('a').keys().join(',')", "true k,n")
        engines.both("Store.open('a').size", "2")
        engines.both("JSON.stringify(Store.open('a').getAll())", "{\"k\":{\"x\":[1,2]},\"n\":null}")
        engines.both("Store.open('a') === Store.open('a')", "true")
    }

    @Test
    fun `update changes a value from its old one`() {
        // Both engines' objects share the store, as a project's compiles do.
        val increment = "Store.open('c').update('n', function (n) { return (n === null ? 0 : n) + 1; })"
        assertEquals("1", engines.neon(increment))
        assertEquals("2", engines.rhino(increment))
        assertEquals("3", engines.neon(increment))
        engines.both("Store.open('c').get('n')", "3")
        val push = "Store.open('c').update('o', function (o) { o = o || {list: []}; o.list.push(1); return o; }).list.length"
        assertEquals("1", engines.neon(push))
        assertEquals("2", engines.rhino(push))
    }

    @Test
    fun `delete and clear remove values`() {
        engines.both("(function(){ var s = Store.open('d'); s.set('a', 1); return s.delete('a') + ' ' + s.delete('a') + ' ' + s.has('a'); })()", "true false false")
        engines.both("(function(){ var s = Store.open('d'); s.set('a', 1).set('b', 2); s.clear(); return s.size; })()", "0")
    }

    @Test
    fun `values without a JSON form are refused`() {
        val (neon, rhino) = engines.errors("Store.open('e').set('f', function () {})")
        assertTrue(neon, neon.contains("can't be kept"))
        assertTrue(rhino, rhino.contains("can't be kept"))
    }

    @Test
    fun `bad names are refused`() {
        for (name in listOf("", ".hidden", "a/b", "..", "a\\\\b"))
            assertTrue(name, engines.errors("Store.open('$name')").first.contains("can't be used"))
        engines.both("Store.open('점수_1.v2').name", "점수_1.v2")
    }

    @Test
    fun `save writes the whole store as JSON, through a temporary file`() {
        engines.neon("Store.open('s').set('a', {b: [1, 'x']}).set('c', true).save()")
        val saved = Json.parseToJsonElement(file("s").readText()).jsonObject
        assertEquals("{\"a\":{\"b\":[1,\"x\"]},\"c\":true}", saved.toString())
        assertFalse(File(file("s").path + ".tmp").exists())
    }

    @Test
    fun `an edit to the file is read when the store opens again`() {
        engines.neon("Store.open('r').set('a', 1).save()")
        file("r").writeText("{\"a\": 2, \"b\": 3}")
        file("r").setLastModified(file("r").lastModified() + 2_000)
        engines.both("Store.open('r').get('a') + Store.open('r').get('b')", "5")
    }

    @Test
    fun `a save keeps an edit made meanwhile under the script's own changes`() {
        val data = dataOf("m")
        data.open()
        data.set("mine", JsonPrimitive(1))
        data.set("both", JsonPrimitive("script"))
        data.save()
        data.set("both", JsonPrimitive("script again"))
        data.delete("mine")
        file("m").writeText("{\"mine\": 1, \"both\": \"edit\", \"theirs\": true}")
        file("m").setLastModified(file("m").lastModified() + 2_000)
        data.save()
        assertEquals("{\"both\":\"script again\",\"theirs\":true}", Json.parseToJsonElement(file("m").readText()).toString())
    }

    @Test
    fun `a file that isn't a store is never overwritten`() {
        file("b").parentFile!!.mkdirs()
        file("b").writeText("not json")
        val (neon, rhino) = engines.errors("Store.open('b')")
        assertTrue(neon, neon.contains("can't be read"))
        assertTrue(rhino, rhino.contains("can't be read"))
        assertEquals("not json", file("b").readText())

        // One broken while the script had changes waiting is kept beside it.
        val data = dataOf("w")
        data.open()
        data.set("a", JsonPrimitive(1))
        data.save()
        data.set("a", JsonPrimitive(2))
        file("w").writeText("{ broken")
        file("w").setLastModified(file("w").lastModified() + 2_000)
        data.save()
        assertEquals("{ broken", File(project, "data/store/w.broken.json").readText())
        assertEquals("{\"a\":2}", Json.parseToJsonElement(file("w").readText()).toString())
        assertEquals(1, home.warnings.size)
    }

    @Test
    fun `nothing is written once the project's folder is gone`() {
        val data = dataOf("g")
        data.open()
        data.set("a", JsonPrimitive(1))
        project.deleteRecursively()
        data.save()
        assertFalse(project.exists())
    }
}
