package dev.mooner.starlight.agent.docs

import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.starlight.languages.neonjs.buildHostAccess
import dev.mooner.starlight.languages.neonjs.buildSandboxPolicy
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
import java.time.Instant
import java.time.LocalDateTime

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
        fun failWithCause(): String = throw IllegalStateException("outer", java.io.IOException("inner"))
        val mutable: MutableList<String> = arrayListOf("a")
        val twice: (Int) -> Int = { it * 2 }
        val upper: java.util.function.Function<String, String> = java.util.function.Function { it.uppercase() }
        val instant: Instant = Instant.ofEpochMilli(5)
        val local: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
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
        assertEquals("java.lang.UnsupportedOperationException: Env is read-only: change the .env file and compile the project again",
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
        assertEquals("java.lang.IllegalStateException|boom|java.lang.IllegalStateException: boom|true|true|false",
            neon("(function(){ try { fx.fail() } catch (e) { return [e.name, e.message, String(e), e instanceof Error, " +
                "e instanceof Java.type('java.lang.RuntimeException'), e instanceof Java.type('java.io.IOException')].join('|') } })()"))
        assertEquals("outer|inner|true",
            neon("(function(){ try { fx.failWithCause() } catch (e) { return [e.getMessage(), e.getCause().getMessage(), e.getStackTrace().length > 0].join('|') } })()"))
        assertEquals("function", neon("typeof Java.type('java.util.Map').Entry"))
        assertEquals("2", neon("Java.to([1, 2], 'long[]').length"))
        assertEquals("int[]", neon("Java.typeName(Java.type('int[]'))"))
        // A Java function is a JS function; its own apply stays Java's.
        assertEquals("6|6|function", neon("[fx.twice(3), fx.twice.call(null, 3), typeof fx.twice.bind(null)].join('|')"))
        assertEquals("A|B", neon("[fx.upper('a'), fx.upper.apply('b')].join('|')"))
        // Packages, as in Rhino: classes where Java.type would load them, any other name a package.
        assertEquals("0|true|function|function|function",
            neon("[new java.util.ArrayList().size(), java.util.ArrayList === Java.type('java.util.ArrayList'), " +
                "typeof android.widget.Toast, typeof Packages.kotlin.text.Regex, typeof java.lang.Math.max].join('|')"))
        assertEquals("1", neon("(function(){ const ArrayList = java.util.ArrayList; const l = new ArrayList(); l.add('a'); return l.size(); })()"))
        assertEquals("object|TypeError", neon("[typeof java.util.ArayList, (function(){ try { new java.util.ArayList(); return 'no' } catch (e) { return e.name } })()].join('|')"))
        // guide:engines/rhino#java-values
        assertEquals("JavaException|true", rhino("(function(){ try { fx.fail() } catch (e) { return e.name + '|' + (e.javaException != null) } })()"))
        assertEquals("true", rhino("(function(){ var ran = false; fx.run(new JavaAdapter(java.lang.Runnable, { run: function(){ ran = true; } })); return ran; })()"))
        assertEquals("function", rhino("typeof Packages.java.util.ArrayList"))
    }

    @Test
    fun `what NeonJS lacks, as its guide lists it`() {
        // guide:engines/neonjs#what-neonjs-lacks, in a context with the globals JSNeon turns on.
        val printed = mutableListOf<String>()
        val context = NeonEngine.builder()
            .webGlobals(true)
            .sandbox(buildSandboxPolicy())
            .hostAccess(buildHostAccess(restricted = false))
            .console { level, message -> printed += "$level $message" }
            .build()
            .newContext()
        try {
            for (name in listOf("Intl", "require", "module", "process", "Buffer", "window", "document", "importClass",
                "importPackage", "JavaImporter", "JavaAdapter"))
                assertEquals(name, "undefined", context.eval("typeof $name", "probe.js").toString())
            for (name in listOf("URL", "URLSearchParams", "TextEncoder", "AbortController", "Temporal", "structuredClone",
                "Java", "Packages", "java", "javax", "com", "org", "edu", "android", "print", "printErr"))
                assertTrue(name, context.eval("typeof $name", "probe.js").toString() != "undefined")
            // guide:engines/neonjs#console
            context.eval("print('a', 1, [2, 3]); printErr('e')", "probe.js")
            assertEquals(listOf("LOG a 1 2,3", "ERROR e"), printed)
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
            val elements = if (value == "fx.ids") "a,b" else "x,y"
            both("$value.length", "2")
            assertEquals("2", neon("[...$value].length"))
            both("(function(){ var r = []; for (var v of $value) r.push(v); return r.join(','); })()", elements)
            both("Array.isArray(Array.from($value))", "true")
            // NeonJS shows them as JS arrays, with the array methods; Rhino doesn't.
            assertEquals("true", neon("Array.isArray($value)"))
            assertEquals("false", rhino("Array.isArray($value)"))
            assertEquals(elements.replace(",", "-"), neon("$value.join('-')"))
            assertEquals(elements.split(",").joinToString(",") { "$it!" }, neon("$value.map(function(x){ return x + '!'; }).join()"))
            assertEquals("true", neon("$value.includes('${elements.take(1)}')"))
            assertEquals("[\"${elements.replace(",", "\",\"")}\"]", neon("JSON.stringify($value)"))
            // A Java array, and a read-only list as Kotlin's listOf makes, can't change their length.
            assertEquals("TypeError", neon("(function(){ try { $value.push('z'); return 'no' } catch (e) { return e.name } })()"))
        }
        both("fx.ids[1]", "b")
        both("fx.list[0]", "x")
        // They are the Java objects: a List grows, and its own Java methods come first.
        assertEquals("2|a,b|1,1", neon("(function(){ var n = fx.mutable.push('b'); var r = []; " +
            "fx.mutable.forEach(function(){ r.push(arguments.length); }); return [n, fx.mutable.join(), r.join()].join('|'); })()"))
        assertEquals("1|a", neon("(function(){ var r = fx.mutable.indexOf('b'); fx.mutable.length = 1; return r + '|' + fx.mutable.join(); })()"))
    }

    @Test
    fun `maps are read as properties`() {
        assertTrue(DtsRenderer.JAVA_MAP.contains("`map[key]` or `map.key`"))
        both("fx.map.a", "1")
        both("fx.map['b']", "2")
        both("Object.keys(fx.map).join(',')", "a,b")
        // In NeonJS a Map is a JS Map too, its Java methods first: size() and forEach((key, value) => …) are Java's.
        assertEquals("true|a,1;b,2|2|true|2", neon("[fx.map instanceof Map, [...fx.map].join(';'), Object.fromEntries(fx.map).b, " +
            "fx.map.has('a'), fx.map.size()].join('|')"))
        assertEquals("a=1,b=2", neon("(function(){ var r = []; fx.map.forEach(function(k, v){ r.push(k + '=' + v); }); return r.join(); })()"))
        assertEquals("{\"a\":1,\"b\":2}", neon("JSON.stringify(fx.map)"))
        assertEquals("false", rhino("fx.map instanceof Map"))
        // Env's KDoc: in NeonJS a map's members come before entries of the same name; in Rhino entries come first.
        both("fx.env.TOKEN", "t")
        assertEquals("S", neon("fx.env.get('size')"))
        assertEquals("function", neon("typeof fx.env.get"))
        assertEquals("function", neon("typeof fx.env.size"))
        assertEquals("G", rhino("fx.env.get"))
        assertEquals("S", rhino("fx.env.size"))
    }

    @Test
    fun `an Instant is a Date in NeonJS, and new Date takes a host date-time's instant`() {
        // guide:engines/neonjs#java-access
        assertEquals("true|5|1970-01-01T00:00:00.005Z|\"1970-01-01T00:00:00.005Z\"|true|TypeError",
            neon("[fx.instant instanceof Date, fx.instant.getTime(), fx.instant.toISOString(), JSON.stringify(fx.instant), " +
                "fx.instant < new Date(6), (function(){ try { fx.instant.setTime(1); return 'no' } catch (e) { return e.name } })()].join('|')"))
        assertEquals("false|5|true", neon("[fx.local instanceof Date, new Date(fx.instant).getTime(), " +
            "new Date(fx.local).getTime() === new Date(2026, 0, 1).getTime()].join('|')"))
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
                .sandbox(buildSandboxPolicy())
                .hostAccess(buildHostAccess(restricted = false))
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
