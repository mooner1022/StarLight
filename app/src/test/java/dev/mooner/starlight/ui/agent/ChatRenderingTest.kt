package dev.mooner.starlight.ui.agent

import dev.mooner.starlight.agent.model.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tables come out of replies as their own blocks. */
class MarkdownBlocksTest {

    @Test
    fun splitsTablesFromProse() {
        val markdown = """
            두 엔진을 비교하면:

            | 엔진 | 문법 | `export` |
            |:-----|:----:|---:|
            | NeonJS | ES2023 | 필요 |
            | Rhino | ES5 \| 일부 |
            끝.
        """.trimIndent()
        val blocks = MarkdownBlocks.split(markdown)
        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlocks.Block.Text("두 엔진을 비교하면:"), blocks[0])
        val table = blocks[1] as MarkdownBlocks.Block.Table
        assertEquals(listOf("엔진", "문법", "`export`"), table.header)
        assertEquals(listOf(listOf("NeonJS", "ES2023", "필요"), listOf("Rhino", "ES5 | 일부", "")), table.rows)
        assertEquals(listOf(android.view.Gravity.START, android.view.Gravity.CENTER_HORIZONTAL, android.view.Gravity.END), table.aligns)
        assertEquals(MarkdownBlocks.Block.Text("끝."), blocks[2])
    }

    @Test
    fun leavesCodeAndLookalikesAlone() {
        val fenced = "```\n| a | b |\n|---|---|\n| 1 | 2 |\n```"
        assertEquals(listOf(MarkdownBlocks.Block.Text(fenced)), MarkdownBlocks.split(fenced))
        // A heading underline is not a table delimiter when the column counts differ.
        val heading = "a | b | c\n---"
        assertEquals(listOf(MarkdownBlocks.Block.Text(heading)), MarkdownBlocks.split(heading))
    }

    @Test
    fun keepsAHalfStreamedTable() {
        val blocks = MarkdownBlocks.split("| a | b |\n|---|---|\n| 1 |")
        assertEquals(1, blocks.size)
        assertEquals(listOf(listOf("1", "")), (blocks[0] as MarkdownBlocks.Block.Table).rows)
    }
}

/** A run's finished tool calls fold under one header, with the latest left in view. */
class ToolGroupTest {

    private fun tool(id: String, done: Boolean = true) = ChatRow.Tool(
        call = Part.ToolCall(id, "read_file", "{}"),
        result = if (done) Part.ToolResult(id, "read_file", "ok", isError = false, summary = null) else null,
        running = !done,
    )

    @Test
    fun foldsAllButTheLatestCall() {
        val run = listOf(
            ChatRow.Thinking("t0", "plan"),
            tool("a"),
            ChatRow.Thinking("t1", "next"),
            ChatRow.Text("x", "Found it, fixing."),
            tool("b"),
            tool("c", done = false),
            ChatRow.Thinking("t2", "wrap up"),
            ChatRow.Text("y", "Done"),
        )
        val collapsed = ChatRows.collapse(run, emptySet())
        assertEquals(
            listOf("t0", "group:a", "x", "tool:c", "y"),
            collapsed.map { it.key },
        )
        assertEquals(ChatRow.ToolGroup("group:a", 3, expanded = false), collapsed[1])

        val expanded = ChatRows.collapse(run, setOf("group:a"))
        assertEquals(listOf("t0", "group:a", "tool:a", "t1", "x", "tool:b", "tool:c", "t2", "y"), expanded.map { it.key })
        assertTrue((expanded[1] as ChatRow.ToolGroup).expanded)
    }

    @Test
    fun leavesASingleCallAlone() {
        val run = listOf(tool("a"), ChatRow.Text("x", "Done"))
        assertEquals(run, ChatRows.collapse(run, emptySet()))
    }
}
