package dev.mooner.starlight.agent.model

/**
 * Keeps every tool call of a conversation answered. The providers reject a history in which a call has no result right
 * after its turn, and a call can be left without one: when the app is closed while a tool runs (results are saved one
 * by one), or when a run fails between calls. Once a user message follows such a turn, every request fails.
 */
object CallResults {

    /** For the call that was running when the app's process ended; its outcome isn't known. */
    const val INTERRUPTED = "The app was closed while this call ran, possibly because it crashed or stopped responding, so " +
        "whether it took effect is unknown. Check before running it again; if it ran a script (compile_project, " +
        "send_debug_message), the script may have hung the app."

    /** For the call that waited for the user when the app's process ended. */
    const val INTERRUPTED_WAITING = "Not run: the app was closed while this call waited for the user's approval or answer."

    /** For the calls after it, which didn't start. */
    const val INTERRUPTED_BEFORE = "Not run: the app was closed before the calls before this one in your turn finished."

    /** For a call found without a result only as a request is made. */
    const val MISSING = "No result was saved for this call, so whether it ran is unknown."

    /**
     * [messages] with the calls a process that ended left open closed (see [close]): the one the run was at as
     * [INTERRUPTED], or [INTERRUPTED_WAITING] when the run [waited] for the user, the rest of its turn as
     * [INTERRUPTED_BEFORE], and those of earlier turns as [MISSING].
     */
    fun closeInterrupted(messages: List<ChatMessage>, waited: Boolean, now: Long): List<ChatMessage> =
        close(messages, now) { call, place ->
            val output = when (place) {
                Place.FIRST -> if (waited) INTERRUPTED_WAITING else INTERRUPTED
                Place.LATER -> INTERRUPTED_BEFORE
                Place.EARLIER_TURN -> MISSING
            }
            Part.ToolResult(call.id, call.name, output, isError = true,
                summary = if (place == Place.EARLIER_TURN) "No result" else "Interrupted")
        }

    /** [messages] with every call that has no result closed as [MISSING]. */
    fun closeMissing(messages: List<ChatMessage>, now: Long): List<ChatMessage> =
        close(messages, now) { call, _ ->
            Part.ToolResult(call.id, call.name, MISSING, isError = true, summary = "No result")
        }

    /** A turn and those of its calls that have no result in the tool message right after it. */
    data class Unanswered(val turn: ChatMessage, val calls: List<Part.ToolCall>)

    /** Where an unanswered call is, which tells what can be said about it. */
    enum class Place {
        /** The first open call of the [current] turn: the one the run was at, running it or waiting for the user. */
        FIRST,
        /** One after it in that turn, which didn't start. */
        LATER,
        /** One of an earlier turn, left open elsewhere, such as by an older version: nothing is known about it. */
        EARLIER_TURN,
    }

    /**
     * The turn a run goes on with, and its calls that have no result: the last turn the model took part in, when it is
     * the assistant's and has open calls; null otherwise.
     */
    fun current(messages: List<ChatMessage>): Unanswered? {
        val last = messages.lastOrNull { takesPart(it) && it.role != Role.TOOL } ?: return null
        return unanswered(messages).lastOrNull()?.takeIf { it.turn.id == last.id }
    }

    /** Every turn with unanswered calls, oldest first. Notices are skipped: the model never sees them. */
    fun unanswered(messages: List<ChatMessage>): List<Unanswered> {
        val found = mutableListOf<Unanswered>()
        for ((index, message) in messages.withIndex()) {
            if (message.role != Role.ASSISTANT || message.toolCalls.isEmpty())
                continue
            val answered = answerOf(messages, index)?.let(messages::get)?.parts.orEmpty()
                .filterIsInstance<Part.ToolResult>()
                .mapTo(HashSet()) { it.callId }
            val calls = message.toolCalls.filter { it.id !in answered }
            if (calls.isNotEmpty())
                found += Unanswered(message, calls)
        }
        return found
    }

    /**
     * [messages] with a result for every unanswered call: [result] makes it from the call and its [Place] (a turn's
     * calls run in order, so the first open one of the [current] turn is the call the run was at). Results go into the
     * tool message right after the turn, or a new one made by [newMessage] right after it: not at the end, where the
     * user's next message may already be.
     */
    fun close(
        messages: List<ChatMessage>,
        now: Long,
        newMessage: (List<Part.ToolResult>) -> ChatMessage = { results -> ChatMessage(newMessageId("t"), Role.TOOL, results, now) },
        result: (call: Part.ToolCall, place: Place) -> Part.ToolResult,
    ): List<ChatMessage> {
        val open = unanswered(messages)
        if (open.isEmpty())
            return messages
        val current = current(messages)?.turn?.id
        val out = messages.toMutableList()
        // From the last turn back, so that inserting a message keeps the earlier indexes right.
        for ((turn, calls) in open.asReversed()) {
            val index = out.indexOfFirst { it.id == turn.id }
            val results = calls.mapIndexed { i, call ->
                result(call, when {
                    turn.id != current -> Place.EARLIER_TURN
                    i == 0 -> Place.FIRST
                    else -> Place.LATER
                })
            }
            val answer = answerOf(out, index)
            if (answer != null)
                out[answer] = out[answer].copy(parts = out[answer].parts + results)
            else
                out.add(index + 1, newMessage(results))
        }
        return out
    }

    /**
     * [messages] without results that answer no call of the turn before them, and without the tool messages that leaves
     * empty: the providers reject those too.
     */
    fun withoutStrayResults(messages: List<ChatMessage>): List<ChatMessage> {
        var calls: Set<String> = emptySet()
        val out = ArrayList<ChatMessage>(messages.size)
        for (message in messages) {
            when {
                !takesPart(message) -> out += message
                message.role == Role.ASSISTANT -> {
                    calls = message.toolCalls.mapTo(HashSet()) { it.id }
                    out += message
                }
                message.role == Role.TOOL -> {
                    val parts = message.parts.filter { it !is Part.ToolResult || it.callId in calls }
                    if (parts.any { it is Part.ToolResult })
                        out += message.copy(parts = parts)
                    calls = emptySet()
                }
                else -> {
                    calls = emptySet()
                    out += message
                }
            }
        }
        return out
    }

    /**
     * The index of the tool message answering the turn at [turn], notices skipped; null when none follows it. The one
     * rule for where a turn's results are, which the run loop's History follows too.
     */
    fun answerOf(messages: List<ChatMessage>, turn: Int): Int? {
        for (index in turn + 1 until messages.size) {
            val message = messages[index]
            if (!takesPart(message))
                continue
            return index.takeIf { message.role == Role.TOOL }
        }
        return null
    }

    /** Whether [message] is part of what the model sees: notices aren't. */
    fun takesPart(message: ChatMessage): Boolean =
        message.parts.any { it !is Part.Notice }
}
