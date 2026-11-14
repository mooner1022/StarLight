package dev.mooner.starlight.languages.rhino

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

class RhinoConsoleTest {

    private lateinit var cx: Context
    private lateinit var scope: Scriptable
    private lateinit var console: RhinoConsole

    private val written = ArrayList<Pair<RhinoConsole.Level, String>>()
    private var cleared = 0
    private var now = 0L

    @Before
    fun setUp() {
        cx = Context.enter()
        cx.optimizationLevel = -1
        scope = cx.initStandardObjects()
        console = RhinoConsole(scope, onClear = { cleared++ }, clock = { now }) { level, text -> written += level to text }
    }

    @After
    fun tearDown() {
        Context.exit()
    }

    private fun call(name: String, vararg args: Any?): Any? {
        val function = console.get(name, console) as Function
        return function.call(cx, scope, console, arrayOf(*args))
    }

    private fun js(source: String): Any? =
        cx.evaluateString(scope, source, "test", 1, null)

    @Test
    fun hasExactlyTheThirteenMembers() {
        val names = listOf("log", "info", "debug", "warn", "error", "clear", "time", "timeEnd", "timeLog", "count",
            "countReset", "assert", "table")
        for (name in names)
            assertTrue(name, console.get(name, console) is Function)
    }

    @Test
    fun argumentsAreJoinedBySpacesAndNullsAreWritten() {
        call("log", "a", 1.0, true, null, Undefined.instance)
        assertEquals(listOf(RhinoConsole.Level.INFO to "a 1 true null null"), written)
    }

    @Test
    fun theLevelsFollowTheMethods() {
        call("debug", "d")
        call("warn", "w")
        call("error", "e")
        call("info", "i")
        assertEquals(
            listOf(
                RhinoConsole.Level.DEBUG to "d",
                RhinoConsole.Level.WARN to "w",
                RhinoConsole.Level.ERROR to "e",
                RhinoConsole.Level.INFO to "i",
            ),
            written
        )
    }

    @Test
    fun logReturnsUndefined() {
        assertEquals(Undefined.instance, call("log", "x"))
    }

    @Test
    fun anObjectShowsItsFirstFiveKeys() {
        assertEquals("{a: 1, b: \"x\"}", RhinoConsole.text(js("({a: 1, b: 'x'})"), quote = false))
        assertEquals(
            "{a: 1, b: 2, c: 3, d: 4, e: 5, ...}",
            RhinoConsole.text(js("({a: 1, b: 2, c: 3, d: 4, e: 5, f: 6})"), quote = false)
        )
        assertEquals("{o: [object Object]}", RhinoConsole.text(js("({o: {x: 1}})"), quote = false))
        assertEquals("{}", RhinoConsole.text(js("({})"), quote = false))
    }

    @Test
    fun anArrayIsBracketedAndItsItemsRecurse() {
        assertEquals("[1, a, [2, 3]]", RhinoConsole.text(js("[1, 'a', [2, 3]]"), quote = false))
        assertEquals("[{k: 1}]", RhinoConsole.text(js("[{k: 1}]"), quote = false))
    }

    @Test
    fun timersReportTheElapsedMilliseconds() {
        now = 100
        call("time", "x")
        now = 112
        call("timeEnd", "x")
        assertEquals(listOf(RhinoConsole.Level.INFO to "x: 12ms"), written)

        written.clear()
        call("timeEnd", "x")
        assertEquals(listOf(RhinoConsole.Level.WARN to "Timer 'x' does not exist"), written)
    }

    @Test
    fun timeLogKeepsTheTimerAndRepeatsTheLabelWithMoreThanOneArgument() {
        now = 0
        call("time")
        now = 5
        call("timeLog", "default", "extra")
        call("timeLog")
        assertEquals(
            listOf(
                RhinoConsole.Level.INFO to "default: 5ms default extra",
                RhinoConsole.Level.INFO to "default: 5ms",
            ),
            written
        )
    }

    @Test
    fun countsGoUpPerLabelAndReset() {
        call("count")
        call("count")
        call("count", "other")
        call("countReset")
        call("count")
        assertEquals(listOf("default: 1", "default: 2", "other: 1", "default: 1"), written.map { it.second })
    }

    @Test
    fun assertWritesAnErrorUnlessTheConditionIsTrue() {
        call("assert")
        call("assert", true, "fine")
        assertTrue(written.isEmpty())

        call("assert", false, "msg")
        call("assert", false)
        assertEquals(
            listOf(
                RhinoConsole.Level.ERROR to "Assertion failed: false msg",
                RhinoConsole.Level.ERROR to "Assertion failed: false",
            ),
            written
        )
    }

    @Test
    fun anObjectIsNotATrueCondition() {
        call("assert", js("({})"))
        assertEquals(1, written.size)
    }

    @Test
    fun tableFramesOneLinePerItem() {
        val frame = "─".repeat(17)
        call("table", js("['a', 'b']"))
        call("table", js("({k: 1})"))
        call("table", "plain")
        call("table", null)
        call("table")
        assertEquals(
            listOf(
                "┌$frame┐\n│ 0: a\n│ 1: b \n└$frame┘",
                "┌$frame┐\n│ k: 1 \n└$frame┘",
                "plain",
                "null",
            ),
            written.map { it.second }
        )
    }

    @Test
    fun clearCallsTheHandler() {
        call("clear")
        assertEquals(1, cleared)
        assertTrue(written.isEmpty())
    }
}
