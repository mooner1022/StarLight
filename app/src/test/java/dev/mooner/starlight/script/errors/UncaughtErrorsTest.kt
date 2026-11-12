package dev.mooner.starlight.script.errors

import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.neonjs.NeonInterruptedException
import dev.mooner.neonjs.NeonValue
import dev.mooner.neonjs.SandboxPolicy
import dev.mooner.starlight.languages.neonjs.buildHostAccess
import dev.mooner.starlight.languages.neonjs.neonUncaughtHandler
import dev.mooner.starlight.languages.rhino.PrimitiveWrapFactory
import dev.mooner.starlight.languages.rhino.rhinoUncaughtHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.WrappedException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException

class UncaughtErrorsTest {

    // ---- UncaughtErrorPolicy ------------------------------------------------------------------------------------

    @Test
    fun `the policy allows 10 calls a minute and tells the limit once`() {
        var now = 0L
        val policy = UncaughtErrorPolicy { now }
        repeat(UncaughtErrorPolicy.MAX_CALLS) { assertEquals(UncaughtErrorPolicy.Decision.CALL, policy.next()) }
        assertEquals(UncaughtErrorPolicy.Decision.LIMITED_FIRST, policy.next())
        assertEquals(UncaughtErrorPolicy.Decision.LIMITED, policy.next())
        now += UncaughtErrorPolicy.WINDOW_MILLIS - 1
        assertEquals(UncaughtErrorPolicy.Decision.LIMITED, policy.next())
        now += 1
        assertEquals(UncaughtErrorPolicy.Decision.CALL, policy.next())
    }

    @Test
    fun `the policy disables the handler after 3 failures in a row, and a success starts the count again`() {
        val policy = UncaughtErrorPolicy { 0L }
        assertFalse(policy.failed())
        assertFalse(policy.failed())
        policy.succeeded()
        assertFalse(policy.failed())
        assertFalse(policy.failed())
        assertEquals(UncaughtErrorPolicy.Decision.CALL, policy.next())
        assertTrue(policy.failed())
        assertEquals(UncaughtErrorPolicy.Decision.DISABLED, policy.next())
        policy.succeeded()
        assertFalse(policy.failed())
        assertEquals(UncaughtErrorPolicy.Decision.DISABLED, policy.next())
    }

    // ---- UncaughtErrors -----------------------------------------------------------------------------------------

    private class Owner : UncaughtErrors.Owner {
        override val id = UUID.randomUUID()!!
        val warnings = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<Throwable>()
        var throws = false

        override fun warn(message: String) { check(!throws) { "owner failed" }; warnings += message }
        override fun error(message: String) { check(!throws) { "owner failed" }; errors += message }
        override fun describe(error: Throwable) = "described: ${error.message}"
        override fun fail(error: Throwable) { check(!throws) { "owner failed" }; failures += error }
    }

    /** A handler that notes its calls as `<origin> <source> <args> <message>`, and does what [body] says. */
    private class Calls(val body: (Throwable) -> Boolean = { true }) {
        val seen = CopyOnWriteArrayList<String>()
        val handler = UncaughtErrors.Handler { error, origin, source, args ->
            seen += "$origin $source ${args.toList()} ${error.message}"
            body(error)
        }
    }

    private val owners = mutableListOf<UncaughtErrors.Owner>()

    private fun owner() = Owner().also { owners += it }

    private fun report(owner: Owner, message: String = "boom", settingFollows: Boolean = false,
                       error: Throwable = IllegalStateException(message)) =
        UncaughtErrors.report(owner.id, error, UncaughtErrors.Origin.EXCEPTION, "onMessage", arrayOf("room"), settingFollows)

    @After
    fun tearDown() {
        owners.forEach { UncaughtErrors.clear(it.id) }
        UncaughtErrors.clock = System::currentTimeMillis
    }

    @Test
    fun `an error reaches the handler of the version that runs, with where it came from`() {
        val owner = owner()
        val calls = Calls()
        UncaughtErrors.install(owner, calls.handler)
        report(owner)
        UncaughtErrors.report(owner.id, IllegalStateException("late"), UncaughtErrors.Origin.REJECTION, "promise")
        assertEquals(listOf("uncaughtException onMessage [room] boom", "unhandledRejection promise [] late"), calls.seen)
    }

