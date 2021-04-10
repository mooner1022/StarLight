package com.mooner.starlight.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.mooner.starlight.MainActivity
import com.mooner.starlight.core.ApplicationSession.pluginLoader

class BackgroundTask: Service() {
    companion object {
        private const val NOTIFICATION_ID = 10
        private const val CHANNEL_ID = "foreground_notification_channel"
        var isRunning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        val style: NotificationCompat.BigTextStyle = NotificationCompat.BigTextStyle()
        style.bigText("당신만을 위한 봇들을 관리중이에요 :)")
        style.setSummaryText("StarLight 실행중")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(style)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            val notification = NotificationCompat.Builder(this)
                .setStyle(style)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationChannel = NotificationChannel(
            CHANNEL_ID,
            "포그라운드 채널",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.enableLights(false)
        notificationChannel.enableVibration(false)
        notificationChannel.description = "StarLight 의 서비스를 실행하기 위한 알림이에요."

        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            notificationChannel)
    }
}