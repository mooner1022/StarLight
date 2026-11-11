/*
 * MessageDeduper.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

/**
 * Tells a message that was delivered again from a new one. KakaoTalk gives each message a chat log id. Other
 * messengers have none, which reads as [NO_ID] for every message, so theirs are told apart by the notification's key
 * and the time it was posted: the same notification handed over twice has both, a new message has a new time.
 */
internal class MessageDeduper {

    /** The chat log id of the last message taken, [NO_ID] if it had none, [NONE] before any or after [reset]. */
    var lastChatLogId: Long = NONE
        private set
    private var lastKey: String? = null
    private var lastPostTime: Long = NONE

    /** Whether this message is the one taken last. If not, it is remembered as the last. */
    @Synchronized
    fun isRepeat(chatLogId: Long, key: String, postTime: Long): Boolean {
        val repeat = if (chatLogId != NO_ID)
            chatLogId == lastChatLogId
        else
            lastChatLogId == NO_ID && key == lastKey && postTime == lastPostTime
        if (!repeat) {
            lastChatLogId = chatLogId
            lastKey = key
            lastPostTime = postTime
        }
        return repeat
    }

    /** Forgets the last message, so the same one counts as new again. */
    @Synchronized
    fun reset() {
        lastChatLogId = NONE
        lastKey = null
        lastPostTime = NONE
    }

    companion object {
        /** What a notification without a chat log id gives. */
        const val NO_ID = 0L
        private const val NONE = -1L
    }
}
