package dev.mooner.starlight.script.schedule

import dev.mooner.starlight.api.original.enteringRhino
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.languages.rhino.RhinoJson
import dev.mooner.starlight.script.Engines
import dev.mooner.starlight.script.ScriptTimes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class StoredScheduleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val seoul = ZoneId.of("Asia/Seoul")

    private class Owner(var readiness: Schedules.Readiness = Schedules.Readiness.RUN) : Schedules.Owner {
        override val id = UUID.randomUUID()!!
        val notes = mutableListOf<String>()
        /** Runs on a thread of their own, as on the project's worker, with what they throw kept. */
        var onWorker = false
        val failures = mutableListOf<Throwable>()

        override fun readiness() = readiness
        override fun run(name: String, block: () -> Unit, onEnd: () -> Unit) {
            if (onWorker) {
                thread {
                    try {
                        block()
                    } catch (e: Throwable) {
                        failures += e
                    } finally {
                        onEnd()
                    }
                }.join()
                return
            }
            try {
                block()
            } finally {
                onEnd()
            }
        }
        override fun note(message: String) { notes += message }
    }

    private class Home(override val directory: File) : StoredTasks.Home {
        val warnings = mutableListOf<String>()
        override fun warn(message: String) { warnings += message }
    }

    private var now = 0L
    private var settled = true
    private val owners = mutableListOf<Owner>()
    private val log = mutableListOf<String>()

    private lateinit var projectDir: File
    private lateinit var home: Home

    @Before
    fun setUp() {
        now = LocalDateTime.parse("2026-11-12T08:00").atZone(seoul).toInstant().toEpochMilli()
        Schedules.clock = { now }
        Schedules.zone = { seoul }
        projectDir = temp.newFolder("project")
        home = Home(projectDir)
    }

    @After
    fun tearDown() {
        owners.forEach { Schedules.cancelAll(it.id) }
        Schedules.clock = System::currentTimeMillis
        Schedules.zone = ZoneId::systemDefault
    }

    private fun owner(readiness: Schedules.Readiness = Schedules.Readiness.RUN) = Owner(readiness).also { owners += it }

    private fun version(owner: Owner, tasks: StoredTasks, json: ScriptJson = ScriptJson.Plain) =
        StoredSchedule(owner, tasks, json) { settled }

    /** A handler that notes its runs in [log] as `<label> <data> late=<ms>`. */
    private fun handler(label: String): (Any?, Schedules.Run) -> Unit =
        { data, run -> log += "$label $data late=${run.lateMillis}" }

    /** The tasks the file holds, as a fresh read of it would find them (as after a restart). */
    private fun onDisk(): List<StoredTasks.Entry> =
        StoredTasks(Home(projectDir)).list()

    private val file: File
        get() = projectDir.resolve("data/schedule/stored.json")

    @Test
    fun `a kept task runs once with its data when due, and leaves the file`() {
        val owner = owner()
        val schedule = version(owner, StoredTasks(home))
        schedule.on("remind", handler("remind"))
        schedule.at(Instant.ofEpochMilli(now + 60_000), "remind", mapOf("text" to "hi", "n" to 2))
        assertEquals(1, onDisk().size)
        assertTrue(file.readText().contains("\"version\": 1"))
        assertEquals(now + 60_000, Schedules.runDue(now))
        assertTrue(log.isEmpty())
        now += 60_000
        Schedules.runDue(now)
        assertEquals(listOf("remind {text=hi, n=2} late=0"), log)
        assertTrue(onDisk().isEmpty())
        now += 60_000
        assertNull(Schedules.runDue(now))
        assertEquals(1, log.size)
    }

    @Test
    fun `a kept task outlives a compile and a restart, and runs for the version that registers it`() {
        val owner = owner()
        val tasks = StoredTasks(home)
        val first = version(owner, tasks)
        first.on("remind", handler("first"))
        first.at(Instant.ofEpochMilli(now + 60_000), "remind", "a")
        first.at(Instant.ofEpochMilli(now + 120_000), "remind", "b")
        // A compile: the version's tasks go, the file stays.
        Schedules.cancelAll(owner.id)
        assertTrue(Schedules.tasksOf(owner.id).isEmpty())
        assertEquals(2, onDisk().size)
        val second = version(owner, tasks)
        second.on("remind", handler("second"))
        now += 60_000
        Schedules.runDue(now)
        assertEquals(listOf("second a late=0"), log)
        // A restart: the file is read again.
        Schedules.cancelAll(owner.id)
        val third = version(owner, StoredTasks(Home(projectDir)))
        third.on("remind", handler("third"))
        now += 60_000
        Schedules.runDue(now)
        assertEquals(listOf("second a late=0", "third b late=0"), log)
        assertTrue(onDisk().isEmpty())
    }

    @Test
    fun `a task that came due meanwhile runs right after its handler is registered, and says how late`() {
        val owner = owner()
        val tasks = StoredTasks(home)
        version(owner, tasks).at(Instant.ofEpochMilli(now - 5_000), "late", null)
        Schedules.cancelAll(owner.id)
        version(owner, tasks).on("late", handler("late"))
        assertEquals(now - 5_000, Schedules.tasksOf(owner.id).single().next)
        Schedules.runDue(now)
        assertEquals(listOf("late null late=5000"), log)
    }

    @Test
    fun `a task due while the project is off is kept, sets no alarm, and runs once it is on`() {
        val owner = owner(Schedules.Readiness.SKIP)
        val schedule = version(owner, StoredTasks(home))
        schedule.on("remind", handler("remind"))
        schedule.at(Instant.ofEpochMilli(now), "remind", 1)
        assertNull(Schedules.runDue(now))
        now += 600_000
        assertNull(Schedules.runDue(now))
        assertTrue(log.isEmpty())
        assertEquals(1, onDisk().size)
        assertTrue(owner.notes.none { it.startsWith("Skipped") })
        owner.readiness = Schedules.Readiness.RUN
        Schedules.runDue(now)
        assertEquals(listOf("remind 1 late=600000"), log)
        assertTrue(onDisk().isEmpty())
    }

    @Test
    fun `a task with no handler is kept until one is registered`() {
        val owner = owner()
        val schedule = version(owner, StoredTasks(home))
        schedule.at(Instant.ofEpochMilli(now), "later", "x")
        assertEquals(listOf("No handler for 'later' yet: register one with Schedule.on"), owner.notes)
        assertTrue(Schedules.tasksOf(owner.id).isEmpty())
        assertNull(Schedules.runDue(now))
        assertEquals(1, onDisk().size)
        now += 1_000
        schedule.on("later", handler("later"))
        Schedules.runDue(now)
        assertEquals(listOf("later x late=1000"), log)
    }

    @Test
    fun `cancelling by name or by task removes it from the file and from the version`() {
        val owner = owner()
        val schedule = version(owner, StoredTasks(home))
        schedule.on("a", handler("a"))
        schedule.on("b", handler("b"))
        schedule.at(Instant.ofEpochMilli(now + 1_000), "a", 1)
        schedule.at(Instant.ofEpochMilli(now + 2_000), "a", 2)
        val b = schedule.at(Instant.ofEpochMilli(now + 3_000), "b", 3)
        val kept = schedule.at(Instant.ofEpochMilli(now + 4_000), "b", 4)
        assertEquals(2, schedule.cancel("a"))
        assertEquals(0, schedule.cancel("a"))
        assertTrue(schedule.isActive(b))
        assertTrue(schedule.cancel(b))
        assertFalse(schedule.cancel(b))
        assertFalse(schedule.isActive(b))
        assertEquals(listOf(kept.id), onDisk().map(StoredTasks.Entry::id))
        assertEquals(1, Schedules.tasksOf(owner.id).size)
        now += 4_000
        Schedules.runDue(now)
        assertEquals(listOf("b 4 late=0"), log)
        assertFalse(schedule.isActive(kept))
    }

    @Test
    fun `the script's cancelAll leaves kept tasks, and Schedule-on replaces the handler`() {
        val owner = owner()
        val schedule = version(owner, StoredTasks(home))
        schedule.on("remind", handler("old"))
        schedule.at(Instant.ofEpochMilli(now + 1_000), "remind", "x")
        Schedules.add(owner, ScheduleSpec.Daily.parse("09:00"), "Schedule.daily") { }
        // As Schedule.cancelAll() calls it: the daily task goes, the kept one stays, in the file and in the version.
        assertEquals(1, Schedules.cancelAll(owner.id, keepStored = true))
        assertEquals(1, onDisk().size)
        assertTrue(Schedules.tasksOf(owner.id).single().stored)
        schedule.on("remind", handler("new"))
        assertEquals(1, Schedules.tasksOf(owner.id).size)
        now += 1_000
        Schedules.runDue(now)
        assertEquals(listOf("new x late=0"), log)
        // A compile's cancelAll takes every task of the version.
        schedule.at(Instant.ofEpochMilli(now + 1_000), "remind", "y")
        assertEquals(1, Schedules.cancelAll(owner.id))
        assertEquals(1, onDisk().size)
    }

    @Test
    fun `limits, names and times`() {
        val owner = owner()
        val schedule = version(owner, StoredTasks(home))
        val at = Instant.ofEpochMilli(now + 60_000)
        // 16 KiB of JSON: a string's quotes count.
        schedule.at(at, "big", "a".repeat(StoredSchedule.MAX_DATA_BYTES - 2))
        val tooBig = runCatching { schedule.at(at, "big", "a".repeat(StoredSchedule.MAX_DATA_BYTES - 1)) }.exceptionOrNull()
        assertTrue("$tooBig", tooBig is IllegalArgumentException)
        // Stored tasks don't count in the 100 of daily, cron and once, nor those in theirs.
        repeat(Schedules.MAX_TASKS) { Schedules.add(owner, ScheduleSpec.Daily.parse("09:00"), "Schedule.daily") { } }
        repeat(StoredTasks.MAX_STORED - 1) { schedule.at(at, "n", it) }
        assertEquals(StoredTasks.MAX_STORED, onDisk().size)
        val over = runCatching { schedule.at(at, "n", 0) }.exceptionOrNull()
        assertTrue("$over", over is IllegalStateException)
        schedule.on("n", handler("n"))
        assertEquals(Schedules.MAX_TASKS + StoredTasks.MAX_STORED - 1, Schedules.tasksOf(owner.id).size)

        for (name in listOf("", "a b", "a/b", "x".repeat(65), "../x")) {
            val error = runCatching { schedule.at(at, name, null) }.exceptionOrNull()
            assertTrue("'$name': $error", error is IllegalArgumentException)
            assertTrue("'$name'", runCatching { schedule.on(name, handler("x")) }.exceptionOrNull() is IllegalArgumentException)
        }
        schedule.cancel("n")
        schedule.at(at, "알림_1.x-y", null)
        schedule.at(at, "x".repeat(64), null)
        // Schedule.at refuses what ScriptTimes can't read as a time.
        for (time in listOf<Any?>(Any(), "tomorrow", null, true, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
            assertNull("$time", ScriptTimes.instantOf(time))
    }

    @Test
    fun `a file that can't be read is kept aside, and the tasks start empty`() {
        file.parentFile!!.mkdirs()
        file.writeText("{ not json")
        val tasks = StoredTasks(home)
        assertTrue(tasks.list().isEmpty())
        assertEquals(1, home.warnings.size)
        assertEquals("{ not json", projectDir.resolve("data/schedule/stored.broken.json").readText())
        assertFalse(file.exists())
        version(owner(), tasks).at(Instant.ofEpochMilli(now), "x", null)
        assertEquals(1, onDisk().size)
        // A file of another shape is kept aside too.
        file.writeText("""{"version":1,"tasks":[{"id":"1","name":"x"}]}""")
        assertTrue(StoredTasks(home).list().isEmpty())
        assertEquals(2, home.warnings.size)
    }

    @Test
    fun `kept tasks wait until rooms are settled, other tasks don't`() {
        val owner = owner()
        val schedule = version(owner, StoredTasks(home))
        settled = false
        schedule.on("remind", handler("kept"))
        schedule.at(Instant.ofEpochMilli(now), "remind", 1)
        Schedules.add(owner, ScheduleSpec.Once(Instant.ofEpochMilli(now)), "Schedule.once") { run ->
            log += "once late=${run.lateMillis}"
        }
        assertEquals(now + Schedules.COMPILE_RETRY_MILLIS, Schedules.runDue(now))
        assertEquals(listOf("once late=0"), log)
        assertEquals(1, onDisk().size)
        settled = true
        now += 1_000
        assertNull(Schedules.runDue(now))
        assertEquals(listOf("once late=0", "kept 1 late=1000"), log)
    }

    /** A script's view of one version, in an engine: `at` and `on`, and `runDue` to run what is due from the script. */
    internal class Probe(private val schedule: StoredSchedule, private val now: () -> Long) {
        fun at(name: String, data: Any?) {
            schedule.at(Instant.ofEpochMilli(now()), name, data)
        }
        fun at(name: String) {
            schedule.at(Instant.ofEpochMilli(now()), name, null)
        }
        fun on(name: String, handler: (Any?, Schedules.Run) -> Unit) =
            schedule.on(name, handler)
        fun runDue() {
            Schedules.runDue(now())
        }
    }

    @Test
    fun `data comes back as the engine's own values`() {
        var folders = 0
        Engines { json ->
            val tasks = StoredTasks(Home(temp.newFolder("engine${folders++}")))
            mapOf("Probe" to Probe(version(owner(), tasks, json)) { now })
        }.use { engines ->
            val code = """
                var got = "none";
                Probe.on("rt", function (data, run) { got = data; });
                function roundTrip(data) { got = "none"; Probe.at("rt", data); Probe.runDue(); return got; }
            """.trimIndent()
            engines.both(
                "(function (d) { return [Array.isArray(d.list), typeof d.obj, d.obj.a, d.list[1], d.list[2] === null, d.text, d.n, d.nil === null].join(','); })" +
                    "(roundTrip({ obj: { a: 1 }, list: [1, 'two', null], text: 'hi', n: 1.5, nil: null }))",
                "true,object,1,two,true,hi,1.5,true", code)
            engines.both("Array.isArray(roundTrip([1, 2])) && roundTrip([1, 2]).length", "2", code)
            engines.both("typeof roundTrip('text') + ':' + roundTrip('text')", "string:text", code)
            engines.both("roundTrip(3) === 3", "true", code)
            engines.both("roundTrip(null) === null", "true", code)
            // undefined reaches the host as null in NeonJS and as text in Rhino (ScriptJsonTest).
            assertEquals("true", engines.neon("roundTrip(undefined) === null", code))
            assertEquals("true", engines.rhino("roundTrip(undefined) === 'undefined'", code))
            // Without data, as Schedule.at(time, name).
            engines.both("(got = 'none', Probe.at('rt'), Probe.runDue(), got === null)", "true", code)
        }
    }

    @Test
    fun `Rhino data is read on a worker that has entered no context`() {
        val factory = ContextFactory()
        val scope = factory.call { cx -> cx.initStandardObjects() } as ScriptableObject
        for (entering in listOf(false, true)) {
            val json = RhinoJson(scope).let { if (entering) it.enteringRhino(factory::enterContext) else it }
            val owner = owner().apply { onWorker = true }
            val tasks = StoredTasks(Home(temp.newFolder("rhino$entering")))
            tasks.add("rt", now, """{"list":[1,2],"text":"hi"}""")
            var got: Any? = null
            version(owner, tasks, json).on("rt") { data, _ -> got = data }
            Schedules.runDue(now)
            if (!entering) {
                // What the app would do without it: the task is gone from the file, and its handler never ran.
                assertTrue("${owner.failures}", owner.failures.single() is IllegalStateException)
                assertNull(got)
                continue
            }
            assertTrue("${owner.failures}", owner.failures.isEmpty())
            val data = got as NativeObject
            assertTrue(data["list"] is NativeArray)
            assertEquals("hi", data["text"])
        }
    }

    @Test
    fun `the file is read again when it changed, as after the project is imported again`() {
        val tasks = StoredTasks(home)
        val schedule = version(owner(), tasks)
        schedule.at(Instant.ofEpochMilli(now + 60_000), "old", null)
        // Another copy of the project's folder, with tasks of its own, in its place.
        file.writeText("""{"version":1,"tasks":[{"id":"0000abcd","name":"imported","at":$now,"data":{"x":1}}]}""")
        file.setLastModified(file.lastModified() + 2_000)
        assertEquals(listOf("imported"), tasks.list().map(StoredTasks.Entry::name))
        schedule.on("imported", handler("imported"))
        Schedules.runDue(now)
        assertEquals(listOf("imported {x=1} late=0"), log)
        assertTrue(onDisk().isEmpty())
    }

    @Test
    fun `tasks kept from two threads at once all get in the file, each with its own ID`() {
        val tasks = StoredTasks(home)
        val schedule = version(owner(), tasks)
        val start = CountDownLatch(1)
        val threads = List(2) { t ->
            thread {
                start.await()
                repeat(40) { schedule.at(Instant.ofEpochMilli(now + 60_000), "t$t", it) }
            }
        }
        start.countDown()
        threads.forEach(Thread::join)
        val kept = onDisk()
        assertEquals(80, kept.size)
        assertEquals(80, kept.map(StoredTasks.Entry::id).toSet().size)
        assertTrue(kept.all { it.id.matches(Regex("[0-9a-f]{8}")) })
        assertEquals(80, tasks.list().size)
    }
}
