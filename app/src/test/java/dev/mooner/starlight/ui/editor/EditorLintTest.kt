package dev.mooner.starlight.ui.editor

import dev.mooner.starlight.script.lint.ScriptLint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class EditorLintTest {

    private val language = "test_lint_language"
    private val replies = LinkedBlockingQueue<Pair<Int, String?>>()
    private val lint = EditorLint(reply = { id, json -> replies += id to json }).apply { languageId = language }

    private fun next(): Pair<Int, String?> =
        replies.poll(5, TimeUnit.SECONDS) ?: error("no reply")

    @After
    fun tearDown() {
        ScriptLint.unregister(language)
        lint.close()
    }

    @Test
    fun `a language with no linter isn't handled, and a request for it is answered with null`() {
        assertFalse(lint.handles())
        lint.request(1, "a", "let a;")
        assertEquals(1 to null, next())
        ScriptLint.register(language) { emptyList() }
        assertTrue(lint.handles())
        lint.languageId = null
        assertFalse(lint.handles())
    }

    @Test
    fun `the findings reach the page as JSON, whatever their text, within the text checked`() {
        val source = "let 가 = '😀';\r\nconst k = 1; k = 2;"
        var given: ScriptLint.Request? = null
        ScriptLint.register(language) { request ->
            given = request
            listOf(
                ScriptLint.Diagnostic(27, 28, ScriptLint.Severity.ERROR, "'k' \"is\" constant.\n</script>", code = "const-assign"),
                ScriptLint.Diagnostic(4, 5, ScriptLint.Severity.WARNING, "이름   😀"),
                ScriptLint.Diagnostic(-3, 999, ScriptLint.Severity.WARNING, "outside"),
                ScriptLint.Diagnostic(9, 2, ScriptLint.Severity.ERROR, "backwards"),
            )
        }
        lint.request(7, "a", source)
        val (id, json) = next()
        assertEquals(7, id)
        // The page gets exactly the text it sent, not a file's.
        assertEquals(source, given!!.source)
        assertNull(given!!.project)
        val list = Json.parseToJsonElement(json!!).jsonArray.map { it.jsonObject }
        fun field(index: Int, name: String) = list[index][name]?.jsonPrimitive?.content
        assertEquals(listOf("27", "28", "error", "'k' \"is\" constant.\n</script>", "const-assign"),
            listOf("start", "end", "severity", "message", "code").map { field(0, it) })
        assertEquals(listOf("4", "5", "warning", "이름   😀", null),
            listOf("start", "end", "severity", "message", "code").map { field(1, it) })
        assertEquals(listOf("0", source.length.toString()), listOf(field(2, "start"), field(2, "end")))
        assertEquals(listOf("9", "9"), listOf(field(3, "start"), field(3, "end")))
        // Put in a script as it is: nothing in it ends a string or a line of the script.
        assertFalse(json.contains('\n'))
        assertFalse(json.contains(' '))
    }

    @Test
    fun `a linter that throws, or goes too deep, is answered with null and the next check still runs`() {
        var mode = 0
        fun deep(n: Int): Int = deep(n + 1) + 1
        ScriptLint.register(language) {
            when (mode) {
                0 -> throw IllegalStateException("broken")
                1 -> { deep(0); emptyList() }
                else -> emptyList()
            }
        }
        lint.request(1, "a", "x")
        assertEquals(1 to null, next())
        mode = 1
        lint.request(2, "a", "x")
        assertEquals(2 to null, next())
        mode = 2
        lint.request(3, "a", "x")
        assertEquals(3 to "[]", next())
    }

    @Test
    fun `a tab's check is given up when the tab asks again, another tab's isn't`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = LinkedBlockingQueue<String>()
        ScriptLint.register(language) { request ->
            if (request.source == "first") {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            seen += "${request.source}:${request.isCancelled()}"
            emptyList()
        }
        lint.request(1, "a", "first")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        lint.request(2, "b", "other")
        lint.request(3, "a", "second")
        release.countDown()
        assertEquals(listOf(1, 2, 3), List(3) { next().first })
        assertEquals(listOf("first:true", "other:false", "second:false"), List(3) { seen.poll(5, TimeUnit.SECONDS) })
    }

    @Test
    fun `after the editor is closed, a request is answered with null`() {
        ScriptLint.register(language) { emptyList() }
        lint.close()
        lint.request(1, "a", "x")
        assertEquals(1 to null, next())
    }
}
