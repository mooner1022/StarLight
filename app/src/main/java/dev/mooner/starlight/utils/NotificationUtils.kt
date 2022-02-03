package dev.mooner.starlight.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

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