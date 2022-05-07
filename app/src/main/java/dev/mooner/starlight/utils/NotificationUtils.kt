package dev.mooner.starlight.utils

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.listener.event.NotificationClickEvent
import dev.mooner.starlight.plugincore.Session

fun Context.createNotificationChannel(channelId: String, channelName: String, channelDescription: String? = null, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel(
            channelId,
            channelName,
            importance
        )
        //notificationChannel.enableLights(false)
        //notificationChannel.enableVibration(false)
        if (channelDescription != null)
            notificationChannel.description = channelDescription

        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            notificationChannel)
    }
}

class NotificationEventService: IntentService(NotificationEventService::class.java.simpleName) {

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        val actionPrefix = NotificationApi.Notification.INTENT_ACTION
        if (action?.startsWith(actionPrefix) == true) {
            val id = action.drop(actionPrefix.length + 1).toInt()

            NotificationClickEvent(id).also(Session.eventManager::fireEventWithContext)
        }
    }
}