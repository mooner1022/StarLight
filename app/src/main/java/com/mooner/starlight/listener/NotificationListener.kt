package com.mooner.starlight.listener

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.core.ApplicationSession.taskHandler
import java.io.ByteArrayOutputStream
import java.util.*

class NotificationListener: NotificationListenerService() {
    private val sessions: HashMap<String, Notification.Action> = hashMapOf()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (sbn.packageName == "com.kakao.talk") {
            val wearableExtender = Notification.WearableExtender(sbn.notification)
            for (act in wearableExtender.actions) {
                if (act.remoteInputs!=null && act.remoteInputs.isNotEmpty()) {
                    val notification = sbn.notification
                    val message = notification.extras["android.text"].toString()
                    val sender = notification.extras.getString("android.title").toString()
                    val room = act.title.toString().replaceFirst("답장 (", "").replace(")", "")
                    val imageHash = encodeIcon(
                            notification.getLargeIcon().loadDrawable(
                                    applicationContext
                            )
                    )
                    if (!sessions.containsKey(room)) {
                        sessions[room] = act
                    }

                    Log.i("StarLight-NotificationListener", "message : $message sender : $sender nRoom : $room nSession : $act")

                    taskHandler.fireEvent("default","response", arrayOf(room, message, sender, imageHash))
                }
            }
        }
    }

    init {
        ApplicationSession.onInitComplete {
            taskHandler.bindRepliers { room, msg ->
                if (!sessions.containsKey(room)) {
                    Log.w("NotificationListener", "No session for room $room found")
                    return@bindRepliers
                }
                send(msg, sessions[room]!!)
            }
        }
    }

    private fun send(message: String, session: Notification.Action) {
        val sendIntent = Intent()
        val msg = Bundle()
        for (input in session.remoteInputs) msg.putCharSequence(
                input.resultKey,
                message
        )
        RemoteInput.addResultsToIntent(session.remoteInputs, sendIntent, msg)
        try {
            session.actionIntent.send(applicationContext, 0, sendIntent)
            Log.i("send() complete", message)
        } catch (e: PendingIntent.CanceledException) {
            e.printStackTrace()
        }
    }

    private fun encodeIcon(icon: Drawable?): Long {
        if (icon != null) {
            val bitDw = icon as BitmapDrawable
            val bitmap = bitDw.bitmap
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            var bitmapByte = stream.toByteArray()
            bitmapByte = Base64.encode(bitmapByte, Base64.DEFAULT)
            return Arrays.hashCode(bitmapByte).toLong()
        }
        return 0
    }
}