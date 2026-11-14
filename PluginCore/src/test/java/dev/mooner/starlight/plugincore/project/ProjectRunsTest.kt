package dev.mooner.starlight.plugincore.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProjectRunsTest {

    private val reports = mutableListOf<String>()
    private var threshold = 10_000L

    private val runs = ProjectRuns({ threshold }) { report ->
        reports += when (report) {
            is ProjectRuns.Report.Slow  -> "slow ${report.label} ${report.elapsedMillis / 1000} first=${report.first}"
            is ProjectRuns.Report.Ended -> "ended ${report.label}"
        }
    }

    private fun later(seconds: Long) = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)

    @Test
    fun `a run past the threshold is warned about, again after a minute, and when it ends`() {
        runs.track(null) {
            runs.label("onMessage")
            runs.check(later(5))
            assertTrue(reports.isEmpty())
            runs.check(later(11))
            runs.check(later(30))
            runs.check(later(61))
            runs.check(later(120))
        }
        assertEquals(listOf("slow onMessage 11 first=true", "slow onMessage 61 first=false", "ended onMessage"), reports)
        assertFalse(runs.isActive)
    }

    @Test
    fun `a quick run says nothing, and a threshold of 0 turns the warnings off`() {
        runs.track("setTimeout") { runs.check(later(1)) }
        threshold = 0
        runs.track("setTimeout") { runs.check(later(100)) }
        assertTrue(reports.toString(), reports.isEmpty())
    }

    @Test
    fun `a threshold of a minute or more warns once`() {
        threshold = 90_000
        runs.track("timer") {
            runs.check(later(61))
            runs.check(later(91))
            runs.check(later(200))
        }
        assertEquals(listOf("slow timer 91 first=true", "ended timer"), reports)
    }

    @Test
    fun `a run started inside another is its own, and the outer one keeps its name`() {
        runs.track("outer") {
            runs.track("inner") { runs.label("innerFunction") }
            runs.label("onMessage")
            runs.check(later(11))
        }
        assertEquals(listOf("slow onMessage 11 first=true", "ended onMessage"), reports)
    }

    @Test
    fun `the label comes from the code when it names it, else from the coroutine`() {
        runs.track("setInterval") { runs.check(later(11)) }
        assertEquals("slow setInterval 11 first=true", reports.first())
    }
}
