package dev.mooner.starlight.languages

import dev.mooner.neonjs.ExecutionMode
import dev.mooner.neonjs.NeonEngine
import dev.mooner.starlight.languages.neonjs.buildHostAccess
import dev.mooner.starlight.languages.neonjs.buildSandboxPolicy
import dev.mooner.starlight.languages.rhino.PrimitiveWrapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory

/**
 * Runs the same escape attempts on both engines, restricted and not. The unrestricted runs show that
 * each attempt works without the policy, so a passing restricted run means the policy stopped it.
 */
class ScriptHostPolicyTest {

    /**
     * A snippet in each engine's syntax: Rhino reaches Java through packages, NeonJS here through Java.type() (its
     * packages are in [packageEscapes]).
     * NeonJS turns Class objects into types, so snippets that call Class methods have no NeonJS form.
     */
    private class Snippet(val rhino: String, val neon: String?)

    // Each snippet ends in a value only reachable through the denied API.
    private val escapes = mapOf(
        "context class loader" to Snippet(
            "java.lang.Thread.currentThread().getContextClassLoader().loadClass('java.lang.String').getName()",
            "Java.isType(Java.type('java.lang.Thread').currentThread().getContextClassLoader().loadClass('java.lang.String'))",
        ),
        "system class loader" to Snippet(
            "java.lang.ClassLoader.getSystemClassLoader().loadClass('java.lang.String').getName()",
            "Java.isType(Java.type('java.lang.ClassLoader').getSystemClassLoader().loadClass('java.lang.String'))",
        ),
        "class loader subclass" to Snippet(
            "new java.net.URLClassLoader([]).loadClass('java.lang.String').getName()",
            "const L = Java.type('java.net.URLClassLoader'); Java.isType(new L([]).loadClass('java.lang.String'))",
        ),
        "Class.forName" to Snippet(
            "java.lang.Class.forName('java.lang.String').getName()",
            "Java.isType(Java.type('java.lang.Class').forName('java.lang.String'))",
        ),
        "getClass().getClassLoader()" to Snippet(
            "new java.util.ArrayList().getClass().getClassLoader() !== undefined",
            null,
        ),
        "reflection" to Snippet(
            "java.lang.reflect.Array.getLength(java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 2))",
            "Java.type('java.lang.reflect.Modifier').isPublic(1)",
        ),
        "Runtime" to Snippet(
            "java.lang.Runtime.getRuntime().availableProcessors() > 0",
            "Java.type('java.lang.Runtime').getRuntime().availableProcessors() > 0",
        ),
        "ProcessBuilder" to Snippet(
            "new java.lang.ProcessBuilder('echo').redirectErrorStream(true).redirectErrorStream()",
            "const P = Java.type('java.lang.ProcessBuilder'); new P('echo').redirectErrorStream(true).redirectErrorStream()",
        ),
        "System.load and loadLibrary" to Snippet(
            "typeof java.lang.System.loadLibrary === 'function' && typeof java.lang.System.load === 'function'",
            "const S = Java.type('java.lang.System'); typeof S.loadLibrary === 'function' && typeof S.load === 'function'",
        ),
        // Rhino and NeonJS can both look up a method by its signature as well as by its name.
        "System.load by signature" to Snippet(
            "typeof java.lang.System['load(java.lang.String)'] === 'function' && " +
                "typeof java.lang.System['loadLibrary(java.lang.String)'] === 'function'",
            "const S = Java.type('java.lang.System'); typeof S['load(java.lang.String)'] === 'function' && " +
                "typeof S['loadLibrary(java.lang.String)'] === 'function'",
        ),
        "Class.forName by signature" to Snippet(
            "java.lang.Class['forName(java.lang.String)']('java.lang.String').getName()",
            "Java.isType(Java.type('java.lang.Class')['forName(java.lang.String)']('java.lang.String'))",
        ),
        "getClassLoader() by signature" to Snippet(
            "new java.util.ArrayList().getClass()['getClassLoader()']() !== undefined",
            null,
        ),
        // Rhino turns getters into properties and reaches static methods through instances.
        "classLoader property" to Snippet(
            // ArrayList comes from the boot class loader, which is null; JUnit's classes come from the app's.
            "new org.junit.runner.Result().getClass().classLoader.loadClass('java.lang.String').getName()",
            null,
        ),
        "forName through an instance" to Snippet(
            "new java.util.ArrayList().getClass().forName('java.lang.String').getName()",
            null,
        ),
        "Os.execve" to Snippet(
            "typeof Packages.android.system.Os.execve === 'function' && typeof Packages.android.system.Os.execv === 'function'",
            "const O = Java.type('android.system.Os'); typeof O.execve === 'function' && typeof O.execv === 'function'",
        ),
    )

