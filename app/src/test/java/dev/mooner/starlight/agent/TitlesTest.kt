package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.TitleSource
import dev.mooner.starlight.agent.runtime.Titles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversation titles: the placeholder, what the model is shown and what is kept of its answer, and who wins. */
class TitlesTest {

    private fun user(text: String, vararg extra: Part) = ChatMessage("u${text.hashCode()}", Role.USER, extra.toList() + Part.Text(text), 0)
    private fun reply(text: String) = ChatMessage("a${text.hashCode()}", Role.ASSISTANT, listOf(Part.Text(text)), 0)
    private fun conversation(vararg messages: ChatMessage, source: TitleSource = TitleSource.FIRST_LINE) =
        Conversation(id = "c1", title = "make a bot", createdAt = 0, updatedAt = 0, messages = messages.toList(), titleSource = source)

    @Test
    fun thePlaceholderIsTheFirstLineCut() {
        assertEquals("make a weather bot", Titles.placeholder("\n  make a weather bot\nthat posts at 8", emptyList()))
        val long = "a".repeat(100)
        assertEquals(Titles.MAX_LENGTH, Titles.placeholder(long, emptyList()).length)
        assertTrue(Titles.placeholder(long, emptyList()).endsWith("…"))
        val file = Part.Attachment("f1", "log.txt", "text/plain", 3, AttachmentKind.TEXT)
        assertEquals("log.txt", Titles.placeholder(" ", listOf(file)))
    }

    @Test
    fun theTitleIsTakenOutOfWhatModelsWrapItIn() {
        assertEquals("날씨 알림 봇 만들기", Titles.clean("\"날씨 알림 봇 만들기\""))
        assertEquals("Weather bot setup", Titles.clean("Title: **Weather bot setup.**\n\nThis names the task."))
        assertEquals("주식 봇 오류 수정", Titles.clean("제목: 「주식 봇 오류 수정」"))
        assertEquals("Fix echo_bot replies", Titles.clean("# Fix echo_bot replies"))
        assertNull(Titles.clean("  \n "))
        assertNull(Titles.clean("\"\""))
        assertEquals(Titles.MAX_LENGTH, Titles.clean("word ".repeat(30))!!.length)
    }

    @Test
    fun theModelSeesTheAskingAndTheLatestReplyOnly() {
        val project = Part.Context("weather", "This conversation was opened from the StarLight project \"weather\".")
        val hidden = ChatMessage("n1", Role.USER, listOf(Part.Context(Part.Context.NUDGE, "Continue.")), 0)
        val file = Part.Attachment("f1", "error.png", "image/png", 3, AttachmentKind.IMAGE)
        val excerpt = Titles.excerpt(conversation(
            user("hi", project), reply("Hello! What should we build?"),
            user("a bot that posts the weather", file), hidden, reply("**Done**: the bot posts at 8."),
        ).copy(project = "weather"))
        assertTrue(excerpt.startsWith("Project: weather"))
        assertTrue("hi" in excerpt)
        assertTrue("a bot that posts the weather [error.png]" in excerpt)
        assertTrue("Done: the bot posts at 8." in excerpt)
        // Not the app's own context, the hidden nudge, or replies before the latest.
        assertFalse("opened from" in excerpt)
        assertFalse("Continue." in excerpt)
        assertFalse("What should we build" in excerpt)
    }

    @Test
    fun thereIsSomethingToNameOnceTheAgentAnswered() {
        assertFalse(Titles.canName(conversation(user("make a bot"))))
        assertTrue(Titles.canName(conversation(user("make a bot"), reply("Made it."))))
    }

    @Test
    fun theModelNamesOnlyWhatNobodyNamed() {
        assertEquals(TitleSource.MODEL, Titles.named(conversation(), "Weather bot").titleSource)
        assertEquals("Weather bot", Titles.named(conversation(), "Weather bot").title)
        // The user renamed it while the model was thinking.
        val mine = conversation(source = TitleSource.USER).copy(title = "Mine")
        assertEquals("Mine", Titles.named(mine, "Weather bot").title)
    }

    @Test
    fun aRenameIsTheUsersAndSticks() {
        val renamed = Titles.renamed(conversation(), "  My   weather bot ")!!
        assertEquals("My weather bot", renamed.title)
        assertEquals(TitleSource.USER, renamed.titleSource)
        assertNull(Titles.renamed(conversation(), "   "))
        // Rewriting the first message keeps the user's name.
        assertEquals(renamed, Titles.beforeMessage(renamed, "something else", emptyList()))
    }

    @Test
    fun rewritingTheFirstMessageStartsTheTitleOver() {
        val named = Titles.named(conversation(), "Weather bot")
        val restarted = Titles.beforeMessage(named, "a stock bot", emptyList())
        assertEquals("a stock bot", restarted.title)
        assertEquals(TitleSource.FIRST_LINE, restarted.titleSource)
        // A later message leaves it.
        val going = named.copy(messages = listOf(user("make a bot"), reply("Done.")))
        assertEquals(going, Titles.beforeMessage(going, "now add a command", emptyList()))
    }

    @Test
    fun theSourceIsLeftOutOfFilesUntilSomeoneNamesIt() {
        val placeholder = AgentJson.encodeToString(conversation())
        assertFalse("titleSource" in placeholder)
        // Files from before titles had a source read as placeholders.
        assertEquals(TitleSource.FIRST_LINE, AgentJson.decodeFromString<Conversation>(placeholder).titleSource)
        val mine = AgentJson.encodeToString(conversation(source = TitleSource.USER))
        assertTrue("\"titleSource\":\"user\"" in mine)
        assertEquals(TitleSource.USER, AgentJson.decodeFromString<Conversation>(mine).titleSource)
    }
}