    @Test
    fun `nothing is called without a handler, after clear, or for an engine stopping the code`() {
        val owner = owner()
        UncaughtErrors.install(owner, null)
        report(owner)
        val calls = Calls()
        UncaughtErrors.install(owner, calls.handler)
        report(owner, error = NeonInterruptedException("interrupted"))
        report(owner, error = RuntimeException("wrapped", NeonInterruptedException("interrupted")))
        report(owner, error = CancellationException("cancelled"))
        UncaughtErrors.clear(owner.id)
        report(owner)
        UncaughtErrors.report(UUID.randomUUID(), IllegalStateException(), UncaughtErrors.Origin.EXCEPTION, "x")
        assertTrue(calls.seen.isEmpty())
        assertTrue(owner.warnings.isEmpty() && owner.errors.isEmpty() && owner.failures.isEmpty())
    }

    @Test
    fun `an error inside the handler isn't reported to it again`() {
        val owner = owner()
        lateinit var calls: Calls
        calls = Calls {
            // As the handler's own timer or failing call would, on this thread.
            report(owner, "inside")
            true
        }
        UncaughtErrors.install(owner, calls.handler)
        report(owner)
        assertEquals(1, calls.seen.size)
        // Its failure, on its way to the project's error path, isn't either.
        report(owner, error = UncaughtErrors.HandlerFailure(IllegalStateException("x"), "x"))
        assertEquals(1, calls.seen.size)
    }

    @Test
    fun `a handler that throws on an event function's error is logged, and the project's setting does the rest`() {
        val owner = owner()
        val calls = Calls { throw IllegalArgumentException("in handler") }
        UncaughtErrors.install(owner, calls.handler)
        report(owner, settingFollows = true)
        assertEquals(1, calls.seen.size)
        assertEquals(1, owner.errors.size)
        assertTrue(owner.errors.single().contains("described: in handler"))
        assertTrue(owner.failures.isEmpty())
    }

    @Test
    fun `a handler that throws on a timer's error goes to the project's error path`() {
        val owner = owner()
        val calls = Calls { throw IllegalArgumentException("in handler") }
        UncaughtErrors.install(owner, calls.handler)
        report(owner, settingFollows = false)
        val failure = owner.failures.single()
        assertTrue(failure is UncaughtErrors.HandlerFailure)
        assertTrue(failure.cause is IllegalArgumentException)
        assertEquals("onUncaughtError failed: described: in handler", failure.message)
        assertTrue(owner.errors.isEmpty())
        // Coming back through the error path, as the project's runEvent sends it, it stops here.
        report(owner, error = failure, settingFollows = true)
        assertEquals(1, calls.seen.size)
    }

    @Test
    fun `three failures in a row stop the calls until the next install`() {
        val owner = owner()
        val calls = Calls { throw IllegalArgumentException("in handler") }
        UncaughtErrors.install(owner, calls.handler)
        repeat(5) { report(owner, settingFollows = true) }
        assertEquals(3, calls.seen.size)
        assertEquals(1, owner.warnings.count { it.contains("3") })
        UncaughtErrors.install(owner, calls.handler)
        report(owner, settingFollows = true)
        assertEquals(4, calls.seen.size)
    }

    @Test
    fun `calls are limited to 10 a minute, which is told once`() {
        var now = 0L
        UncaughtErrors.clock = { now }
        val owner = owner()
        val calls = Calls()
        UncaughtErrors.install(owner, calls.handler)
        repeat(15) { report(owner) }
        assertEquals(10, calls.seen.size)
        assertEquals(1, owner.warnings.size)
        now += UncaughtErrorPolicy.WINDOW_MILLIS
        report(owner)
        assertEquals(11, calls.seen.size)
    }

