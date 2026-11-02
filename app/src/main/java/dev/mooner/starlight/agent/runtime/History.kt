package dev.mooner.starlight.agent.runtime

import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role

/** Reads and changes a conversation's messages the way runs need it. */
internal object History {

    /**
     * The latest assistant turn and those of its tool calls that have no result yet, or null when the model is to go
     * on. Notices are skipped: they are for the user, not part of the turns.
     */
    fun openCalls(messages: List<ChatMessage>): Pair<ChatMessage, List<Part.ToolCall>>? {
        val turns = messages.filter(::takesPart)
        val index = turns.indexOfLast { it.role != Role.TOOL }
        val turn = turns.getOrNull(index)?.takeIf { it.role == Role.ASSISTANT && it.toolCalls.isNotEmpty() } ?: return null
        val answered = turns.drop(index + 1).flatMapTo(HashSet()) { message ->
            message.parts.filterIsInstance<Part.ToolResult>().map { it.callId }
        }
        val open = turn.toolCalls.filter { it.id !in answered }
        return if (open.isEmpty()) null else turn to open
    }

    /**
     * Adds [results] for calls of the turn [turnId] to the tool message that answers it, or appends one made by
     * [newMessage]. Keeping one tool message per turn keeps every result of a turn together for the providers.
     */
    fun addResults(
        messages: List<ChatMessage>,
        turnId: String,
        results: List<Part.ToolResult>,
        newMessage: () -> ChatMessage,
    ): List<ChatMessage> {
        val turn = messages.indexOfFirst { it.id == turnId }
        if (turn < 0)
            return messages
        var answer = -1
        for (index in turn + 1 until messages.size) {
            val message = messages[index]
            if (!takesPart(message))
                continue
            if (message.role == Role.TOOL)
                answer = index
            break
        }
        if (answer < 0)
            return messages + newMessage()
        return messages.toMutableList().also { it[answer] = it[answer].copy(parts = it[answer].parts + results) }
    }

    /** The project a conversation made of [messages] is about, from the project notes in the user's messages. */
    fun projectAt(messages: List<ChatMessage>): String? {
        var project: String? = null
        for (message in messages) {
            if (message.role != Role.USER)
                continue
            for (part in message.parts) {
                if (part !is Part.Context || part.label in Part.Context.HIDDEN)
                    continue
                project = part.label.takeIf { it != Part.Context.NO_PROJECT }
            }
        }
        return project
    }

    private fun takesPart(message: ChatMessage): Boolean =
        message.parts.any { it !is Part.Notice }
}
