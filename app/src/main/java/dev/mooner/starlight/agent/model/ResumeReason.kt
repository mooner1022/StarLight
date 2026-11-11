package dev.mooner.starlight.agent.model

/** Why a conversation stopped before the model answered, for the screen to offer going on. */
enum class ResumeReason {
    /** Android ended the run for having run too long. */
    TIME_LIMIT,
    ERROR,
    /** The app was closed during the run. */
    INTERRUPTED,
    /** The user stopped it. */
    STOPPED,
    /** The run reached the most steps one may take, and waits to be told to go on. */
    STEP_LIMIT,
    /** It ended on tool results with nothing said. */
    NO_REPLY;

    companion object {

        /** Why [conversation], which isn't running, stopped short, or null when continuing has nothing to offer. */
        fun of(conversation: Conversation): ResumeReason? {
            val messages = conversation.messages
            val lastTurn = messages.indexOfLast { message -> message.compaction == null && message.parts.any { it !is Part.Notice } }
            val last = messages.getOrNull(lastTurn) ?: return null
            // The notices after the last turn say how the run ended.
            val notices = messages.drop(lastTurn + 1).flatMap { it.parts }.filterIsInstance<Part.Notice>().map { it.kind }
            // The model said something and asked for nothing: answered. Unless the step limit fell on a turn the
            // provider itself had paused, which looks the same and isn't.
            if (last.role == Role.ASSISTANT && last.toolCalls.isEmpty())
                return if (NoticeKind.PAUSED in notices) STEP_LIMIT else null
            return when {
                NoticeKind.TIME_LIMIT in notices           -> TIME_LIMIT
                // The error itself is in the chat right above.
                conversation.state == RunState.ERROR       -> ERROR
                conversation.state == RunState.INTERRUPTED -> INTERRUPTED
                // Asking again would end the same way; the notice says so, and the user can write instead.
                NoticeKind.NO_REPLY in notices || NoticeKind.REFUSAL in notices -> null
                NoticeKind.STOPPED in notices              -> STOPPED
                // Before the step limit had its own kind, its notice was an INFO one.
                NoticeKind.PAUSED in notices || NoticeKind.INFO in notices -> STEP_LIMIT
                // The agent put its plan right after its reply, as the app asked; that ends a run.
                notices.isEmpty() && Plan.endsOnUpdate(messages) -> null
                // Ended on tool results with nothing said, as older versions did when the model gave an empty answer.
                else                                       -> NO_REPLY
            }
        }
    }
}
