package com.mooner.starlight.listener

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.util.Base64
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.projectManager
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.models.ChatRoom
import com.mooner.starlight.plugincore.models.ChatSender
import com.mooner.starlight.plugincore.models.Message
import java.io.ByteArrayOutputStream
import java.util.*

class NotificationListener: NotificationListenerService() {

    private val sessions: MutableMap<String, Notification.Action> = WeakHashMap()
    private val isGlobalPowerOn: Boolean
        get() = Session.generalConfig["global_power", "true"].toBoolean()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val wearableExtender = Notification.WearableExtender(sbn.notification)
        for (act in wearableExtender.actions) {
            if (act.remoteInputs != null && act.remoteInputs.isNotEmpty()) {
                if (!isGlobalPowerOn) {
                    return
                }
                val notification = sbn.notification
                val message = notification.extras["android.text"].toString()
                val sender = notification.extras.getString("android.title").toString()
                val room = act.title.toString().replaceFirst("답장 (", "").replaceAfterLast(")", "")
                val base64 = notification.getLargeIcon().loadDrawable(applicationContext).toBase64()
                val isGroupChat = notification.extras["android.text"] is SpannableString
                val hasMention = notification.extras["android.text"] is SpannableString
                if (!sessions.containsKey(room)) {
                    sessions[room] = act
                }

                val projects = projectManager.getEnabledProjects()//.filter { packageName in it.config.packages }
                if (projects.isEmpty()) return

                val data = Message(
                    message = message,
                    sender = ChatSender(
                        name = sender,
                        profileBase64 = base64,
                        profileHash = base64.hashCode()
                    ),
                    room = ChatRoom(
                        name = room,
                        isGroupChat = isGroupChat,
                        session = act,
                        context = applicationContext
                    ),
                    packageName = sbn.packageName,
                    hasMention = hasMention
                )

                Logger.d("NotificationListenerService", "message : $message sender : $sender nRoom : $room nSession : $act")

                for (project in projects) {
                    project.callEvent("onMessage", arrayOf(data))
                }
                stopSelf()
            }
        }
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