package dev.mooner.starlight.agent.runtime

import dev.mooner.starlight.agent.model.CallResults
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role

/** Reads and changes a conversation's messages the way runs need it. */
internal object History {

    /**
     * The latest assistant turn and those of its tool calls that have no result yet, or null when the model is to go
     * on. Notices are skipped: they are for the user, not part of the turns. The calls [CallResults.current] finds, so
     * that closing them and running them agree on which they are.
     */
    fun openCalls(messages: List<ChatMessage>): Pair<ChatMessage, List<Part.ToolCall>>? =
        CallResults.current(messages)?.let { it.turn to it.calls }

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
        val answer = CallResults.answerOf(messages, turn)
            ?: return messages + newMessage()
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
}