    private val allowed = mapOf(
        "collections" to Snippet(
            "var l = new java.util.ArrayList(); l.add('a'); l.size()",
            "const L = Java.type('java.util.ArrayList'); const l = new L(); l.add('a'); l.size()",
        ),
        "System.currentTimeMillis" to Snippet(
            "java.lang.System.currentTimeMillis() > 0",
            "Java.type('java.lang.System').currentTimeMillis() > 0",
        ),
        "Thread.sleep" to Snippet(
            "java.lang.Thread.sleep(1); true",
            "Java.type('java.lang.Thread').sleep(1); true",
        ),
        "getClass().getName()" to Snippet(
            "new java.util.ArrayList().getClass().getName()",
            null,
        ),
        "StringBuilder" to Snippet(
            "new java.lang.StringBuilder('a').append('b').toString()",
            "const B = Java.type('java.lang.StringBuilder'); new B('a').append('b').toString()",
        ),
    )

    @Test
    fun deniesLoadersReflectionProcessesAndEngines() {
        for (name in listOf(
            "dalvik.system.DexClassLoader", "dalvik.system.PathClassLoader", "dalvik.system.InMemoryDexClassLoader",
            "java.lang.ClassLoader", "java.net.URLClassLoader", "java.lang.reflect.Method", "java.lang.invoke.MethodHandles",
            "java.lang.Runtime", "java.lang.ProcessBuilder", "android.content.pm.PackageInstaller",
            "android.content.pm.PackageInstaller\$Session", "org.mozilla.javascript.Context",
            "com.faendir.rhino_android.AndroidContextFactory", "dev.mooner.neonjs.NeonEngine", "com.android.dx.dex.DexOptions",
            "android.system.Os", "libcore.io.Libcore",
        ))
            assertTrue(name, ScriptHostPolicy.isClassNameDenied(name))
        for (name in listOf("java.lang.System", "java.lang.Thread", "java.lang.Class", "java.util.ArrayList", "android.webkit.WebView"))
            assertFalse(name, ScriptHostPolicy.isClassNameDenied(name))
        assertTrue(ScriptHostPolicy.isMemberDenied(System::class.java, "loadLibrary"))
        assertTrue(ScriptHostPolicy.isMemberDenied(Class::class.java, "forName"))
        assertFalse(ScriptHostPolicy.isMemberDenied(Class::class.java, "getName"))
        assertFalse(ScriptHostPolicy.isMemberDenied(System::class.java, "currentTimeMillis"))
    }

    @Test
    fun rhinoBlocksEscapesOnlyWhenRestricted() {
        for ((name, code) in escapes) {
            assertWorks("rhino unrestricted: $name", rhino(restricted = false), code.rhino)
            assertBlocked("rhino restricted: $name", rhino(restricted = true), code.rhino)
        }
        assertBlocked("rhino restricted: Context.enter", rhino(restricted = true), "org.mozilla.javascript.Context.enter()")
        assertBlocked("rhino restricted: JavaAdapter", rhino(restricted = true), "new JavaAdapter(java.lang.ClassLoader, {})")
    }

    @Test
    fun rhinoKeepsOrdinaryJavaAccess() {
        for ((name, code) in allowed)
            assertWorks("rhino restricted: $name", rhino(restricted = true), code.rhino)
    }

    @Test
    fun neonBlocksEscapesOnlyWhenRestricted() {
        for ((name, code) in escapes) {
            val neonCode = code.neon ?: continue
            assertWorks("neon unrestricted: $name", neon(restricted = false), neonCode)
            assertBlocked("neon restricted: $name", neon(restricted = true), neonCode)
        }
        assertBlocked("neon restricted: Java.extend", neon(restricted = true), "Java.extend(Java.type('java.lang.ClassLoader'))")
    }

    @Test
    fun neonKeepsOrdinaryJavaAccess() {
        for ((name, code) in allowed)
            assertWorks("neon restricted: $name", neon(restricted = true), code.neon ?: continue)
    }

    /** NeonJS's package globals reach classes only where `Java.type` would load them: a denied class is a package. */
    private val packageEscapes = mapOf(
        "Runtime" to "java.lang.Runtime.getRuntime().availableProcessors() > 0",
        "ProcessBuilder" to "new java.lang.ProcessBuilder('echo').redirectErrorStream(true).redirectErrorStream()",
        "System.load and loadLibrary" to "typeof java.lang.System.loadLibrary === 'function' && typeof java.lang.System.load === 'function'",
        "Os.execve" to "typeof android.system.Os.execve === 'function'",
        "Class.forName" to "Java.isType(java.lang.Class.forName('java.lang.String'))",
        "reflection" to "java.lang.reflect.Modifier.isPublic(1)",
        "Rhino" to "Java.isType(org.mozilla.javascript.Context)",
        "NeonJS" to "Java.isType(Packages.dev.mooner.neonjs.NeonEngine)",
    )

