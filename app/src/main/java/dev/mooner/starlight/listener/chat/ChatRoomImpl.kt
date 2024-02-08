package dev.mooner.starlight.listener.chat

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.mooner.starlight.listener.NotificationListener
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
    private val context: Context
): ChatRoom {

    private var lastReceivedId: Long = 0

    fun setLastReceivedId(id: Long) {
        lastReceivedId = id
    }

    override fun send(message: String): Boolean {
        return try {
            val sendIntent = Intent()
            val msg = Bundle()
            for (input in sendSession.remoteInputs) msg.putCharSequence(
                input.resultKey,
                message
            )
            NotificationListener.notifySent()
            RemoteInput.addResultsToIntent(sendSession.remoteInputs, sendIntent, msg)
            sendSession.actionIntent.send(context, 0, sendIntent)
            LOG.verbose { "send() success: $message" }
            true
        } catch (e: PendingIntent.CanceledException) {
            e.printStackTrace()
            false
        }
    }

    override fun markAsRead(): Boolean {
        try {
            readSession.actionIntent.send(context, 1, null)
        } catch (e: PendingIntent.CanceledException) {
            return false
        }
        return true
    }
}