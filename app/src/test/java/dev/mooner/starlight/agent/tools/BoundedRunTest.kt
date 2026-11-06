package dev.mooner.starlight.agent.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** compile_project's bounded wait: a stuck script gives the call a result anyway. */
class BoundedRunTest {

    @Test
    fun workThatEndsInTimeGivesItsError() = runBlocking {
        val ok = BoundedRun.run("ok", 5_000) { }!!
        assertTrue(ok.finished)
        assertNull(ok.error)
        val failed = BoundedRun.run("failed", 5_000) { throw IllegalStateException("boom") }!!
        assertTrue(failed.finished)
        assertEquals("boom", failed.error?.message)
    }

    @Test
    fun stuckWorkEndsTheWaitAndGoesOn() = runBlocking {
        val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val start = System.nanoTime()
        val stuck = BoundedRun.run("stuck", 200, onEnd = { ended.countDown() }) { release.await() }!!
        val waited = (System.nanoTime() - start) / 1_000_000
        assertFalse(stuck.finished)
        assertTrue("waited $waited ms", waited in 150..3_000)
        assertTrue(stuck.thread.isAlive)
        // It ends whenever it does, and says so.
        release.countDown()
        assertTrue(ended.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun aRunThatMayNotStartLeavesItsThreadUnstarted() = runBlocking {
        var seen: Thread? = null
        val outcome = BoundedRun.run("refused", 5_000, canStart = { seen = it; false }) { error("must not run") }
        assertNull(outcome)
        assertEquals(Thread.State.NEW, seen!!.state)
    }

    @Test
    fun stoppingTheRunStopsTheWait() = runBlocking {
        val release = CountDownLatch(1)
        val wait = async { BoundedRun.run("cancelled", 60_000) { release.await() } }
        delay(100)
        val start = System.nanoTime()
        wait.cancel()
        runCatching { wait.await() }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
        release.countDown()
    }
}
