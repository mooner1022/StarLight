package dev.mooner.starlight.plugincore.utils

import java.text.SimpleDateFormat
import java.util.*

class TimeUtils {
    companion object {
        fun formatCurrentDate(pattern: String): String {
            val format = SimpleDateFormat(pattern, Locale.getDefault())
            return format.format(System.currentTimeMillis())
        }

        fun getTimestamp(fullTimestamp: Boolean = false): String {
            val format = if (fullTimestamp)
                SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
            else
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return format.format(System.currentTimeMillis())
        }

        fun formatMillis(millis: Long, pattern: String): String {
            val format = SimpleDateFormat(pattern, Locale.getDefault())
            return format.format(millis)
        }
    }
}