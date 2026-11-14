/*
 * ChatSessions.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.chat

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import java.math.BigInteger

private val LOG = LoggerFactory.logger {  }

/** Sends through a notification's actions, as a reply typed into the notification would. */
internal object SessionSend {

    /**
     * Hands [text] to [action]'s remote inputs and fires it.
     *
     * @throws PendingIntent.CanceledException when the messenger no longer takes it.
     * @throws IllegalStateException when [action] has no remote input.
     */
    fun text(context: Context, action: Notification.Action, text: String) {
        val inputs = action.remoteInputs
        if (inputs.isNullOrEmpty())
            throw IllegalStateException("The action takes no reply")
        val intent = Intent()
        val results = Bundle()
        for (input in inputs)
            results.putCharSequence(input.resultKey, text)
        NotificationListener.notifySent()
        RemoteInput.addResultsToIntent(inputs, intent, results)
        action.actionIntent.send(context, 0, intent)
    }

    /**
     * Fires [action] as a "mark as read" for the room [roomId] (`null` when it isn't known).
     *
     * KakaoTalk then takes the room's notification away, which [OwnReads] keeps from counting as a deletion.
     *
     * @throws PendingIntent.CanceledException when the messenger no longer takes it.
     */
    fun read(context: Context, action: Notification.Action, roomId: String?) {
        OwnReads.shared.note(roomId, OwnReads.READ_MILLIS)
        action.actionIntent.send(context, 1, null)
    }
}

/**
 * A room made from two notification actions a script handed over, which has no message behind it.
 *
 * Its ID is `"0"`.
 */
