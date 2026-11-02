package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Compaction
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.provider.RequestContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What requests send of a conversation that has been summarized. */
class CompactionTest {

    private fun user(id: String) = ChatMessage(id, Role.USER, listOf(Part.Text("say $id")), 0)
    private fun reply(id: String) = ChatMessage(id, Role.ASSISTANT, listOf(Part.Text("reply $id")), 0)
    private fun summary(id: String, keepFrom: String?, covers: Int) = ChatMessage(id, Role.USER,
        listOf(Part.Context(Part.Context.INSTRUCTIONS, "summary $id")), 0, compaction = Compaction(keepFrom, covers))

    @Test
    fun withoutASummaryEverythingIsSent() {
        val messages = listOf(user("u1"), reply("a1"))
        assertEquals(messages, Compaction.view(messages))
    }

    @Test
    fun aSummaryTakesThePlaceOfWhatItCovers() {
        // Summarized while idle: the summary covers all before it, and what follows comes after it.
        val messages = listOf(user("u1"), reply("a1"), user("u2"), reply("a2"), summary("s1", null, 4), user("u3"))
        assertEquals(listOf("s1", "u3"), Compaction.view(messages).map { it.id })
    }

    @Test
    fun aSummaryDuringARunKeepsTheRunAsItIs() {
        // Summarized before a request in the run that u2 started: u2 and what came of it stay, after the summary.
        val messages = listOf(user("u1"), reply("a1"), user("u2"), reply("a2"), summary("s1", "u2", 2), reply("a3"))
        assertEquals(listOf("s1", "u2", "a2", "a3"), Compaction.view(messages).map { it.id })
        assertEquals(listOf("s1", "u2", "a2", "a3"), RequestContent.visibleMessages(messages).map { it.id })
    }

    @Test
    fun onlyTheLatestSummaryCounts() {
        val messages = listOf(user("u1"), reply("a1"), summary("s1", null, 2), user("u2"), reply("a2"), summary("s2", null, 5),
            user("u3"))
        assertEquals(listOf("s2", "u3"), Compaction.view(messages).map { it.id })
    }

    @Test
    fun aMessageWithoutASummaryLeavesTheFieldOut() {
        assertFalse(AgentJson.encodeToString(ChatMessage.serializer(), user("u1")).contains("compaction"))
    }
}
