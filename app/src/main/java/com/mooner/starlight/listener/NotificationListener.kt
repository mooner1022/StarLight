package com.mooner.starlight.listener

import android.app.Notification
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.widget.Toast
import com.mooner.starlight.listener.legacy.ImageDB
import com.mooner.starlight.listener.legacy.LegacyEvent
import com.mooner.starlight.listener.legacy.Replier
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.chat.ChatRoom
import com.mooner.starlight.plugincore.chat.ChatRoomImpl
import com.mooner.starlight.plugincore.chat.ChatSender
import com.mooner.starlight.plugincore.chat.Message
import com.mooner.starlight.plugincore.event.callEvent
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.utils.PACKAGE_KAKAO_TALK
import java.util.*

class NotificationListener: NotificationListenerService() {

    private val sessions: MutableMap<String, Notification.Action> = WeakHashMap()
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
                    val notification = sbn.notification
                    val message = notification.extras["android.text"].toString()
                    val sender = notification.extras.getString("android.title").toString()
                    val room = act.title.toString().replaceFirst("답장 (", "").dropLast(1)
                    val base64 = notification.getLargeIcon().loadDrawable(applicationContext).toBase64()
                    val isGroupChat = notification.extras["android.text"] is SpannableString
                    val hasMention = notification.extras["android.text"] is SpannableString
                    if (!sessions.containsKey(room)) {
                        sessions[room] = act
                    }

                    val data = Message(
                        message = message,
                        sender = ChatSender(
                            name = sender,
                            profileBase64 = base64,
                            profileHash = base64.hashCode()
                        ),
                        room = ChatRoomImpl(
                            name = room,
                            isGroupChat = isGroupChat,
                            session = act,
                            context = applicationContext
                        ),
                        packageName = sbn.packageName,
                        hasMention = hasMention
                    )

                    Logger.v("NotificationListenerService", "message : $message sender : $sender room : $room session : $act")

                    Session.eventManager.callEvent<DefaultEvent>(arrayOf(data)) { e ->
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

                        Session.eventManager.callEvent<LegacyEvent>(arrayOf(room, message, sender, isGroupChat, replier, imageDB)) { e ->
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

    override fun onDestroy() {
        super.onDestroy()
        Logger.v("NotificationListener", "onDestroy() called")
        sessions.clear()
    }

    private fun Drawable.toBase64(): String {
        val bitDw = this as BitmapDrawable
        val bitmap = bitDw.bitmap
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bitmapByte = stream.toByteArray()
        return Base64.encodeToString(bitmapByte, Base64.DEFAULT)
    }
}