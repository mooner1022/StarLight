package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.store.CheckpointStore
import dev.mooner.starlight.agent.store.Rewind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** What the agent changed per turn, and putting it back. */
class CheckpointTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { CheckpointStore(tmp.newFolder("attachments")) }

    /** Projects as folders under one directory, with what the rewind asked of them. */
    private inner class FakeProjects : Rewind.Projects {
        val root: File = tmp.newFolder("projects")
        val restoredArchives = mutableListOf<String>()
        val events = mutableMapOf<String, List<String>>()

        fun project(name: String): File = File(root, name).apply { mkdirs() }

        override fun directory(name: String): File? = File(root, name).takeIf(File::isDirectory)
        override fun remove(name: String) { File(root, name).deleteRecursively() }
        override fun restore(archive: File) {
            restoredArchives += archive.readText()
            project("restored")
        }
        override fun setEvents(name: String, allowedEventIds: List<String>) { events[name] = allowedEventIds }
    }

    @Test
    fun aTurnKeepsWhatEachFileWasBeforeItsFirstChange() {
        val projects = FakeProjects()
        val bot = projects.project("bot")
        File(bot, "main.js").writeText("v1")
        val turn = store.recorder("c1", "m1")
        turn.file("bot", bot, "main.js")
        File(bot, "main.js").writeText("v2")
        // A second change in the same turn keeps the first copy.
        turn.file("bot", bot, "main.js")
        File(bot, "main.js").writeText("v3")
        turn.file("bot", bot, "lib/new.js")
        File(bot, "lib").mkdirs()
        File(bot, "lib/new.js").writeText("made")

        val changes = store.changes("c1", listOf("m1"))
        assertEquals(listOf("bot/main.js", "bot/lib/new.js"), changes.map { Rewind.describe(it.entry) })
        val result = Rewind.apply(changes, projects)
        assertEquals("v1", File(bot, "main.js").readText())
        // A file that didn't exist goes away.
        assertFalse(File(bot, "lib/new.js").exists())
        assertEquals(setOf("bot"), result.touched)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun rewindingSeveralTurnsTakesTheEarliestState() {
        val projects = FakeProjects()
        val bot = projects.project("bot")
        val file = File(bot, "main.js")
        file.writeText("start")
        store.recorder("c1", "m1").file("bot", bot, "main.js")
        file.writeText("after m1")
        store.recorder("c1", "m2").file("bot", bot, "main.js")
        file.writeText("after m2")

        // Back to before m2: the state m2 found.
        Rewind.apply(store.changes("c1", listOf("m2")), projects)
        assertEquals("after m1", file.readText())
        file.writeText("after m2")
        // Back to before m1: the state m1 found, though m2 changed the file too.
        Rewind.apply(store.changes("c1", listOf("m1", "m2")), projects)
        assertEquals("start", file.readText())
    }

    @Test
    fun projectsComeAndGoBeforeTheirFiles() {
        val projects = FakeProjects()
        // Created in this turn, then given a file: rewinding removes it, files and all.
        val turn = store.recorder("c1", "m1")
        turn.project("made")
        val made = projects.project("made")
        turn.file("made", made, "main.js")
        File(made, "main.js").writeText("new bot")
        // Deleted in this turn: rewinding loads it from the copy.
        turn.project("gone", bytes = 3) { it.write("zip".toByteArray()) }
        // Its events changed.
        val old = projects.project("old")
        turn.info("old", CheckpointStore.SavedInfo(listOf("starlight.message.create"), isEnabled = true))

        val result = Rewind.apply(store.changes("c1", listOf("m1")), projects)
        assertFalse(made.exists())
        assertEquals(listOf("zip"), projects.restoredArchives)
        assertEquals(listOf("starlight.message.create"), projects.events["old"])
        assertEquals(mapOf("old" to true), result.enabled)
        assertTrue(old.exists())
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun whatWasTooLargeOrOutsideIsReportedNotTouched() {
        val projects = FakeProjects()
        val bot = projects.project("bot")
        val big = File(bot, "data.bin")
        big.writeBytes(ByteArray((CheckpointStore.MAX_FILE_BYTES + 1).toInt()))
        val turn = store.recorder("c1", "m1")
        turn.file("bot", bot, "data.bin")
        turn.file("bot", bot, "../escape.txt")
        File(bot, "data.bin").writeText("changed")

        val result = Rewind.apply(store.changes("c1", listOf("m1")), projects)
        assertEquals("changed", big.readText())
        assertEquals(2, result.failed.size)
        assertTrue(result.restored.isEmpty())
    }

    @Test
    fun discardedTurnsHaveNothingToRewind() {
        val projects = FakeProjects()
        val bot = projects.project("bot")
        store.recorder("c1", "m1").file("bot", bot, "a.js")
        store.recorder("c1", "m2").file("bot", bot, "b.js")
        store.discard("c1", listOf("m2"))
        assertEquals(listOf("bot/a.js"), store.changes("c1", listOf("m1", "m2")).map { Rewind.describe(it.entry) })
        assertTrue(store.changes("c2", listOf("m1")).isEmpty())
    }
}
