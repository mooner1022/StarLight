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
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.mooner.starlight.MainActivity
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.splash.SplashActivity

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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StarLight 실행중")
            .setSubText("당신만을 위한 봇들을 관리중이에요 :)")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.main_bright))
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .apply { if (GlobalApplication.isSafeStart) safeStartContent() }
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else
            0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)

        isRunning = true
    }

    /** Says that the bots are off for this start, and how to get them back. */
    private fun NotificationCompat.Builder.safeStartContent() {
        val restart = PendingIntent.getActivity(this@ForegroundTask, 1,
            Intent(this@ForegroundTask, SplashActivity::class.java)
                .putExtra(SplashActivity.EXTRA_RESTART, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        setContentTitle(getString(R.string.safe_start_title))
        setSubText(null)
        setContentText(getString(R.string.safe_start_text))
        setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.safe_start_text)))
        addAction(0, getString(R.string.safe_start_restart), restart)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        ApplicationSession.shutdown()
    }

    private fun createNotificationChannel() {
        val notificationChannel = NotificationChannel(
            CHANNEL_ID,
            "포그라운드 채널",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        //notificationChannel.enableLights(false)
        //notificationChannel.enableVibration(false)
        //notificationChannel.description = "StarLight 의 서비스를 실행하기 위한 알림이에요."

        val notificationManager = applicationContext.getSystemService(
            NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            notificationChannel)
    }
}