    @Test
    fun `a Promise the handler returns settles its run later, and only the handler of the version that runs counts`() {
        val owner = owner()
        val calls = Calls { false }
        UncaughtErrors.install(owner, calls.handler)
        // Pending runs don't count as successes: three rejections in a row disable it.
        repeat(3) {
            report(owner)
            UncaughtErrors.handlerFailedLater(owner.id, calls.handler, IllegalStateException("rejected"))
        }
        assertEquals(3, owner.failures.size)
        assertTrue(owner.failures.all { it is UncaughtErrors.HandlerFailure })
        report(owner)
        assertEquals(3, calls.seen.size)

        // A fulfilled one does.
        UncaughtErrors.install(owner, calls.handler)
        repeat(2) { UncaughtErrors.handlerFailedLater(owner.id, calls.handler, IllegalStateException("rejected")) }
        UncaughtErrors.handlerSucceededLater(owner.id, calls.handler)
        repeat(2) { UncaughtErrors.handlerFailedLater(owner.id, calls.handler, IllegalStateException("rejected")) }
        report(owner)
        assertEquals(4, calls.seen.size)

        // The last version's handler, rejecting late, is ignored.
        val old = Calls()
        UncaughtErrors.handlerFailedLater(owner.id, old.handler, IllegalStateException("old"))
        UncaughtErrors.handlerSucceededLater(owner.id, old.handler)
        assertEquals(7, owner.failures.size)
    }

    @Test
    fun `errors of two threads both reach the handler`() {
        val owner = owner()
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = Calls { error ->
            if (error.message == "first") {
                inside.countDown()
                release.await()
            }
            true
        }
        UncaughtErrors.install(owner, calls.handler)
        val first = thread { report(owner, "first") }
        inside.await()
        // While the first thread's handler still runs.
        report(owner, "second")
        release.countDown()
        first.join()
        assertEquals(setOf("first", "second"), calls.seen.map { it.substringAfterLast(' ') }.toSet())
    }

    @Test
    fun `report never throws, even when the project's log does`() {
        val owner = owner().apply { throws = true }
        UncaughtErrors.install(owner, Calls { throw IllegalArgumentException("in handler") }.handler)
        report(owner, settingFollows = true)
        report(owner, settingFollows = false)
        UncaughtErrors.handlerFailedLater(owner.id, Calls().handler, IllegalStateException())
    }

    // ---- NeonJS -------------------------------------------------------------------------------------------------

    private val neonContexts = mutableListOf<NeonContext>()

    @After
    fun closeContexts() {
        neonContexts.forEach(NeonContext::close)
    }

    private fun neon(hostAccess: HostAccess = HostAccess.builder(HostAccess.Level.ALL).defaultDenyList(false).allowLookup { true }.build()): NeonContext =
        NeonEngine.builder()
            .sandbox(SandboxPolicy.builder().exposeJavaGlobal(true).build())
            .hostAccess(hostAccess)
            .build()
            .newContext()
            .also { neonContexts += it }

    private class Later {
        val succeeded = CopyOnWriteArrayList<UncaughtErrors.Handler>()
        val failed = CopyOnWriteArrayList<Throwable>()
    }

    private fun neonHandler(context: NeonContext, module: NeonValue, later: Later = Later()): UncaughtErrors.Handler =
        neonUncaughtHandler(context, module, { later.succeeded += it }, { _, error -> later.failed += error })!!

    private fun NeonContext.read(expression: String): String =
        eval("String($expression)", "read.js").toString()

    private val recording = """
        export function onUncaughtError(e, origin, detail) { globalThis.last = { e: e, origin: origin, detail: detail }; }
        export function boom() { throw new TypeError("x"); }
        export function text() { throw "text"; }
    """.trimIndent()

    @Test
    fun `NeonJS gives the handler the value the script threw, the origin and the detail`() {
        val context = neon()
        val module = context.evalModule(recording, "main.js")
        assertNull(neonUncaughtHandler(context, context.evalModule("export function onMessage() {}", "none.js"), {}, { _, _ -> }))
        val handler = neonHandler(context, module)

        val typeError = runCatching { module.getMember("boom").call() }.exceptionOrNull()!!
        assertTrue(handler.call(typeError, "uncaughtException", "onMessage", arrayOf<Any?>("a1", 2)))
        assertEquals("true", context.read("last.e instanceof Error"))
        assertEquals("TypeError", context.read("last.e.name"))
        assertEquals("x", context.read("last.e.message"))
        assertEquals("uncaughtException", context.read("last.origin"))
        assertEquals("onMessage", context.read("last.detail.source"))
        assertEquals("true", context.read("Array.isArray(last.detail.args)"))
        assertEquals("a1,2", context.read("last.detail.args.join(',')"))

        val text = runCatching { module.getMember("text").call() }.exceptionOrNull()!!
        handler.call(text, "unhandledRejection", "promise", emptyArray())
        assertEquals("true", context.read("last.e === 'text'"))
        assertEquals("0", context.read("last.detail.args.length"))

        // A Java exception, as a host API throws it.
        handler.call(IllegalStateException("from Java"), "uncaughtException", "setTimeout", emptyArray())
        assertEquals("object", context.read("typeof last.e"))
        assertEquals("false", context.read("last.e instanceof Error"))
        assertEquals("from Java", context.read("last.e.message"))
    }

