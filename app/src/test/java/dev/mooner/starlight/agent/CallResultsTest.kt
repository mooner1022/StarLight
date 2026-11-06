package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.CallResults
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.NoticeKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Closing calls left without a result, wherever they are, as an ended process or a failed run leaves them. */
class CallResultsTest {

    private fun user(id: String) = ChatMessage(id, Role.USER, listOf(Part.Text("hi")), 0)
    private fun assistant(id: String, vararg calls: String) =
        ChatMessage(id, Role.ASSISTANT, listOf(Part.Text("on it")) + calls.map { Part.ToolCall(it, "compile_project", "{}") }, 0)
    private fun tool(id: String, vararg callIds: String) =
        ChatMessage(id, Role.TOOL, callIds.map { Part.ToolResult(it, "compile_project", "ok") }, 0)
    private fun notice(id: String) = ChatMessage(id, Role.ASSISTANT, listOf(Part.Notice(NoticeKind.ERROR, "failed")), 0)

    private fun close(messages: List<ChatMessage>) =
        CallResults.close(messages, { ChatMessage("new", Role.TOOL, it, 0) }) { call, first ->
            Part.ToolResult(call.id, call.name, if (first) "first" else "later", isError = true)
        }

    private fun results(message: ChatMessage) =
        message.parts.filterIsInstance<Part.ToolResult>().map { it.callId to it.output }

    @Test
    fun aResultGoesIntoTheToolMessageOfItsTurnBeforeTheUserMessage() {
        // What the ANR left: one result saved, the user's message after it.
        val messages = listOf(user("u1"), assistant("a1", "c1", "c2"), tool("t1", "c1"), user("u2"))
        val closed = close(messages)
        assertEquals(listOf("u1", "a1", "t1", "u2"), closed.map { it.id })
        assertEquals(listOf("c1" to "ok", "c2" to "first"), results(closed[2]))
    }

    @Test
    fun aTurnWithNoToolMessageGetsOneRightAfterIt() {
        val messages = listOf(user("u1"), assistant("a1", "c1", "c2"), user("u2"))
        val closed = close(messages)
        assertEquals(listOf("u1", "a1", "new", "u2"), closed.map { it.id })
        assertEquals(listOf("c1" to "first", "c2" to "later"), results(closed[2]))
    }

    @Test
    fun noticesBetweenATurnAndItsResultsAreSkipped() {
        val messages = listOf(user("u1"), assistant("a1", "c1", "c2"), notice("n1"), tool("t1", "c1"), user("u2"))
        val closed = close(messages)
        assertEquals(listOf("u1", "a1", "n1", "t1", "u2"), closed.map { it.id })
        assertEquals(listOf("c1" to "ok", "c2" to "first"), results(closed[3]))
    }

    @Test
    fun aHistoryWithEveryCallAnsweredIsLeftAsItIs() {
        val messages = listOf(user("u1"), assistant("a1", "c1"), tool("t1", "c1"), assistant("a2"))
        assertSame(messages, close(messages))
        assertTrue(CallResults.unanswered(messages).isEmpty())
    }

    @Test
    fun openCallsInSeveralTurnsAreAllClosedInPlace() {
        val messages = listOf(user("u1"), assistant("a1", "c1"), user("u2"), assistant("a2", "c2", "c3"), tool("t2", "c2"), user("u3"))
        val closed = close(messages)
        assertEquals(listOf("u1", "a1", "new", "u2", "a2", "t2", "u3"), closed.map { it.id })
        assertEquals(listOf("c1" to "first"), results(closed[2]))
        assertEquals(listOf("c2" to "ok", "c3" to "first"), results(closed[5]))
    }

    @Test
    fun anInterruptedRunSaysWhichCallWasRunningAndWhichWaited() {
        val messages = listOf(user("u1"), assistant("a1", "c1", "c2", "c3"), tool("t1", "c1"))
        val running = CallResults.closeInterrupted(messages, waited = false, now = 0)
        assertEquals(listOf(CallResults.INTERRUPTED, CallResults.INTERRUPTED_BEFORE),
            running[2].parts.filterIsInstance<Part.ToolResult>().drop(1).map { it.output })
        val waiting = CallResults.closeInterrupted(messages, waited = true, now = 0)
        assertEquals(listOf(CallResults.INTERRUPTED_WAITING, CallResults.INTERRUPTED_BEFORE),
            waiting[2].parts.filterIsInstance<Part.ToolResult>().drop(1).map { it.output })
        assertTrue(waiting[2].parts.filterIsInstance<Part.ToolResult>().drop(1).all { it.isError })
    }

    @Test
    fun callsFoundOpenOnlyLaterAreClosedAsUnknown() {
        val closed = CallResults.closeMissing(listOf(user("u1"), assistant("a1", "c1", "c2"), tool("t1", "c1")), now = 0)
        assertEquals(listOf(CallResults.MISSING), closed[2].parts.filterIsInstance<Part.ToolResult>().drop(1).map { it.output })
    }

    @Test
    fun resultsWithoutTheirCallAreDropped() {
        // As a summary can leave them: the turn that made the call was summarized away.
        val messages = listOf(tool("t0", "gone"), user("u1"), assistant("a1", "c1"), tool("t1", "c1", "other"))
        val kept = CallResults.withoutStrayResults(messages)
        assertEquals(listOf("u1", "a1", "t1"), kept.map { it.id })
        assertEquals(listOf("c1" to "ok"), results(kept[2]))
    }
}
