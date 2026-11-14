package dev.mooner.starlight.script.schedule

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class ScheduleTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(text: String, zone: ZoneId = seoul): ZonedDateTime =
        LocalDateTime.parse(text).atZone(zone)

    private fun next(cron: String, after: String): String? =
        CronExpression.parse(cron).next(at(after))?.toLocalDateTime()?.toString()

    @Test
    fun `cron finds the next minute its fields allow`() {
        // 2026-11-13 is a Friday.
        assertEquals("2026-11-16T09:00", next("0 9 * * 1-5", "2026-11-13T10:00"))
        assertEquals("2026-11-13T09:00", next("0 9 * * MON-FRI", "2026-11-13T08:59:30"))
        assertEquals("2026-11-12T10:15", next("*/15 * * * *", "2026-11-12T10:07"))
        assertEquals("2026-11-12T10:15", next("*/15 * * * *", "2026-11-12T10:00"))
        assertEquals("2026-11-12T10:30", next("0,30 * * * *", "2026-11-12T10:00"))
        assertEquals("2026-11-12T10:20", next("10-40/10 * * * *", "2026-11-12T10:10"))
        assertEquals("2026-11-12T10:50", next("50/5 * * * *", "2026-11-12T10:40"))
        // Sunday is 0 and 7; 2026-11-15 is a Sunday.
        assertEquals("2026-11-15T00:00", next("0 0 * * 7", "2026-11-12T12:00"))
        assertEquals("2026-11-15T00:00", next("0 0 * * SUN", "2026-11-12T12:00"))
        assertEquals("2027-01-04T09:00", next("0 9 * JAN MON", "2026-11-12T12:00"))
        // Both day fields restricted: a day matching either. 2026-11-13 is the 13th and a Friday; 2026-11-20 a Friday.
        assertEquals("2026-11-20T09:00", next("0 9 13 * 5", "2026-11-13T10:00"))
        assertEquals("2026-12-04T09:00", next("0 9 13 * 5", "2026-11-27T10:00"))
        assertEquals("2026-12-13T09:00", next("0 9 13 * 5", "2026-12-11T10:00"))
        assertEquals("2028-02-29T00:00", next("0 0 29 2 *", "2026-11-12T00:00"))
        assertNull(next("0 0 31 2 *", "2026-11-12T00:00"))
    }

    @Test
    fun `a cron expression that isn't one says why`() {
        for ((text, says) in listOf(
            "0 9 * *" to "five fields",
            "60 * * * *" to "minute",
            "a * * * *" to "minute",
            "5-1 * * * *" to "goes up",
            "*/0 * * * *" to "step",
            "0 9 * 13 *" to "month",
            "0 9 * * 8" to "day of the week",
            "0 9 ,1 * *" to "empty",
        )) {
            val error = runCatching { CronExpression.parse(text) }.exceptionOrNull()
            assertTrue("$text: $error", error is IllegalArgumentException && error.message!!.contains(says))
        }
    }

    @Test
    fun `daily runs today when the time is still to come, else tomorrow`() {
        val daily = ScheduleSpec.Daily.parse("9:00")
        assertEquals("daily 09:00", daily.description)
        assertEquals(at("2026-11-12T09:00").toInstant(), daily.next(at("2026-11-12T08:00").toInstant(), seoul))
        assertEquals(at("2026-11-13T09:00").toInstant(), daily.next(at("2026-11-12T09:00").toInstant(), seoul))
        assertEquals("daily 21:30:15", ScheduleSpec.Daily.parse("21:30:15").description)
        assertTrue(runCatching { ScheduleSpec.Daily.parse("25:00") }.exceptionOrNull() is IllegalArgumentException)
        // A time a daylight-saving change skips runs right after the gap.
        val newYork = ZoneId.of("America/New_York")
        val gap = ScheduleSpec.Daily.parse("02:30").next(at("2026-03-08T00:00", newYork).toInstant(), newYork)
        assertEquals("2026-03-08T03:30", gap.atZone(newYork).toLocalDateTime().toString())
    }

    // ---- Schedules, with a test clock and projects --------------------------------------------------------------

    private class Owner(var readiness: Schedules.Readiness = Schedules.Readiness.RUN) : Schedules.Owner {
        override val id = UUID.randomUUID()!!
        val runs = mutableListOf<String>()
        val notes = mutableListOf<String>()
        /** Runs end at once, unless held. */
        var hold = false
        private val held = mutableListOf<() -> Unit>()

        override fun readiness() = readiness
        override fun run(name: String, block: () -> Unit, onEnd: () -> Unit) {
            block()
            if (hold) held += onEnd else onEnd()
        }
        override fun note(message: String) { notes += message }
        fun release() { held.forEach { it() }; held.clear() }
    }

    private var now = 0L
    private val owners = mutableListOf<Owner>()
    private val log = mutableListOf<String>()

    private fun owner(readiness: Schedules.Readiness = Schedules.Readiness.RUN) = Owner(readiness).also { owners += it }

    private fun millis(text: String) = at(text).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        now = millis("2026-11-12T08:00")
        Schedules.clock = { now }
        Schedules.zone = { seoul }
    }

    @After
    fun tearDown() {
        owners.forEach { Schedules.cancelAll(it.id) }
        Schedules.clock = System::currentTimeMillis
        Schedules.zone = ZoneId::systemDefault
    }

    private fun add(owner: Owner, spec: ScheduleSpec, label: String) =
        Schedules.add(owner, spec, "Schedule.test") { run -> log += "$label late=${run.lateMillis}" }

    @Test
    fun `a daily task runs when due, and is set for the next day`() {
        val owner = owner()
        val task = add(owner, ScheduleSpec.Daily.parse("09:00"), "daily")
        assertEquals(millis("2026-11-12T09:00"), task.next)
        assertEquals(millis("2026-11-12T09:00"), Schedules.runDue(now))
        assertTrue(log.isEmpty())
        now = millis("2026-11-12T09:00") + 250
        Schedules.runDue(now)
        assertEquals(listOf("daily late=250"), log)
        assertEquals(millis("2026-11-13T09:00"), task.next)
    }

    @Test
    fun `runs missed while the device slept are made once`() {
        val owner = owner()
        val task = add(owner, ScheduleSpec.Cron(CronExpression.parse("*/10 * * * *")), "cron")
        now = millis("2026-11-12T09:03")
        Schedules.runDue(now)
        assertEquals(listOf("cron late=${millis("2026-11-12T09:03") - millis("2026-11-12T08:10")}"), log)
        assertEquals(millis("2026-11-12T09:10"), task.next)
    }

    @Test
    fun `a once task in the past runs at once, then is gone`() {
        val owner = owner()
        val task = add(owner, ScheduleSpec.Once(Instant.ofEpochMilli(now - 1_000)), "once")
        Schedules.runDue(now)
        assertEquals(listOf("once late=1000"), log)
        assertNull(task.next)
        assertTrue(Schedules.tasksOf(owner.id).isEmpty())
    }

    @Test
    fun `a task due while its project is off is skipped, and one while it compiles waits`() {
        val off = owner(Schedules.Readiness.SKIP)
        val offTask = add(off, ScheduleSpec.Once(Instant.ofEpochMilli(now)), "off")
        val compiling = owner(Schedules.Readiness.WAIT)
        val waiting = add(compiling, ScheduleSpec.Once(Instant.ofEpochMilli(now)), "compiling")
        assertEquals(now + Schedules.COMPILE_RETRY_MILLIS, Schedules.runDue(now))
        assertTrue(log.isEmpty())
        assertNull(offTask.next)
        assertEquals(1, off.notes.size)
        assertEquals(now, waiting.next)
        compiling.readiness = Schedules.Readiness.RUN
        now += 1_000
        Schedules.runDue(now)
        assertEquals(listOf("compiling late=1000"), log)
    }

    @Test
    fun `a run due while the last one still runs is skipped`() {
        val owner = owner().apply { hold = true }
        val task = add(owner, ScheduleSpec.Cron(CronExpression.parse("* * * * *")), "busy")
        now = millis("2026-11-12T08:01")
        Schedules.runDue(now)
        now = millis("2026-11-12T08:02")
        Schedules.runDue(now)
        assertEquals(1, log.size)
        assertEquals(1, owner.notes.size)
        owner.release()
        assertFalse(task.running)
        now = millis("2026-11-12T08:03")
        Schedules.runDue(now)
        assertEquals(2, log.size)
    }

    @Test
    fun `tasks are cancelled one by one or all at once, and a project has a limit`() {
        val owner = owner()
        val first = add(owner, ScheduleSpec.Daily.parse("09:00"), "a")
        add(owner, ScheduleSpec.Daily.parse("10:00"), "b")
        assertTrue(Schedules.cancel(first))
        assertFalse(Schedules.cancel(first))
        assertEquals(1, Schedules.tasksOf(owner.id).size)
        assertEquals(1, Schedules.cancelAll(owner.id))
        repeat(Schedules.MAX_TASKS) { add(owner, ScheduleSpec.Daily.parse("09:00"), "n") }
        assertTrue(runCatching { add(owner, ScheduleSpec.Daily.parse("09:00"), "over") }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { add(owner(), ScheduleSpec.Cron(CronExpression.parse("0 0 31 2 *")), "never") }
            .exceptionOrNull() is IllegalArgumentException)
    }
}
