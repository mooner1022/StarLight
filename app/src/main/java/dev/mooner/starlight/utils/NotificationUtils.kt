package dev.mooner.starlight.utils

import android.Manifest
import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import dev.mooner.starlight.api.original.NotificationApi
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events

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

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        val actionPrefix = NotificationApi.Notification.INTENT_ACTION
        if (action?.startsWith(actionPrefix) == true) {
            val id = action.drop(actionPrefix.length + 1).toInt()

            Events.Notification.Click(id).also(EventHandler::fireEventWithScope)
        }
    }
}

fun Context.canPostNotification(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        return true

    return ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}