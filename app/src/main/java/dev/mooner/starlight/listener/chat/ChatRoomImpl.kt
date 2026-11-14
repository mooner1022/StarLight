package dev.mooner.starlight.listener.chat

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.logger.LoggerFactory

private val LOG = LoggerFactory.logger {  }

data class ChatRoomImpl(
    override val id: String,
    override val name: String,
    override val isGroupChat: Boolean,
    override val isDebugRoom: Boolean = false,
    val sendSession: Notification.Action,
    val readSession: Notification.Action,
    internal val context: Context,
): ChatRoom {

    private var lastReceivedId: Long = 0

    /**
     * The Android user the room's notification belongs to: `0` for the device's owner, another for a dual messenger,
     * secure folder or work profile copy of the app. Set once the notification is parsed.
     */
    @Volatile
    var userId: Int = 0

    /** Whether the room's notification came from another Android user than the owner, as API2's `isMultiChat` says. */
    val isMultiChat: Boolean
        get() = userId != 0

    fun setLastReceivedId(id: Long) {
        lastReceivedId = id
    }

    override fun send(message: String): Boolean {
        return try {
            SessionSend.text(context, sendSession, message)
            LOG.verbose { "send() success: $message" }
            true
        } catch (e: PendingIntent.CanceledException) {
            e.printStackTrace()
            false
        }
    }

    override fun markAsRead(): Boolean {
        try {
            SessionSend.read(context, readSession, id)
        } catch (_: PendingIntent.CanceledException) {
            return false
        }
        return true
    }
}

/** Whether [this] room's notification came from another Android user than the owner; `false` for any other room. */
fun ChatRoom.isMultiChat(): Boolean =
    (this as? ChatRoomImpl)?.isMultiChat == true