    @Test
    fun `NeonJS's wrapper leaves no global behind`() {
        val context = neon()
        val before = context.read("Object.getOwnPropertyNames(globalThis).sort().join()")
        val module = context.evalModule(recording, "main.js")
        val handler = neonHandler(context, module)
        handler.call(IllegalStateException("x"), "uncaughtException", "onMessage", emptyArray())
        val after = context.read("Object.getOwnPropertyNames(globalThis).filter(n => n !== 'last').sort().join()")
        assertEquals(before, after)
        assertEquals("undefined,undefined", context.read("[typeof sink, typeof fn].join()"))
    }

    @Test
    fun `an async handler's rejection is told to the app, not reported as unhandled`() {
        for ((label, hostAccess) in listOf("open" to null, "Play" to buildHostAccess(restricted = true))) {
            for (body in listOf("throw new Error('at once');", "await null; throw new Error('after await');")) {
                val context = if (hostAccess == null) neon() else neon(hostAccess)
                val unhandled = CopyOnWriteArrayList<String>()
                context.setUncaughtErrorHandler { error, _ -> unhandled += error.toString() }
                val module = context.evalModule("export async function onUncaughtError(e) { $body }", "main.js")
                val later = Later()
                val handler = neonHandler(context, module, later)
                assertFalse("$label $body", handler.call(IllegalStateException("x"), "uncaughtException", "onMessage", emptyArray()))
                assertEquals("$label $body: ${later.failed}", 1, later.failed.size)
                assertTrue("$label $body: ${later.failed}", later.failed.single().message!!.contains("Error: "))
                assertTrue("$label $body: $unhandled", unhandled.isEmpty())
                assertTrue(later.succeeded.isEmpty())
            }
            val context = if (hostAccess == null) neon() else neon(hostAccess)
            val later = Later()
            val handler = neonHandler(context, context.evalModule("export async function onUncaughtError(e) { await null; }", "main.js"), later)
            assertFalse(handler.call(IllegalStateException("x"), "uncaughtException", "onMessage", emptyArray()))
            assertEquals(listOf(handler), later.succeeded.toList())
            assertTrue(later.failed.isEmpty())
        }
    }

    @Test
    fun `an async handler that keeps rejecting is disabled after three times`() {
        val context = neon()
        val owner = owner()
        val module = context.evalModule(
            "globalThis.calls = 0; export async function onUncaughtError(e) { globalThis.calls++; await null; throw new Error('no'); }",
            "main.js")
        UncaughtErrors.install(owner, neonUncaughtHandler(context, module,
            { UncaughtErrors.handlerSucceededLater(owner.id, it) },
            { handler, error -> UncaughtErrors.handlerFailedLater(owner.id, handler, error) }))
        repeat(5) { report(owner) }
        assertEquals("3", context.read("calls"))
        assertEquals(3, owner.failures.size)
        assertEquals(1, owner.warnings.size)
    }

