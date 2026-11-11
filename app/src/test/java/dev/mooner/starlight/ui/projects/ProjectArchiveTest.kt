package dev.mooner.starlight.ui.projects

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** What a project zip holds. */
class ProjectArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun exportLeavesOutLogsAndTheDebugRoom() {
        val root = tmp.newFolder("bot")
        root.resolve("project.json").writeText("{}")
        root.resolve("index.js").writeText("export function onMessage(msg) {}")
        root.resolve("images").mkdirs()
        root.resolve("images/cat.png").writeBytes(byteArrayOf(1, 2, 3))
        root.resolve("logs.json").writeText("[]")
        root.resolve("debugroom/chats/media").mkdirs()
        root.resolve("debugroom/chats.json").writeText("[]")
        root.resolve("debugroom/chats/media/video.mp4").writeBytes(ByteArray(1024))
        // Only the debug room at the top is left out; a folder of the same name deeper in is the project's own.
        root.resolve("data/debugroom").mkdirs()
        root.resolve("data/debugroom/keep.txt").writeText("kept")

        val out = ByteArrayOutputStream()
        ProjectArchive.export(root, out)

        val entries = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry?.name }.toSortedSet()
        }
        assertEquals(sortedSetOf("data/debugroom/keep.txt", "images/cat.png", "index.js", "project.json"), entries)
    }

    @Test
    fun anImportedProjectIsTurnedOffAndKeepsTheRestOfItsInfo() {
        val file = tmp.newFile("project.json")
        file.writeText("""{"id": "4cac5884", "name": "bot", "isEnabled": true, "apiLevels": ["starlight"], "added_later": 3}""")

        ProjectArchive.turnOff(file)

        val info = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(JsonPrimitive(false), info["isEnabled"])
        assertEquals(setOf("id", "name", "isEnabled", "apiLevels", "added_later"), info.keys)
        assertEquals(JsonPrimitive(3), info["added_later"])
    }

    @Test
    fun aProjectAlreadyOffIsLeftAsItIs() {
        val file = tmp.newFile("project.json")
        val text = """{"name": "bot",   "isEnabled": false}"""
        file.writeText(text)

        ProjectArchive.turnOff(file)

        assertEquals(text, file.readText())
    }
}
