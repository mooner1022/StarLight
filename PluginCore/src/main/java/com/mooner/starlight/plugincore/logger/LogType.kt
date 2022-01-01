package com.mooner.starlight.plugincore.logger

import android.util.Log

enum class LogType(
    val priority: Int,
    val indentSize: Int,
) {
    WARN(Log.WARN, 4),
    INFO(Log.INFO, 4),
    ERROR(Log.ERROR, 3),
    CRITICAL(Log.ERROR, 0),
    DEBUG(Log.DEBUG, 3),
    VERBOSE(Log.VERBOSE, 1)
}