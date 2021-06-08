package com.mooner.starlight.utils

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.mooner.starlight.core.ApplicationSession

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
            val seconds = millis / 1000
            val s = seconds % 60
            val m = (seconds / 60) % 60
            val h = (seconds / (60 * 60)) % 24
            return String.format("%d시간 %02d분 %02d초", h,m,s)
        }
    }
}