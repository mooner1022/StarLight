/*
 * NotificationDrops.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

/**
 * The notifications that looked like chat messages and weren't handed to any project, by why. "The bot doesn't
 * answer in this messenger" has a handful of causes, and each used to leave no trace at all, or, for a parser that
 * fails, the same long error for every message.
 *
 * Each cause is counted per app and user, and [note] says when one is seen for the first time, which is when the
 * listener writes a log about it: once, not once a message. The problem solver shows the counts.
 */
internal class NotificationDrops {

    enum class Reason {
        /** No package rule names the app. */
        NO_RULE,
        /** A rule names the app, for another user of the device: a dual app or a work profile. */
        OTHER_USER,
        /** The rule's parser spec isn't registered. */
        UNKNOWN_SPEC,
        /** The parser spec didn't take the notification as a message. */
        NOT_A_MESSAGE,
        /** The parser spec, or handing the message on, failed. */
        FAILED,
    }

    /** [detail] tells failures apart: the kind of error and where it was thrown. */
    data class Key(val reason: Reason, val packageName: String, val userId: Int, val specId: String? = null, val detail: String? = null)

    class Count(val times: Int, val lastAt: Long)

    private val counts = LinkedHashMap<Key, Count>()

    /** Counts one more of [key]; true when it is the first, the one to write a log for. */
    @Synchronized
    fun note(key: Key, now: Long): Boolean {
        val before = counts[key]
        // Apps that post notifications are few; a parser failing in ever new ways isn't to fill the memory.
        if (before == null && counts.size >= MAX_KEYS)
            counts.remove(counts.keys.first())
        counts[key] = Count((before?.times ?: 0) + 1, now)
        return before == null
    }

    /** What was dropped, the most often first. */
    @Synchronized
    fun snapshot(): List<Pair<Key, Count>> =
        counts.entries.map { it.key to it.value }.sortedByDescending { it.second.times }

    /** Forgets everything, as when the rules change: what they dropped before says nothing of them now. */
    @Synchronized
    fun reset() = counts.clear()

    companion object {
        private const val MAX_KEYS = 128

        /** What tells one failure from another: the error's class and the place it was thrown from. */
        fun signatureOf(error: Throwable): String =
            error.javaClass.name + "@" + error.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
    }
}
