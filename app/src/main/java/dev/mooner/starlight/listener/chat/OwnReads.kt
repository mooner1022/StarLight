/*
 * OwnReads.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.chat

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * The rooms StarLight just had KakaoTalk read, by a "mark as read" or by a share that opens the room.
 *
 * KakaoTalk then takes the room's notification away as it does when the room's latest message is deleted: the same
 * reason, the same notifications. A removal right after StarLight's own read is that read, not a deletion. A reply
 * needs none of this: it starts the deduper over, after which no removal counts until the next message.
 *
 * @param clock Milliseconds that only go forward.
 */
internal class OwnReads(private val clock: () -> Long) {

    /** Until when a removal in each room is put down to StarLight's read, by room ID. */
    private val until = ConcurrentHashMap<String, Long>()

    /** Until when a removal in any room is, for a read whose room isn't known. */
    @Volatile
    private var anyUntil = Long.MIN_VALUE

    /**
     * StarLight is having the room [roomId] read, or a room it can't name when [roomId] is `null` or `"0"`; KakaoTalk
     * takes its notification away within [forMillis].
     */
    fun note(roomId: String?, forMillis: Long) {
        val now = clock()
        val end = now + forMillis
        if (roomId == null || roomId == "0") {
            anyUntil = maxOf(anyUntil, end)
            return
        }
        until.merge(roomId, end, ::maxOf)
        until.entries.removeIf { it.value < now }
    }

    /** Whether a notification of the room [roomId] taken away now is StarLight's own read. */
    fun explains(roomId: String?): Boolean {
        val now = clock()
        if (now < anyUntil)
            return true
        return roomId != null && (until[roomId] ?: return false) > now
    }

    companion object {

        /** A "mark as read": KakaoTalk takes the notification away within a fraction of a second. */
        const val READ_MILLIS = 3_000L

        /** A share: KakaoTalk starts, opens the room and reads it, which may take a few seconds. */
        const val SHARE_MILLIS = 15_000L

        val shared = OwnReads(SystemClock::elapsedRealtime)
    }
}
