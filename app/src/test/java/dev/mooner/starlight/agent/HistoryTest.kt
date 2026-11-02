package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.NoticeKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.runtime.History
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Finding the calls a run still has to make, keeping a turn's results together, and the project at a point. */
class HistoryTest {

    private fun user(id: String, vararg parts: Part) = ChatMessage(id, Role.USER, parts.toList(), 0)
    private fun assistant(id: String, vararg parts: Part) = ChatMessage(id, Role.ASSISTANT, parts.toList(), 0)
    private fun tool(id: String, vararg results: Part.ToolResult) = ChatMessage(id, Role.TOOL, results.toList(), 0)
    private fun call(id: String) = Part.ToolCall(id, "write_file", "{}")
    private fun result(callId: String) = Part.ToolResult(callId, "write_file", "ok")
    private fun notice() = assistant("n" + System.nanoTime(), Part.Notice(NoticeKind.STOPPED, "Stopped."))

    @Test
    fun openCallsAreTheUnansweredCallsOfTheLastTurn() {
        val turn = assistant("a1", Part.Text("Writing"), call("c1"), call("c2"), call("c3"))
        // The app was killed after the first call: its result is saved, the other two are left.
        val messages = listOf(user("u1", Part.Text("make a bot")), turn, tool("t1", result("c1")))
        val (open, calls) = History.openCalls(messages)!!
        assertEquals("a1", open.id)
        assertEquals(listOf("c2", "c3"), calls.map { it.id })
    }

    @Test
    fun noOpenCallsOnceEveryCallHasAResultOrTheModelAnswered() {
        val turn = assistant("a1", call("c1"), call("c2"))
        assertNull(History.openCalls(listOf(user("u1"), turn, tool("t1", result("c1"), result("c2")))))
        assertNull(History.openCalls(listOf(user("u1"), assistant("a1", Part.Text("Done")))))
        assertNull(History.openCalls(listOf(user("u1"))))
    }

    @Test
    fun noticesDontHideOpenCalls() {
        val messages = listOf(user("u1"), assistant("a1", call("c1")), notice())
        assertEquals(listOf("c1"), History.openCalls(messages)!!.second.map { it.id })
    }

    @Test
    fun resultsOfOneTurnGoToOneToolMessage() {
        val turn = assistant("a1", call("c1"), call("c2"))
        var messages = listOf(user("u1"), turn)
        messages = History.addResults(messages, "a1", listOf(result("c1"))) { tool("t1", result("c1")) }
        messages = History.addResults(messages, "a1", listOf(result("c2"))) { tool("t2", result("c2")) }
        assertEquals(listOf("u1", "a1", "t1"), messages.map { it.id })
        assertEquals(listOf("c1", "c2"), messages.last().parts.filterIsInstance<Part.ToolResult>().map { it.callId })
        assertNull(History.openCalls(messages))
    }

    @Test
    fun resultsAfterANoticeStillJoinTheTurnsToolMessage() {
        val messages = listOf(user("u1"), assistant("a1", call("c1"), call("c2")), tool("t1", result("c1")), notice())
        val updated = History.addResults(messages, "a1", listOf(result("c2"))) { tool("t2", result("c2")) }
        assertEquals(messages.size, updated.size)
        assertEquals(listOf("c1", "c2"), updated[2].parts.filterIsInstance<Part.ToolResult>().map { it.callId })
    }

    @Test
    fun aTurnThatIsGoneGetsNoResults() {
        val messages = listOf(user("u1"))
        assertEquals(messages, History.addResults(messages, "a1", listOf(result("c1"))) { tool("t1", result("c1")) })
    }

    @Test
    fun projectFollowsTheProjectNotesUpToThePoint() {
        val opened = user("u1", Part.Context("hello-bot", "opened from"), Part.Context(Part.Context.INSTRUCTIONS, "be brief"), Part.Text("hi"))
        val switched = user("u2", Part.Context("echo-bot", "switched to"), Part.Text("now this"))
        val detached = user("u3", Part.Context(Part.Context.NO_PROJECT, "detached"), Part.Text("any"))
        val nudge = user("u4", Part.Context(Part.Context.NUDGE, "reply"))
        assertNull(History.projectAt(emptyList()))
        assertEquals("hello-bot", History.projectAt(listOf(opened)))
        assertEquals("echo-bot", History.projectAt(listOf(opened, assistant("a1", Part.Text("ok")), switched)))
        assertNull(History.projectAt(listOf(opened, switched, detached)))
        assertEquals("echo-bot", History.projectAt(listOf(opened, switched, nudge)))
    }
}
