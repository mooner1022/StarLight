package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.NoticeKind
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.ResumeReason
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.RunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeReasonTest {

    private var nextId = 0

    private fun message(role: Role, vararg parts: Part) =
        ChatMessage("m${nextId++}", role, parts.toList(), 0L)

    private fun user(text: String) = message(Role.USER, Part.Text(text))
    private fun said(text: String) = message(Role.ASSISTANT, Part.Text(text))
    private fun called() = message(Role.ASSISTANT, Part.ToolCall("call${nextId}", "read_file", "{}"))
    private fun result() = message(Role.TOOL, Part.ToolResult("call${nextId - 1}", "read_file", "ok"))
    private fun notice(kind: NoticeKind) = message(Role.ASSISTANT, Part.Notice(kind, "notice"))

    private fun conversation(state: RunState, vararg messages: ChatMessage) =
        Conversation(id = "c", title = "t", createdAt = 0L, updatedAt = 0L, messages = messages.toList(), state = state)

    @Test
    fun anAnsweredConversationHasNothingToGoOnWith() {
        assertNull(ResumeReason.of(conversation(RunState.IDLE, user("hi"), said("hello"))))
    }

    @Test
    fun theStepLimitAsksToGoOn() {
        val paused = conversation(RunState.IDLE, user("do it"), called(), result(), notice(NoticeKind.PAUSED))

        assertEquals(ResumeReason.STEP_LIMIT, ResumeReason.of(paused))
    }

    @Test
    fun theStepLimitOnATurnThatSaidSomethingStillAsksToGoOn() {
        // A turn the provider paused has text and no call, like an answer, but the run isn't over.
        val paused = conversation(RunState.IDLE, user("do it"), said("working on it"), notice(NoticeKind.PAUSED))

        assertEquals(ResumeReason.STEP_LIMIT, ResumeReason.of(paused))
    }

    @Test
    fun howTheRunEndedDecidesTheReason() {
        assertEquals(ResumeReason.STOPPED,
            ResumeReason.of(conversation(RunState.IDLE, user("do it"), called(), result(), notice(NoticeKind.STOPPED))))
        assertEquals(ResumeReason.ERROR,
            ResumeReason.of(conversation(RunState.ERROR, user("do it"), called(), result(), notice(NoticeKind.ERROR))))
        assertEquals(ResumeReason.INTERRUPTED,
            ResumeReason.of(conversation(RunState.INTERRUPTED, user("do it"), called(), result())))
        assertEquals(ResumeReason.NO_REPLY,
            ResumeReason.of(conversation(RunState.IDLE, user("do it"), called(), result())))
    }
}
