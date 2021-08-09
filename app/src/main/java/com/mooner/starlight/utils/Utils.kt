package com.mooner.starlight.utils

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.ui.splash.SplashActivity
import kotlin.system.exitProcess

class Utils {
    companion object {
        fun isForeground(): Boolean {
            val appProcessInfo = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(appProcessInfo)
            return appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
        }

        fun formatStringRes(@StringRes id: Int, map: Map<String, String>): String {
            var string = ApplicationSession.context.getString(id)
            for (pair in map) {
                string = string.replace("\$${pair.key}", pair.value)
            }
            return string
        }

        fun String.trimLength(max: Int): String {
            return if (this.length <= max) this else (this.substring(0, max) + "..")
        }

        fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
            for (permission in permissions) {
                val perm = ContextCompat.checkSelfPermission(context, permission)
                if (perm == PackageManager.PERMISSION_DENIED) return false
            }
            return true
        }

        fun formatTime(millis: Long): String {
            val day = 1000 * 60 * 60 * 24
            val seconds = millis / 1000
            val s = seconds % 60
            val m = (seconds / 60) % 60
            val h = (seconds / (60 * 60)) % 24
            return if (millis >= day) {
                val d = seconds / (60 * 60 * 24)
                String.format("%d일 %d시간 %02d분 %02d초", h, m, s, d)
            } else {
                String.format("%d시간 %02d분 %02d초", h, m, s)
            }
        }

        fun restartApplication(context: Context) {
            val mStartActivity = Intent(context, SplashActivity::class.java)
            val mPendingIntentId = (0..100000).random()
            val mPendingIntent = PendingIntent.getActivity(
                context,
                mPendingIntentId,
                mStartActivity,
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent
            exitProcess(0)
        }
    }
}