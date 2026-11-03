package dev.mooner.starlight.agent.runtime

import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Conversation
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.TitleSource
import dev.mooner.starlight.agent.model.plainPreview

/**
 * Conversation titles: the first line of the user's first message to begin with, then a name the model gives from
 * what the conversation is about, unless the user names it first.
 */
internal object Titles {

    /** Titles the app makes are cut to this; the user's own may be longer, up to [MAX_USER_LENGTH]. */
    const val MAX_LENGTH = 48
    const val MAX_USER_LENGTH = 80

    /** What the model is told when it is asked for a title, in place of the agent's system prompt. */
    const val SYSTEM = "You name conversations in StarLight, an Android app where people talk to an AI agent that " +
        "builds, edits and tests chat bot scripts. Read the excerpt of a conversation and reply with its title only. " +
        "The title says what the user wants done or asked about, specifically: the bot, feature or problem, not the " +
        "greeting. Keep it to 6 words at most, about 20 characters in Korean or Japanese. Write it in the language the " +
        "user writes in, as plain text: no quotes, emoji, markdown or final period."

    private const val MESSAGE_CHARS = 500
    private const val REPLY_CHARS = 700
    private const val LATEST_MESSAGES = 2

    /** The first line of [text], or the first attachment's name, as the title until the model names it. */
    fun placeholder(text: String, attachments: List<Part.Attachment>): String =
        cap(text.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
            ?: attachments.firstOrNull()?.name
            ?: "New conversation")

    /** [title] on one line, cut to [max] with an ellipsis. */
    fun cap(title: String, max: Int = MAX_LENGTH): String {
        val line = title.replace(Regex("\\s+"), " ").trim()
        return if (line.length > max) line.take(max - 1).trimEnd() + "…" else line
    }

    /** [conversation] with the title the model gave, unless someone named it before the model answered. */
    fun named(conversation: Conversation, title: String): Conversation =
        if (conversation.titleSource == TitleSource.FIRST_LINE) conversation.copy(title = title, titleSource = TitleSource.MODEL)
        else conversation

    /** [conversation] named by the user; null when [name] is blank. Nothing renames it after this. */
    fun renamed(conversation: Conversation, name: String): Conversation? =
        cap(name, MAX_USER_LENGTH).takeIf(String::isNotEmpty)?.let { conversation.copy(title = it, titleSource = TitleSource.USER) }

    /**
     * [conversation] about to take a message, [text] with [attachments]. When it holds no message of the user's, as
     * after rewriting the first one, its title starts over from this message, unless the user named it.
     */
    fun beforeMessage(conversation: Conversation, text: String, attachments: List<Part.Attachment>): Conversation =
        if (conversation.messages.none { it.role == Role.USER } && conversation.titleSource != TitleSource.USER)
            conversation.copy(title = placeholder(text, attachments), titleSource = TitleSource.FIRST_LINE)
        else conversation

    /** Whether there is something to name yet: the user wrote, and the agent answered in words. */
    fun canName(conversation: Conversation): Boolean =
        conversation.messages.any { userText(it).isNotEmpty() } &&
            conversation.messages.any { it.role == Role.ASSISTANT && it.text.isNotBlank() }

    /**
     * What the model reads to name [conversation]: its project, the user's first and latest messages, and the agent's
     * latest reply. Only text: attachments go by name.
     */
    fun excerpt(conversation: Conversation): String {
        val asked = conversation.messages.map(::userText).filter(String::isNotEmpty)
        val reply = conversation.messages.lastOrNull { it.role == Role.ASSISTANT && it.text.isNotBlank() }?.text
        return buildString {
            conversation.project?.let { append("Project: ").append(it).append("\n\n") }
            asked.firstOrNull()?.let { append("The user's first message:\n").append(it.take(MESSAGE_CHARS)).append("\n\n") }
            val latest = asked.drop(1).takeLast(LATEST_MESSAGES)
            if (latest.isNotEmpty())
                append("The user's latest messages:\n").append(latest.joinToString("\n---\n") { it.take(MESSAGE_CHARS) }).append("\n\n")
            reply?.let { append("The agent's latest reply:\n").append(it.plainPreview().take(REPLY_CHARS)) }
        }.trim()
    }

    /** The title in the model's reply: its first line, without the quotes, markdown or label models tend to add. */
    fun clean(reply: String): String? {
        val line = reply.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty) ?: return null
        val title = line
            .replace(Regex("^(#+|[-*>])\\s*"), "")
            .replace(Regex("^(title|제목)\\s*[:：]\\s*", RegexOption.IGNORE_CASE), "")
            // Underscores stay: bot names like weather_bot have them far more often than emphasis does.
            .replace(Regex("\\*+|`+"), "")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』')
            .trim()
            .trimEnd('.', '。')
            .trim()
        return title.takeIf(String::isNotEmpty)?.let(::cap)
    }

    /** What the user wrote in [message], with the names of the files they attached, or "" for other messages. */
    private fun userText(message: ChatMessage): String {
        if (!message.isUserTurn || message.compaction != null)
            return ""
        val files = message.parts.filterIsInstance<Part.Attachment>().map { "[${it.name}]" }
        return (listOf(message.text.trim()) + files).filter(String::isNotEmpty).joinToString(" ").plainPreview()
    }
}