    @Test
    fun `one rejected Promise calls the handler once, and an error thrown out of a call isn't reported as unhandled`() {
        val context = neon()
        val owner = owner()
        // As JSNeon wires the context.
        context.setUncaughtErrorHandler { error, rejection ->
            UncaughtErrors.report(owner.id, error,
                if (rejection) UncaughtErrors.Origin.REJECTION else UncaughtErrors.Origin.EXCEPTION, "promise")
        }
        val module = context.evalModule("""
            globalThis.calls = [];
            export function onUncaughtError(e, origin, detail) { globalThis.calls.push(origin + " " + detail.source + " " + e.message); }
            export function reject() { Promise.reject(new Error("r")); }
            export function boom() { throw new Error("thrown"); }
        """.trimIndent(), "main.js")
        UncaughtErrors.install(owner, neonHandler(context, module))
        module.getMember("reject").call()
        assertEquals("unhandledRejection promise r", context.read("calls.join('|')"))
        assertTrue(runCatching { module.getMember("boom").call() }.isFailure)
        assertEquals("1", context.read("calls.length"))
    }

    // ---- Rhino --------------------------------------------------------------------------------------------------

    private val rhinoFactory = object : ContextFactory() {
        override fun hasFeature(cx: Context?, featureIndex: Int): Boolean =
            featureIndex == Context.FEATURE_ENABLE_JAVA_MAP_ACCESS || super.hasFeature(cx, featureIndex)

        override fun makeContext(): Context = super.makeContext().also { cx ->
            cx.languageVersion = Context.VERSION_ES6
            cx.wrapFactory = PrimitiveWrapFactory(restricted = false)
            cx.optimizationLevel = -1
        }
    }

    @Test
    fun `Rhino gives the handler what the script would have caught, from a thread with no context`() {
        val scope = rhinoFactory.call { cx -> cx.initStandardObjects() } as ScriptableObject
        rhinoFactory.call { cx ->
            cx.evaluateString(scope, """
                var last = null;
                function onUncaughtError(e, origin, detail) { last = { e: e, origin: origin, detail: detail }; }
                function boom() { throw new Error("x"); }
                function text() { throw "text"; }
                function ecma() { return undefined.y; }
                function javaError() { return java.lang.Integer.parseInt("x"); }
            """.trimIndent(), "main.js", 1, null)
        }
        assertNull(rhinoUncaughtHandler(rhinoFactory.call { cx -> cx.initStandardObjects() } as ScriptableObject, rhinoFactory::enterContext))
        val handler = rhinoUncaughtHandler(scope, rhinoFactory::enterContext)!!
        fun thrown(function: String): Throwable =
            rhinoFactory.call { cx -> runCatching { ScriptableObject.callMethod(cx, scope, function, emptyArray()) }.exceptionOrNull() } as Throwable
        fun read(expression: String): String =
            rhinoFactory.call { cx -> Context.toString(cx.evaluateString(scope, "String($expression)", "read.js", 1, null)) } as String
        /** As the app calls it: on a worker that has left the context it ran the script in. */
        fun callOnBareThread(error: Throwable) {
            var failure: Throwable? = null
            var leftContext = false
            thread {
                try {
                    assertTrue(handler.call(error, "uncaughtException", "onMessage", arrayOf<Any?>("a1", 2)))
                    leftContext = Context.getCurrentContext() == null
                } catch (e: Throwable) {
                    failure = e
                }
            }.join()
            assertNull(failure)
            assertTrue(leftContext)
        }

        val error = thrown("boom")
        assertTrue(error is JavaScriptException)
        callOnBareThread(error)
        assertEquals("true|Error|x", read("[last.e instanceof Error, last.e.name, last.e.message].join('|')"))
        assertEquals("uncaughtException|onMessage|true|a1,2", read("[last.origin, last.detail.source, Array.isArray(last.detail.args), last.detail.args.join(',')].join('|')"))

        callOnBareThread(thrown("text"))
        assertEquals("true", read("last.e === 'text'"))

        val ecma = thrown("ecma")
        assertTrue(ecma is EcmaError)
        callOnBareThread(ecma)
        assertEquals("true|TypeError", read("[last.e instanceof TypeError, last.e.name].join('|')"))
        assertEquals((ecma as EcmaError).errorMessage, read("last.e.message"))

        val wrapped = thrown("javaError")
        assertTrue(wrapped is WrappedException)
        callOnBareThread(wrapped)
        assertEquals("false|object", read("[last.e instanceof Error, typeof last.e].join('|')"))
        assertTrue(read("last.e.message").contains("For input string"))

        // An error of the host, not of the script.
        callOnBareThread(IllegalStateException("from Java"))
        assertEquals("from Java", read("last.e.message"))
    }
}
