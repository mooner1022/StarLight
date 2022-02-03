/*
 * NotificationListener.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

import android.app.Notification
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.widget.Toast
import androidx.core.app.NotificationCompat
import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.LegacyEvent
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.ChatRoomImpl
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.fireEvent
import dev.mooner.starlight.utils.PACKAGE_KAKAO_TALK
import java.util.*

class NotificationListener: NotificationListenerService() {

    companion object {
        private val chatRooms: MutableMap<String, ChatRoom> = WeakHashMap()
        private var lastReceivedRoom: ChatRoom? = null

        fun hasRoom(roomName: String): Boolean = roomName in chatRooms

        fun send(message: String): Boolean = lastReceivedRoom?.send(message)?: false
        fun sendTo(roomName: String, message: String): Boolean = chatRooms[roomName]?.send(message)?: false

        fun markAsRead(): Boolean = lastReceivedRoom?.markAsRead()?: false
        fun markAsRead(roomName: String): Boolean = chatRooms[roomName]?.markAsRead()?: false
    }

    private val isGlobalPowerOn: Boolean
        get() = Session.globalConfig.getCategory("general").getBoolean("global_power", true)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val wearableExtender = Notification.WearableExtender(sbn.notification)
        for (act in wearableExtender.actions) {
            if (act.remoteInputs != null && act.remoteInputs.isNotEmpty()) {
                if (!isGlobalPowerOn || sbn.packageName != PACKAGE_KAKAO_TALK) {
                    return
                }
                try {
                    val extras = sbn.notification.extras
                    val message = extras[NotificationCompat.EXTRA_TEXT].toString()
                    val sender = extras[NotificationCompat.EXTRA_TITLE].toString()
                    val room = (extras[NotificationCompat.EXTRA_SUMMARY_TEXT] ?: extras[NotificationCompat.EXTRA_TITLE]).toString()
                    //val base64 = notification.getLargeIcon().loadDrawable(applicationContext).toBase64()
                    val profileBitmap = (sbn.notification.getLargeIcon().loadDrawable(applicationContext) as BitmapDrawable).bitmap
                    val isGroupChat = extras.containsKey(NotificationCompat.EXTRA_SUMMARY_TEXT)
                    val hasMention = extras[NotificationCompat.EXTRA_TEXT] is SpannableString
                    val chatLogId = extras.getLong("chatLogId")

                    if (room !in chatRooms) {
                        chatRooms[room] = ChatRoomImpl(
                            name = room,
                            isGroupChat = isGroupChat,
                            session = act,
                            context = this
                        )
                    }

                    val data = Message(
                        message = message,
                        sender = ChatSender(
                            name = sender,
                            profileBitmap = profileBitmap
                        ),
                        room = chatRooms[room]!!,
                        packageName = sbn.packageName,
                        hasMention = hasMention,
                        chatLogId = chatLogId
                    )

                    Logger.v("NotificationListenerService", "message : $message sender : $sender room : $room session : $act")

                    Session.projectManager.fireEvent<DefaultEvent>(data) { e ->
                        e.printStackTrace()
                        Logger.e("NotificationListener", e)
                    }

                    if (Session.globalConfig.getCategory("legacy").getBoolean("use_legacy_event", false)) {
                        val replier = Replier { roomName, msg, hideToast ->
                            val chatRoom = if (roomName == null) lastReceivedRoom else chatRooms[roomName]
                            if (chatRoom == null) {
                                if (!hideToast)
                                    Toast.makeText(applicationContext, "메세지가 수신되지 않은 방 '$roomName' 에 메세지를 보낼 수 없습니다.", Toast.LENGTH_LONG).show()
                                false
                            } else {
                                chatRoom.send(msg)
                            }
                        }

                        val imageDB = ImageDB(profileBitmap)

                        Session.projectManager.fireEvent<LegacyEvent>(room, message, sender, isGroupChat, replier, imageDB) { e ->
                            e.printStackTrace()
                            Logger.e("NotificationListener", e)
                        }
                    }
                    //stopSelf()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Logger.e("NotificationListener", e)
                }
            }
        }
    }
}