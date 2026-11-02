package dev.mooner.starlight.agent.model

import kotlinx.serialization.Serializable

/**
 * Marks a message holding a summary of the conversation before it, written when the conversation grew too long for the
 * model. Requests send the summary in place of what it covers; the conversation itself keeps every message.
 */
@Serializable
data class Compaction(
    /** The first message kept as it is after the summary; null when the summary covers everything before it. */
    val keepFrom: String? = null,
    /** How many messages the summary covers. */
    val covers: Int = 0,
) {
    companion object {
        /**
         * What requests send of [messages]: from the last summary on, the summary first, then the messages it kept.
         * Without a summary, all of them.
         */
        fun view(messages: List<ChatMessage>): List<ChatMessage> {
            val at = messages.indexOfLast { it.compaction != null }
            if (at < 0)
                return messages
            val marker = messages[at]
            val keep = marker.compaction?.keepFrom
                ?.let { id -> messages.indexOfFirst { it.id == id } }
                ?.takeIf { it in 0 until at }
            val kept = if (keep != null) messages.subList(keep, messages.size).filter { it !== marker }
                else messages.subList(at + 1, messages.size)
            return listOf(marker) + kept
        }
    }
}
