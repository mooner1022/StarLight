package com.mooner.starlight.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.core.Info
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.utils.FileUtils
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.system.exitProcess

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

        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            val pInfo: PackageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                pInfo.longVersionCode
            else
                pInfo.versionCode

            val errMsg = """
                *** 치명적인 오류가 발생했습니다. 앱을 종료하는 중... ***
                [버그 제보시 아래 메세지를 첨부해주세요.]
                ──────────
                StarLight v${pInfo.versionName}(build ${versionCode})
                PluginCore v${Info.PLUGINCORE_VERSION}
                Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}
                Build.DEVICE: ${Build.DEVICE}
                thread  : ${paramThread.name}
                message : ${paramThrowable.localizedMessage}
                cause   : ${paramThrowable.cause}
                ┉┉┉┉┉┉┉┉┉┉
                stackTrace:
            """.trimIndent() + paramThrowable.stackTraceToString() + "\n──────────"
            Logger.wtf("StarLight-core", errMsg)

            val startupData = Session.json.encodeToString(mapOf("last_error" to errMsg))
            File(FileUtils.getInternalDirectory(), "STARTUP.info").writeText(startupData)
            stopForeground(true)
            ApplicationSession.shutdown()
            stopSelf()
            exitProcess(2)
        }

        /*
        if (!ApplicationSession.isInitComplete) {
            //ApplicationSession.context = applicationContext
            ApplicationSession.init(applicationContext)
        }
         */

        createNotificationChannel()
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("StarLight 실행중")
                .setSubText("당신만을 위한 봇들을 관리중이에요 :)")
                .setSmallIcon(R.mipmap.ic_logo)
                .setContentIntent(pendingIntent)
                .setShowWhen(false)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            val notification = NotificationCompat.Builder(this)
                .setContentTitle("StarLight 실행중")
                .setSubText("당신만을 위한 봇들을 관리중이에요 :)")
                .setSmallIcon(R.mipmap.ic_logo)
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