    private val packageAllowed = mapOf(
        "collections" to "var l = new java.util.ArrayList(); l.add('a'); l.size()",
        "System.currentTimeMillis" to "java.lang.System.currentTimeMillis() > 0",
        "StringBuilder" to "new java.lang.StringBuilder('a').append('b').toString()",
        "Packages" to "Packages.java.util.ArrayList === java.util.ArrayList",
    )

    @Test
    fun neonPackagesBlockEscapesOnlyWhenRestricted() {
        for ((name, code) in packageEscapes) {
            assertWorks("neon packages unrestricted: $name", neon(restricted = false), code)
            assertBlocked("neon packages restricted: $name", neon(restricted = true), code)
        }
        for ((name, code) in packageAllowed)
            assertWorks("neon packages restricted: $name", neon(restricted = true), code)
    }

    @Test
    fun aDeniedClassWrittenAsAPackageFailsWhenUsed() {
        // guide:play-build#java-access-limits, in both engines.
        for ((engine, run) in listOf("rhino" to rhino(restricted = true), "neon" to neon(restricted = true))) {
            assertEquals(engine, "object", run("typeof java.lang.Runtime").toString())
            val call = runCatching { run("java.lang.Runtime.getRuntime()") }.exceptionOrNull()
            assertTrue("$engine: $call", call.toString().contains("not a function"))
            val construct = runCatching { run("new java.lang.ProcessBuilder('echo')") }.exceptionOrNull()
            assertTrue("$engine: $construct", construct.toString().contains("not a function") || construct.toString().contains("not a constructor"))
        }
    }

    @Suppress("unused")
    class Thrower {
        /** An exception of a denied class, which a script catches. */
        fun invoke(): String = throw java.lang.reflect.InvocationTargetException(java.io.IOException("inner"), "outer")
    }

    @Test
    fun neonShowsNothingOfADeniedExceptionWhenRestricted() {
        // Scripts get a caught exception's methods, as the policy shows them for its class.
        val code = "(function(){ try { t.invoke() } catch (e) { return typeof e.getTargetException === 'function' && e.name } })()"
        assertEquals("java.lang.reflect.InvocationTargetException", neon(restricted = false, "t" to Thrower())(code))
        assertEquals("false", neon(restricted = true, "t" to Thrower())(code))
        assertEquals("java.lang.ReflectiveOperationException|outer", neon(restricted = true, "t" to Thrower())(
            "(function(){ try { t.invoke() } catch (e) { return e.name + '|' + e.message } })()"))
    }

    private fun assertWorks(message: String, run: (String) -> Any?, code: String) {
        val result = try {
            run(code)
        } catch (e: Throwable) {
            fail("$message threw $e")
        }
        assertTrue("$message returned $result", result != null && result != false && result.toString() != "false")
    }

    private fun assertBlocked(message: String, run: (String) -> Any?, code: String) {
        val result = try {
            run(code)
        } catch (_: Throwable) {
            return
        }
        assertEquals("$message wasn't blocked", "false", result.toString())
    }

    private fun rhino(restricted: Boolean): (String) -> Any? {
        val factory = object : ContextFactory() {
            override fun makeContext(): Context =
                super.makeContext().also { cx ->
                    cx.optimizationLevel = -1
                    if (restricted)
                        PrimitiveWrapFactory.restrictContext(cx)
                    else
                        cx.wrapFactory = PrimitiveWrapFactory(restricted = false)
                }
        }
        return { code ->
            factory.call(ContextAction { cx ->
                val scope = cx.initStandardObjects()
                Context.toString(cx.evaluateString(scope, code, "test", 1, null))
            })
        }
    }

    private fun neon(restricted: Boolean, vararg globals: Pair<String, Any>): (String) -> Any? {
        val engine = NeonEngine.builder()
            .executionMode(ExecutionMode.INTERPRETER)
            .sandbox(buildSandboxPolicy())
            .hostAccess(buildHostAccess(restricted))
            .build()
        return { code ->
            engine.newContext().use { context ->
                for ((name, value) in globals)
                    context[name] = value
                context.eval(code, "test").toString()
            }
        }
    }
}
