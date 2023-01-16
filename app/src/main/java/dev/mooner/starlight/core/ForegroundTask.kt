/*
 * ForegroundTask.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.core.session.ApplicationSession

class ForegroundTask: Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        @JvmStatic
        var isRunning: Boolean = false
    }

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        /*
        if (!ApplicationSession.isInitComplete) {
            //ApplicationSession.context = applicationContext
            ApplicationSession.init(applicationContext)
        }
         */

        createNotificationChannel()
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0 or PendingIntent.FLAG_IMMUTABLE)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("StarLight 실행중")
                .setSubText("당신만을 위한 봇들을 관리중이에요 :)")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentIntent(pendingIntent)
                .setShowWhen(false)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            val notification = NotificationCompat.Builder(this)
                .setContentTitle("StarLight 실행중")
                .setSubText("당신만을 위한 봇들을 관리중이에요 :)")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setShowWhen(false)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        ApplicationSession.shutdown()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "포그라운드 채널",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            //notificationChannel.enableLights(false)
            //notificationChannel.enableVibration(false)
            //notificationChannel.description = "StarLight 의 서비스를 실행하기 위한 알림이에요."

            val notificationManager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                notificationChannel)
        }
    }
}