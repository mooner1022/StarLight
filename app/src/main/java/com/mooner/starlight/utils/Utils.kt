package com.mooner.starlight.utils

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import androidx.annotation.StringRes
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
    }
}