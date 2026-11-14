/*
 * SessionManager.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.chat

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import java.math.BigInteger

private val LOG = LoggerFactory.logger {  }

/**
 * Which action of a notification replies, and which marks as read. Pure over how an action is read, so that it can be
 * tested without Android.
 */
internal object ActionChoice {

    const val SEMANTIC_REPLY = 1
    const val SEMANTIC_MARK_AS_READ = 2

    /**
     * The reply action of [actions]: the first with the reply meaning that takes input, else the first that takes
     * input, else the first whose title contains "reply" or "답장". `null` when none.
     */
    fun <A : Any> reply(actions: List<A>, semantic: (A) -> Int, takesInput: (A) -> Boolean, title: (A) -> String?): A? =
        actions.firstOrNull { semantic(it) == SEMANTIC_REPLY && takesInput(it) }
            ?: actions.firstOrNull(takesInput)
            ?: actions.firstOrNull { titleHas(title(it), "reply", "답장") }

    /**
     * The mark-as-read action of [actions]: the first with that meaning, else the first without input whose title
     * contains "read" or "읽음". `null` when none.
     */
    fun <A : Any> read(actions: List<A>, semantic: (A) -> Int, takesInput: (A) -> Boolean, title: (A) -> String?): A? =
        actions.firstOrNull { semantic(it) == SEMANTIC_MARK_AS_READ }
            ?: actions.firstOrNull { !takesInput(it) && titleHas(title(it), "read", "읽음") }

    private fun titleHas(title: String?, vararg words: String): Boolean {
        val lower = title?.lowercase() ?: return false
        return words.any(lower::contains)
    }
}

/**
 * The session of one posted notification, the second argument of `onNotificationPosted` and of API2's
 * `notificationPosted` listeners.
 *
 * It holds the notification's reply and mark-as-read actions, and binds them to a room so that `Api.replyRoom`,
 * `Bot.send`, `replier.reply` and the like can reach that room afterwards. Use it for a messenger app whose messages
 * StarLight doesn't parse:
 *
 * ```js
 * function onNotificationPosted(sbn, sessionManager) {
 *     if (sbn.getPackageName() == "com.example.chat")
 *         sessionManager.bindSession(String(sbn.getNotification().extras.getString("android.title")));
 * }
 * ```
 *
 * A room bound here has the ID `0`. Every notification gets its own manager. They cannot be created by scripts.
 */
class SessionManager internal constructor(
    packageName: String,
    replyAction: Notification.Action?,
    markAsReadAction: Notification.Action?,
) {

    /** The package of the app that posted the notification. */
    @Volatile
    var packageName: String = packageName

    /**
     * The room bound last by [bindSession]; `null` until a session is bound.
     */
    @Volatile
    var room: String? = null

    /**
     * The notification's reply action: the first action, wearable ones before the notification's own, that replies
     * (with the reply meaning and a remote input, else any remote input, else a title containing "reply" or "답장").
     * `null` when it has none.
     */
    @Volatile
    var replyAction: Notification.Action? = replyAction

    /**
     * The notification's mark-as-read action: the first action, wearable ones first, with the mark-as-read meaning,
     * else the first without remote input whose title contains "read" or "읽음". `null` when it has none.
     */
    @Volatile
    var markAsReadAction: Notification.Action? = markAsReadAction

    /**
     * Makes [room] a room that can be replied to, as the notification's own package, with this notification's reply
     * action.
     *
     * @param room The room's name.
     * @return `false` when the notification has no reply action, `true` once the room is bound.
     */
    fun bindSession(room: String): Boolean =
        bindSession(packageName, room, null)

    /**
     * Makes [room] a room that can be replied to, as the notification's own package, with [action] as its reply
     * action; `null` takes this notification's reply action.
     *
     * @param room The room's name.
     * @param action The action that replies, or `null` for [replyAction].
     * @return `false` when there is no reply action to use, `true` once the room is bound.
     */
    fun bindSession(room: String, action: Notification.Action?): Boolean =
        bindSession(packageName, room, action)

    /**
     * Makes [room] a room of [packageName] that can be replied to, with this notification's reply action.
     *
     * @param packageName The package to bind the room under.
     * @param room The room's name.
     * @return `false` when the notification has no reply action, `true` once the room is bound.
     */
    fun bindSession(packageName: String, room: String): Boolean =
        bindSession(packageName, room, null)

    /**
     * Makes [room] a room of [packageName] that can be replied to, with [action] as its reply action; `null` takes
     * this notification's reply action.
     *
     * The room keeps the mark-as-read action it already has in that package, else this notification's. It has the ID
     * `0`, and from then on a message to it goes through `Api.replyRoom`, `Bot.send` and the like by name. [room]
     * is set to it.
     *
     * @param packageName The package to bind the room under.
     * @param room The room's name.
     * @param action The action that replies, or `null` for [replyAction].
     * @return `false` when there is no reply action to use, `true` once the room is bound.
     */
    fun bindSession(packageName: String, room: String, action: Notification.Action?): Boolean {
        val reply = action ?: replyAction ?: return false
        val key = RoomKey(room, BigInteger.ZERO)
        val read = ChatSessions.registry.session(key, packageName)?.read ?: markAsReadAction
        ChatSessions.bind(packageName, room, reply, read)
        this.room = room
        return true
    }

    /**
     * Gives [room], as a room of the notification's package, this notification's mark-as-read action; the room keeps
     * its reply action.
     *
     * @param room The room's name.
     * @return `false` when the notification has no mark-as-read action, `true` once it is bound.
     */
    fun bindMarkAsRead(room: String): Boolean =
        bindMarkAsRead(room, null)

    /**
     * Gives [room], as a room of the notification's package, [action] as its mark-as-read action; `null` takes this
     * notification's. The room keeps the reply action it has, else this notification's. [room] is not changed.
     *
     * @param room The room's name.
     * @param action The action that marks as read, or `null` for [markAsReadAction].
     * @return `false` when there is no mark-as-read action to use, `true` once it is bound.
     */
    fun bindMarkAsRead(room: String, action: Notification.Action?): Boolean {
        val read = action ?: markAsReadAction ?: return false
        val key = RoomKey(room, BigInteger.ZERO)
        val reply = ChatSessions.registry.session(key, packageName)?.reply ?: replyAction
        ChatSessions.bind(packageName, room, reply, read)
        return true
    }

    internal companion object {

        /** The manager of [sbn]'s notification, with the actions it finds; one that has none if reading them fails. */
        fun of(sbn: StatusBarNotification): SessionManager {
            val actions = try {
                actionsOf(sbn.notification)
            } catch (e: Exception) {
                LOG.debug { "Couldn't read the actions of a notification from ${sbn.packageName}: $e" }
                emptyList()
            }
            val semantic = { action: Notification.Action ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) action.semanticAction else 0
            }
            val takesInput = { action: Notification.Action -> action.remoteInputs?.isNotEmpty() == true }
            val title = { action: Notification.Action -> action.title?.toString() }
            return SessionManager(
                packageName = sbn.packageName,
                replyAction = ActionChoice.reply(actions, semantic, takesInput, title),
                markAsReadAction = ActionChoice.read(actions, semantic, takesInput, title),
            )
        }

        /** The wearable actions of [notification], then its own. */
        private fun actionsOf(notification: Notification): List<Notification.Action> {
            val wearable = try {
                Notification.WearableExtender(notification).actions
            } catch (_: Exception) {
                emptyList()
            }
            return wearable + notification.actions.orEmpty()
        }
    }
}
