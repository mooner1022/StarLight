package dev.mooner.starlight.script

import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.neonjs.SandboxPolicy
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.languages.neonjs.NeonJson
import dev.mooner.starlight.languages.rhino.PrimitiveWrapFactory
import dev.mooner.starlight.languages.rhino.RhinoJson
import org.junit.Assert.assertEquals
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptableObject

/**
 * Both engines set up as StarLight sets them up for a project, with globals made for each from its own [ScriptJson],
 * as JSNeon and JSRhino make API objects.
 */
class Engines(globals: (ScriptJson) -> Map<String, Any>) : AutoCloseable {

    private val neon: NeonContext = NeonEngine.builder()
        .sandbox(SandboxPolicy.builder().exposeJavaGlobal(true).build())
        .hostAccess(HostAccess.builder(HostAccess.Level.ALL).defaultDenyList(false).allowLookup { true }.build())
        .build()
        .newContext()

    private val rhinoFactory = object : ContextFactory() {
        override fun hasFeature(cx: Context?, featureIndex: Int): Boolean =
            featureIndex == Context.FEATURE_ENABLE_JAVA_MAP_ACCESS || super.hasFeature(cx, featureIndex)

        override fun makeContext(): Context = super.makeContext().also { cx ->
            cx.languageVersion = Context.VERSION_ES6
            cx.wrapFactory = PrimitiveWrapFactory(restricted = false)
            cx.optimizationLevel = -1
        }
    }

    private val rhinoScope: ScriptableObject = rhinoFactory.call { cx -> cx.initStandardObjects() } as ScriptableObject

    init {
        for ((name, value) in globals(NeonJson(neon)))
            neon[name] = value
        for ((name, value) in globals(RhinoJson(rhinoScope)))
            rhinoScope.put(name, rhinoScope, value)
    }

    /** [code], a script that may define functions and variables, then [expression] as text. */
    fun neon(expression: String, code: String = ""): String =
        neon.eval("$code\nString($expression)", "test.js").toString()

    fun rhino(expression: String, code: String = ""): String =
        rhinoFactory.call { cx ->
            Context.toString(cx.evaluateString(rhinoScope, "$code\nString($expression)", "test.js", 1, null))
        } as String

    fun both(expression: String, expected: String, code: String = "") {
        assertEquals("NeonJS: $expression", expected, neon(expression, code))
        assertEquals("Rhino: $expression", expected, rhino(expression, code))
    }

    /** The error [expression] throws in each engine, as text. */
    fun errors(expression: String): Pair<String, String> =
        (runCatching { neon(expression) }.exceptionOrNull().toString()) to
            (runCatching { rhino(expression) }.exceptionOrNull().toString())

    override fun close() = neon.close()
}
