package dev.mooner.starlight.agent.docs

import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.HostName
import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.neonjs.SandboxPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [ScriptView] against fixtures of each rule in docs/agent-docs.md §6.2, and against NeonJS itself. */
class ScriptViewTest {

    @Suppress("unused")
    class Fixture {
        val name: String = "fixture"
        val isOn: Boolean = true
        @get:JvmName("getIsReady")
        val isReady: Boolean = true
        var count: Int = 0
        @JvmField
        val raw: String = "raw"

        fun send(text: String): Boolean = text.isNotEmpty()

        /** A setter with no getter: it stays a method. */
        fun setPrefix(prefix: String) {}

        @JvmOverloads
        fun rooms(packageName: String? = null, limit: Int = 10): List<String> = listOf("$packageName:$limit")

        fun overload(value: Int): String = "int $value"
        fun overload(value: String): String = "string $value"

        /** A getter in name only: it takes a parameter, so it stays a method. */
        fun getThing(index: Int): Int = index

        @HostName("renamed")
        fun original(): String = "renamed"

        companion object {
            @JvmStatic
            fun create(): Fixture = Fixture()
        }
    }

    data class Data(val id: Int, val label: String)

    private lateinit var neon: NeonContext

    private val js = mutableMapOf<String, Any?>()

    @Suppress("unused")
    inner class Recorder {
        fun put(key: String, value: Any?) {
            js[key] = value
        }
    }

    @Before
    fun setUp() {
        neon = NeonEngine.builder()
            .sandbox(SandboxPolicy.builder().exposeJavaGlobal(true).build())
            .hostAccess(HostAccess.builder(HostAccess.Level.ALL).defaultDenyList(false).allowLookup { true }.build())
            .build()
            .newContext()
        neon["fx"] = Fixture()
        neon["rec"] = Recorder()
    }

    @After
    fun tearDown() {
        neon.close()
    }

    @Test
    fun `properties follow NeonJS's getter rules`() {
        val view = ScriptView(Fixture::class.java)
        val properties = view.instance.properties.associateBy { it.name }
        assertEquals(setOf("name", "on", "isReady", "count", "raw"), properties.keys)
        assertTrue(properties.getValue("count").isWritable)
        assertFalse(properties.getValue("name").isWritable)
        assertFalse(properties.getValue("raw").isWritable)
    }

    @Test
    fun `methods keep overloads and names, without accessors and Kotlin's members`() {
        val methods = ScriptView(Fixture::class.java).instance.methods
        assertEquals(setOf("send", "setPrefix", "rooms", "overload", "getThing", "renamed"), methods.keys)
        assertEquals(listOf(0, 1, 2), methods.getValue("rooms").map { it.parameterCount })
        assertEquals(2, methods.getValue("overload").size)
    }

    @Test
    fun `statics and data classes`() {
        val view = ScriptView(Fixture::class.java)
        assertEquals(setOf("create"), view.static.methods.keys)
        assertTrue(view.static.properties.isEmpty())
        val data = ScriptView(Data::class.java).instance
        assertEquals(setOf("id", "label"), data.properties.map { it.name }.toSet())
        assertTrue(data.methods.isEmpty())
    }

    @Suppress("unused")
    class Nullables {
        val maybe: String? = null
        val sure: String = ""
        fun find(key: String?, fallback: String): String? = key ?: fallback
    }

    @Test
    fun `members without docs take nullability from Kotlin's metadata`() {
        val type = ScriptCatalog(DocFiles(emptyList()), emptyList(), emptyList()).describe(Nullables::class.java)
        assertTrue(type.properties.single { it.name == "maybe" }.type.nullable)
        assertFalse(type.properties.single { it.name == "sure" }.type.nullable)
        val find = type.methods.single { it.name == "find" }
        assertTrue(find.returns.nullable)
        assertEquals(listOf(true, false), find.params.map { it.type.nullable })
    }

    @Test
    fun `members without docs take Kotlin's parameter names, @JvmOverloads ones too`() {
        val catalog = ScriptCatalog(DocFiles(emptyList()), emptyList(), emptyList())
        val rooms = catalog.describe(Fixture::class.java).methods.filter { it.name == "rooms" }
        assertEquals(listOf(emptyList(), listOf("packageName"), listOf("packageName", "limit")),
            rooms.map { signature -> signature.params.map { it.name } }.sortedBy { it.size })
    }

    @Test
    fun `NeonJS sees the same members`() {
        val view = ScriptView(Fixture::class.java).instance
        for (property in view.properties)
            neon.eval("rec.put('p:${property.name}', typeof fx.${property.name})", "test.js")
        for (name in view.methods.keys)
            neon.eval("rec.put('m:$name', typeof fx.$name)", "test.js")
        for (property in view.properties)
            assertTrue("${property.name} reads as a value", js["p:${property.name}"] != "function" && js["p:${property.name}"] != "undefined")
        for (name in view.methods.keys)
            assertEquals("$name is a method", "function", js["m:$name"])

        neon.eval("fx.count = 5; rec.put('count', fx.count)", "test.js")
        assertEquals(5.0, (js["count"] as Number).toDouble(), 0.0)
        neon.eval("rec.put('rooms0', fx.rooms().length); rec.put('rooms1', fx.rooms('x').length)", "test.js")
        assertEquals(1.0, (js["rooms0"] as Number).toDouble(), 0.0)
        neon.eval("rec.put('isOn', typeof fx.isOn); rec.put('on', fx.on)", "test.js")
        assertEquals(true, js["on"])
    }
}
