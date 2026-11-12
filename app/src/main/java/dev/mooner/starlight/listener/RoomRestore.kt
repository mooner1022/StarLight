/*
 * RoomRestore.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

import dev.mooner.starlight.plugincore.chat.ChatRoom

/**
 * The rooms StarLight can send to again after it starts: those whose message notifications were still shown, which
 * carry the reply action a room sends through. Picks them, and tells when the attempt is over, so that what waits for
 * rooms, as stored scheduled tasks, runs after it.
 */
internal object RoomRestore {

    /** How long after the app starts what waits for rooms waits at most, when the listener isn't connected. */
    const val SETTLE_TIMEOUT_MILLIS = 10_000L

    var clock: () -> Long = System::currentTimeMillis

    @Volatile
    private var startedAt: Long? = null

    @Volatile
    private var attempted = false

    /**
     * The rooms to register from the notifications still [shown]: for a room with several, the one posted last; none
     * already known. A notification [parse] throws on is passed over.
     */
    fun <N> pick(shown: List<N>, postTime: (N) -> Long, parse: (N) -> ChatRoom?, isKnown: (roomId: String) -> Boolean): List<ChatRoom> {
        val latest = LinkedHashMap<String, Pair<Long, ChatRoom>>()
        for (notification in shown) {
            val room = try {
                parse(notification)
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                // A spec built against another version of the app or of Android, as handleMessage allows for.
                null
            } ?: continue
            if (isKnown(room.id))
                continue
            val time = postTime(notification)
            val earlier = latest[room.id]
            if (earlier == null || time > earlier.first)
                latest[room.id] = time to room
        }
        return latest.values.map { it.second }
    }

    /** Called once, as the app's initialization begins. */
    fun appStarted() {
        startedAt = clock()
    }

    /** Called when rooms were restored once, whether or not it worked. */
    fun markAttempted() {
        attempted = true
    }

    /** Whether rooms were restored once, or [SETTLE_TIMEOUT_MILLIS] passed since the app started. */
    fun isSettled(): Boolean {
        if (attempted)
            return true
        // Projects may compile before the listener is connected, or before the app even says it started.
        val started = startedAt ?: return false
        return clock() - started >= SETTLE_TIMEOUT_MILLIS
    }

    /** For tests: as before the app started. */
    internal fun reset() {
        startedAt = null
        attempted = false
        clock = System::currentTimeMillis
    }
}
