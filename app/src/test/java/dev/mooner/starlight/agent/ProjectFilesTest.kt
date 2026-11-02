package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.plainPreview
import dev.mooner.starlight.agent.tools.ProjectFiles
import dev.mooner.starlight.agent.tools.ToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The agent reaches only files inside the project it works on. */
class ProjectFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun files(): ProjectFiles {
        val dir = folder.newFolder("bot")
        dir.resolve("main.js").writeText("export function onMessage(event) {\n}\n")
        return ProjectFiles(dir)
    }

    private fun assertRefused(block: () -> Unit) {
        try {
            block()
            fail("Expected the path to be refused")
        } catch (_: ToolException) {
        }
    }

    @Test
    fun refusesPathsOutsideTheProject() {
        val files = files()
        assertRefused { files.resolve("../other/main.js") }
        assertRefused { files.resolve("/etc/hosts") }
        assertRefused { files.resolve("lib/../../secret") }
        assertEquals("lib/util.js", files.relative(files.resolve("lib/./util.js")))
    }

    @Test
    fun editsTheOneOccurrence() {
        val files = files()
        files.edit("main.js", "{\n}", "{\n    event.room.send(\"hi\");\n}", null)
        assertTrue(files.read("main.js").second.contains("event.room.send"))
        assertRefused { files.edit("main.js", "missing", "x", null) }
        files.write("dup.js", "a a", null)
        assertRefused { files.edit("dup.js", "a", "b", null) }
    }

    @Test
    fun checksHashesBeforeWriting() {
        val files = files()
        val hash = ProjectFiles.sha256(files.resolve("main.js").readBytes())
        files.write("main.js", "changed", hash)
        assertRefused { files.write("main.js", "again", hash) }
        assertRefused { files.write("main.js", "again", "") }
        files.write("new.js", "fresh", "")
    }

    @Test
    fun searchesTextFiles() {
        val files = files()
        val hits = files.search("onmessage", isRegex = false, ignoreCase = true, limit = 10)
        assertEquals(listOf("main.js:1: export function onMessage(event) {"), hits)
    }
}

class PreviewTest {
    @Test
    fun previewsDropMarkdown() {
        val text = "**agent_test** 봇을 만들었어요\n\n- `/안녕`에 답해요\n\n```js\ncode()\n```\n# Done"
        assertEquals("agent_test 봇을 만들었어요 /안녕에 답해요 Done", text.plainPreview())
    }
}
