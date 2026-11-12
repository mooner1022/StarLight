package dev.mooner.starlight.plugincore.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

class ProjectExecutorTest {

    private fun executor(concurrency: Int, dropped: AtomicInteger = AtomicInteger()) =
        ProjectExecutor("test", concurrency, ProjectRuns({ 0L }) {}) { dropped.incrementAndGet() }

    @Test
    fun namesARunAfterItsCoroutineAndTimesEvents() {
        val labels = Collections.synchronizedList(ArrayList<String?>())
        val runs = ProjectRuns({ 1L }) { report -> if (report is ProjectRuns.Report.Slow) labels += report.label }
        val executor = ProjectExecutor("test", 1, runs) {}
        val later = { System.nanoTime() + TimeUnit.SECONDS.toNanos(5) }
        val done = CountDownLatch(2)
        executor.scope.launch(CoroutineName("setTimeout")) {
            runs.check(later())
            done.countDown()
        }
        executor.post {
            runs.check(later())
            done.countDown()
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        // A coroutine's own name; none for an event, whose function names it when it calls one.
        assertEquals(2, labels.size)
        assertEquals(setOf("setTimeout", null), labels.toSet())
        executor.shutdown()
    }

    @Test
    fun runsEventsOneAtATimeInArrivalOrder() {
        val executor = executor(1)
        val order = Collections.synchronizedList(ArrayList<Int>())
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val done = CountDownLatch(200)
        repeat(200) { i ->
            executor.post {
                maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                order += i
                running.decrementAndGet()
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals((0 until 200).toList(), order)
        assertEquals(1, maxRunning.get())
        executor.shutdown()
    }

    @Test
    fun runsUpToItsConcurrencyAtOnce() {
        val executor = executor(3)
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val done = CountDownLatch(12)
        repeat(12) {
            executor.post {
                maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                Thread.sleep(50)
                running.decrementAndGet()
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(3, maxRunning.get())
        executor.shutdown()
    }

    @Test
    fun dropsTheOldestEventsWhenTheBufferIsFull() {
        val dropped = AtomicInteger()
        val executor = executor(1, dropped)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.post {
            started.countDown()
            release.await()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val ran = Collections.synchronizedList(ArrayList<Int>())
        val extra = 10
        val total = ProjectExecutor.EVENT_BUFFER_SIZE + extra
        val done = CountDownLatch(ProjectExecutor.EVENT_BUFFER_SIZE)
        repeat(total) { i ->
            executor.post {
                ran += i
                done.countDown()
            }
        }
        assertEquals(extra, dropped.get())
        assertEquals(ProjectExecutor.EVENT_BUFFER_SIZE, executor.queuedEvents)
        release.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals((extra until total).toList(), ran)
        assertEquals(0, executor.queuedEvents)
        executor.shutdown()
    }

    @Test
    fun shutdownDropsPendingEventsSilently() {
        val dropped = AtomicInteger()
        val executor = executor(1, dropped)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.post {
            started.countDown()
            release.await()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val ran = AtomicInteger()
        repeat(5) { executor.post { ran.incrementAndGet() } }
        executor.shutdown()
        release.countDown()
        Thread.sleep(200)
        assertEquals(0, ran.get())
        assertEquals(0, dropped.get())
    }
}
