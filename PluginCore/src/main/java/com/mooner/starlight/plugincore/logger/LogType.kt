package com.mooner.starlight.plugincore.logger

import android.util.Log

enum class LogType(
    val priority: Int
) {
    WARNING(Log.WARN),
    INFO(Log.INFO),
    ERROR(Log.ERROR),
    CRITICAL(Log.ERROR),
    DEBUG(Log.DEBUG)
}