class SessionChatRoom(
    override val name: String,
    /** The package whose notification the actions belong to. */
    val packageName: String,
    private val replyAction: Notification.Action?,
    private val readAction: Notification.Action?,
): ChatRoom {

    override val id: String = "0"

    override val isGroupChat: Boolean = false

    override val isDebugRoom: Boolean = false

    override fun send(message: String): Boolean {
        val action = replyAction ?: return false
        return try {
            SessionSend.text(GlobalApplication.requireContext(), action, message)
            true
        } catch (e: PendingIntent.CanceledException) {
            LOG.debug { "send() on a bound session failed: $e" }
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    override fun markAsRead(): Boolean {
        val action = readAction ?: return false
        return try {
            SessionSend.read(GlobalApplication.requireContext(), action, null)
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }
}

/**
 * The rooms StarLight can reply to, of every notification rule and package, and what is done through them.
 *
 * Messages register their rooms here ([register]); `SessionManager.bindSession` registers rooms of its own ([bind]).
 * Rooms are told apart by [RoomKey]: name plus ID. Scripts name a room in several ways; [RoomResolver] turns what they
 * pass into a key. The `reply`, `markAsRead` and `sendMarkdown` members take a key; the ones named `replyTo`,
 * `markRoomAsRead`, `markdownTo` and `canReply` take what a script passes and resolve it first.
 *
 * Results are MessengerBot's: replying says whether a session exists, not whether the messenger took the text.
 */
object ChatSessions {

    /** The registry itself. */
    val registry: SessionRegistry = SessionRegistry()

    /**
     * Registers [room], which a message of [packageName] came from, as that package's session of the room, replacing
     * its earlier one.
     */
    fun register(packageName: String, room: ChatRoomImpl) {
        registry.put(sessionOf(packageName, room))
    }

    /** As [register], unless the package already has the room. Returns whether it was registered. */
    fun registerIfAbsent(packageName: String, room: ChatRoomImpl): Boolean =
        registry.putIfAbsent(sessionOf(packageName, room))

    private fun sessionOf(packageName: String, room: ChatRoomImpl) =
        RoomSession(packageName, RoomKey.of(room), room, room.sendSession, room.readSession)

    /**
     * Registers the room named [room], with ID zero, for [packageName] with [reply] and [read], as
     * `SessionManager.bindSession` does.
     */
    fun bind(packageName: String, room: String, reply: Notification.Action?, read: Notification.Action?) {
        val key = RoomKey(room, BigInteger.ZERO)
        registry.put(RoomSession(packageName, key, SessionChatRoom(room, packageName, reply, read), reply, read))
    }

    /** The package whose session of [key] is the latest, or `null` when it has none. */
    fun packageOf(key: RoomKey): String? =
        registry.packageOf(key)

    /** Whether [key] has a reply session, in [packageName] or, when it is `null`, in any package. */
    fun hasSession(key: RoomKey, packageName: String? = null): Boolean =
        registry.hasSession(key, packageName)

    /**
     * Sends [text] to [key] through its reply action: [packageName]'s, or the latest session's when it is `null`.
     *
     * @param cancelledIsSent What a reply action the messenger cancelled (a dismissed notification) gives: `true` for
     *   the legacy API, which counts the session as found, `false` for API2, where it means the session has expired.
     * @return `true` when the room has a session with a reply action, whether or not the messenger took the text;
     *   `false` when it has none, when sending threw, or when the action was cancelled and [cancelledIsSent] is
     *   `false`.
     */
    fun reply(key: RoomKey, text: String, packageName: String? = null, cancelledIsSent: Boolean = true): Boolean {
        val action = registry.session(key, packageName)?.reply ?: return false
        return try {
            SessionSend.text(GlobalApplication.requireContext(), action, text)
            true
        } catch (e: PendingIntent.CanceledException) {
            LOG.debug { "The reply action of $key was cancelled: $e" }
            cancelledIsSent
        } catch (e: Exception) {
            LOG.debug { "Sending to $key failed: $e" }
            false
        }
    }

    /**
     * Marks [key] as read through [packageName]'s session, or through every package's session when it is `null`.
     *
     * @return `true` when at least one read action was sent; `false` when there is none. Without a package, a session
     *   with a read action counts as found even when the messenger cancelled it; with a package, a cancelled action
     *   gives `false`.
     */
    fun markAsRead(key: RoomKey, packageName: String? = null): Boolean {
        val sessions = if (packageName == null)
            registry.sessions(key)
        else
            listOfNotNull(registry.session(key, packageName))
        var sent = false
        for (session in sessions) {
            val action = session.read ?: continue
            if (packageName == null)
                sent = true
            try {
                SessionSend.read(GlobalApplication.requireContext(), action, key.id.takeIf { it.signum() != 0 }?.toString())
                sent = true
            } catch (_: PendingIntent.CanceledException) {
                // Another package's session may still take it.
            }
        }
        return sent
    }

    /**
     * Sends [text] to [key] as a Markdown message, to [packageName] or, when it is `null`, the package of the room's
     * latest session.
     *
     * @return Whether the share was launched; `false` when no package is known or nothing can take it.
     */
    fun sendMarkdown(key: RoomKey, text: String, packageName: String? = null): Boolean {
        val target = packageName ?: registry.packageOf(key) ?: return false
        val userId = (registry.chatRoom(key) as? ChatRoomImpl)?.userId
        return MarkdownSender.send(target, key.id, text, userId)
    }

    /** Whether the room [room] stands for, resolved as [RoomResolver] does, has a reply session. */
    fun canReply(room: Any?, packageName: String? = null): Boolean =
        RoomResolver.resolve(room, packageName)?.let { hasSession(it, packageName) } == true

    /** [reply] to the room [room] stands for; `false` when it resolves to no room. */
    fun replyTo(room: Any?, text: String, packageName: String? = null): Boolean =
        RoomResolver.resolve(room, packageName)?.let { reply(it, text, packageName) } == true

    /** [markAsRead] of the room [room] stands for; `false` when it resolves to no room. */
    fun markRoomAsRead(room: Any?, packageName: String? = null): Boolean =
        RoomResolver.resolve(room, packageName)?.let { markAsRead(it, packageName) } == true

    /** [sendMarkdown] to the room [room] stands for; `false` when it resolves to no room. */
    fun markdownTo(room: Any?, text: String, packageName: String? = null): Boolean =
        RoomResolver.resolve(room, packageName)?.let { sendMarkdown(it, text, packageName) } == true
}
