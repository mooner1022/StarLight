package dev.mooner.starlight.agent.docs

import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.neonjs.SandboxPolicy
import dev.mooner.starlight.languages.rhino.PrimitiveWrapFactory
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptableObject
import java.math.BigInteger

/**
 * What the docs tell the agent about Java values, checked in both engines: the `JavaList`/`JavaMap` declarations every
 * description starts with (docs/agent-docs.md §6.3), and the guides' "Java values" sections.
 */
class EngineValuesTest {

    @Suppress("unused")
    class Fixture {
        val name: String = "fixture"
        val isOn: Boolean = true
        @get:JvmName("getIsReady")
        val isReady: Boolean = true
        fun getCurrentBot(): String = "bot"
        val ids: Array<String> = arrayOf("a", "b")
        val list: List<String> = listOf("x", "y")
        val map: Map<String, Int> = linkedMapOf("a" to 1, "b" to 2)
        val big: Long = 9007199254740993L
        val small: Long = 42L
        val bigInt: BigInteger = BigInteger("9007199254740993")
        val builder: StringBuilder = StringBuilder("sb")
        fun takesLong(value: Long): String = value.toString()
        fun run(task: Runnable): String { task.run(); return "ran" }
        fun apply(transform: (String) -> String): String = transform("in")
        fun fail(): String = throw IllegalStateException("boom")
        fun pick(value: String): String = "string"
        fun pick(value: Int): String = "int"
        val nothing: String? = null
        val env: Map<String, String> = mapOf("size" to "S", "get" to "G", "TOKEN" to "t")
        val realEnv = dev.mooner.starlight.api.original.EnvironmentApi.Environment(mapOf("TOKEN" to "t"))
        val set: Set<String> = linkedSetOf("a", "b")
        fun takesArray(values: Array<out Any>): Int = values.size
        fun takesList(values: List<String>): Int = values.size
        fun takesSet(values: Set<String>): Int = values.size
    }

    @Test
    fun `sets are iterated, and array and list parameters take JS arrays`() {
        // JavaSet and the `T[] | JavaList<T>` of parameters (docs/agent-docs.md §6.3).
        assertTrue(DtsRenderer.JAVA_SET.contains("size()") && DtsRenderer.JAVA_SET.contains("contains(value: T)"))
        both("fx.set.size()", "2")
        both("fx.set.contains('a')", "true")
        both("fx.set.length", "undefined")
        both("(function(){ var r = []; for (var v of fx.set) r.push(v); return r.join(','); })()", "a,b")
        both("Array.from(fx.set).join(',')", "a,b")
        both("fx.takesArray(['x', 1])", "2")
        both("fx.takesList(['x', 'y'])", "2")
        assertTrue(runCatching { neon("fx.takesSet(['x'])") }.isFailure)
        assertTrue(runCatching { rhino("fx.takesSet(['x'])") }.isFailure)
    }

    @Test
    fun `Env can't be changed`() {
        // Env's KDoc: a TypeError in NeonJS, whose modules are strict, and UnsupportedOperationException in Rhino.
        val neonError = runCatching { neon("(function(){ 'use strict'; fx.realEnv.TOKEN = 'x'; })()") }.exceptionOrNull()
        assertTrue(neonError.toString(), neonError.toString().contains("TypeError: Cannot assign to read only property"))
        assertEquals("HostError: Env is read-only: change the .env file and compile the project again",
            neon("(function(){ try { fx.realEnv.put('TOKEN', 'x') } catch (e) { return e.name + ': ' + e.message } })()"))
        val rhinoError = runCatching { rhino("(fx.realEnv.TOKEN = 'x')") }.exceptionOrNull()
        assertTrue(rhinoError.toString(), rhinoError.toString().contains("UnsupportedOperationException"))
        both("fx.realEnv.TOKEN", "t")
        both("fx.realEnv.get('TOKEN')", "t")
        both("Object.keys(fx.realEnv).join(',')", "TOKEN")
    }

    @Test
    fun `Rhino's syntax is what the Rhino guide lists`() {
        // guide:engines/rhino#syntax, with the default language version (ES6).
        val works = listOf(
            "(() => 1)()", "(function(){ let a = 1; const b = 2; return a + b; })()", "`a${'$'}{1 + 1}`",
            "(function(){ var {a} = {a: 3}; return a; })()", "(function(){ var [x, y] = [1, 2]; return y; })()",
            "(function(...r){ return r.length; })(1, 2)", "(function(){ return { f() { return 1; } }.f(); })()",
            "(function(){ var r = 0; for (let x of [1, 2]) r += x; return r; })()",
            "Promise.resolve(1) instanceof Promise", "new Map([['k', 1]]).get('k')", "new Set([1, 1]).size", "1n + 1n",
        )
        val fails = listOf(
            "(function(){ class A {} return 1; })()", "(async function(){ return 1; })", "Math.max(...[1, 2])",
            "[...[1, 2]].length", "({...{a: 1}}).a", "(function(a, b = 2){ return a + b; })(1)", "'a'?.length",
            "null ?? 'd'", "(function(){ for (const x of [1]) {} return 1; })()",
        )
        for (expression in works) {
            rhino(expression)
            neon(expression)
        }
        for (expression in fails) {
            assertTrue("Rhino parses $expression", runCatching { rhino(expression) }.isFailure)
            neon(expression)
        }
    }

    @Test
    fun `Java access as the engine guides describe it`() {
        // guide:engines/neonjs#java-access
        both("fx.run(function(){})", "ran")
        both("fx.apply(function(s){ return s + '!'; })", "in!")
        both("fx['pick(java.lang.String)']('x')", "string")
        both("fx['pick(int)'](1)", "int")
        both("fx.nothing === null", "true")
        assertEquals("HostError|boom", neon("(function(){ try { fx.fail() } catch (e) { return e.name + '|' + e.message } })()"))
        assertEquals("function", neon("typeof Java.type('java.util.Map').Entry"))
        assertEquals("2", neon("Java.to([1, 2], 'long[]').length"))
        // guide:engines/rhino#java-values
        assertEquals("JavaException|true", rhino("(function(){ try { fx.fail() } catch (e) { return e.name + '|' + (e.javaException != null) } })()"))
        assertEquals("true", rhino("(function(){ var ran = false; fx.run(new JavaAdapter(java.lang.Runnable, { run: function(){ ran = true; } })); return ran; })()"))
        assertEquals("function", rhino("typeof Packages.java.util.ArrayList"))
    }

    @Test
    fun `what NeonJS lacks, as its guide lists it`() {
        // guide:engines/neonjs#what-neonjs-lacks, in a context with the web globals JSNeon turns on.
        val context = NeonEngine.builder().webGlobals(true).build().newContext()
        try {
            for (name in listOf("Intl", "require", "module", "process", "Buffer", "window", "document", "Packages", "importClass", "JavaAdapter", "print"))
                assertEquals(name, "undefined", context.eval("typeof $name", "probe.js").toString())
            for (name in listOf("URL", "URLSearchParams", "TextEncoder", "AbortController", "Temporal", "structuredClone"))
                assertTrue(name, context.eval("typeof $name", "probe.js").toString() != "undefined")
        } finally {
            context.close()
        }
    }

    private fun both(expression: String, expected: String) {
        assertEquals("NeonJS: $expression", expected, neon(expression))
        assertEquals("Rhino: $expression", expected, rhino(expression))
    }

    @Test
    fun `arrays and lists are read by index, length and for-of, and Array_from copies them`() {
        assertTrue(DtsRenderer.JAVA_LIST.contains("`Array.from(x)`"))
        for (value in listOf("fx.ids", "fx.list")) {
            both("$value.length", "2")
            assertEquals("2", neon("[...$value].length"))
            both("Array.isArray($value)", "false")
            both("(function(){ var r = []; for (var v of $value) r.push(v); return r.join(','); })()", if (value == "fx.ids") "a,b" else "x,y")
            both("Array.isArray(Array.from($value))", "true")
        }
        both("fx.ids[1]", "b")
        both("fx.list[0]", "x")
    }

    @Test
    fun `maps are read as properties`() {
        assertTrue(DtsRenderer.JAVA_MAP.contains("`map[key]` or `map.key`"))
        both("fx.map.a", "1")
        both("fx.map['b']", "2")
        both("Object.keys(fx.map).join(',')", "a,b")
        // Env's KDoc: in NeonJS a map's members come before entries of the same name; in Rhino entries come first.
        both("fx.env.TOKEN", "t")
        assertEquals("S", neon("fx.env.get('size')"))
        assertEquals("function", neon("typeof fx.env.get"))
        assertEquals("function", neon("typeof fx.env.size"))
        assertEquals("G", rhino("fx.env.get"))
        assertEquals("S", rhino("fx.env.size"))
    }

    @Test
    fun `getters are properties and stay callable`() {
        both("fx.name === 'fixture'", "true")
        both("fx.on", "true")
        both("fx.isOn()", "true")
        both("fx.isReady", "true")
        both("fx.currentBot", "bot")
        both("fx.getCurrentBot()", "bot")
        // A CharSequence other than a String stays a Java object in both.
        both("fx.builder === 'sb'", "false")
        both("String(fx.builder) === 'sb'", "true")
    }

    @Test
    fun `a long beyond 2^53 is a BigInt in NeonJS and loses precision in Rhino`() {
        assertEquals("bigint", neon("typeof fx.big"))
        assertEquals("9007199254740993", neon("String(fx.big)"))
        assertEquals("9007199254740993", neon("fx.takesLong(9007199254740993n)"))
        both("typeof fx.small", "number")
        both("typeof fx.bigInt", "bigint")

        assertEquals("number", rhino("typeof fx.big"))
        assertEquals("9007199254740992", rhino("String(fx.big)"))
        assertEquals("9007199254740992", rhino("fx.takesLong('9007199254740993')"))
    }

    private fun neon(expression: String): String =
        neonContext.eval("String($expression)", "probe.js").toString()

    private fun rhino(expression: String): String =
        rhinoFactory.call { cx ->
            Context.toString(cx.evaluateString(rhinoScope, "String($expression)", "probe.js", 1, null))
        } as String

    companion object {

        private lateinit var neonContext: NeonContext

        /** As JSRhino sets up its contexts: Java map access, ES6 and [PrimitiveWrapFactory]. */
        private val rhinoFactory = object : ContextFactory() {
            override fun hasFeature(cx: Context?, featureIndex: Int): Boolean =
                featureIndex == Context.FEATURE_ENABLE_JAVA_MAP_ACCESS || super.hasFeature(cx, featureIndex)

            override fun makeContext(): Context = super.makeContext().also { cx ->
                cx.languageVersion = Context.VERSION_ES6
                cx.wrapFactory = PrimitiveWrapFactory(restricted = false)
                cx.optimizationLevel = -1
            }
        }

        private lateinit var rhinoScope: ScriptableObject

        @BeforeClass
        @JvmStatic
        fun setUp() {
            neonContext = NeonEngine.builder()
                .sandbox(SandboxPolicy.builder().exposeJavaGlobal(true).build())
                .hostAccess(HostAccess.builder(HostAccess.Level.ALL).defaultDenyList(false).allowLookup { true }.build())
                .build()
                .newContext()
            neonContext["fx"] = Fixture()
            rhinoFactory.call { cx ->
                rhinoScope = cx.initStandardObjects()
                ScriptableObject.putProperty(rhinoScope, "fx", Context.javaToJS(Fixture(), rhinoScope))
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            neonContext.close()
        }
    }
}
