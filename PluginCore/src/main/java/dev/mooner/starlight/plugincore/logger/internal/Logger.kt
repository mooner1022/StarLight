/*
 * Logger.kt created by Minki Moon(mooner1022) on 22. 12. 25. 오후 3:26
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger.internal

import android.util.Log
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.Flags
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LogType
import dev.mooner.starlight.plugincore.utils.currentThread
import kotlinx.coroutines.*

/**
 * Internal logger for Project StarLight
 * @author mooner1022
 */

internal object Logger {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logPublishScope: CoroutineScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1)) + SupervisorJob()

    private val showInternalLogs get() =
        Session.isInitComplete &&
                GlobalConfig
                    .category("dev_mode_config")
                    .getBoolean("show_internal_log", false)

    private val callSite get() =
        currentThread.stackTrace[4].className.split(".").last()

    @Deprecated("Deprecated, use Logger.v(tag: String, message: String).")
    fun v(message: String) =
        v(tag = callSite, message = message)

    fun v(tag: String?, message: String) =
        log(LogType.VERBOSE, tag, message)

    @Deprecated("Deprecated, use Logger.d(tag: String, message: String).")
    fun d(message: String) =
        d(tag = callSite, message = message)

    fun d(tag: String?, message: String) =
        log(LogType.DEBUG, tag, message)

    @Deprecated("Deprecated, use Logger.i(tag: String, message: String).")
    fun i(message: String) =
        i(tag = callSite, message = message)

    fun i(tag: String?, message: String) =
        i(tag, message, 0)

    fun i(tag: String?, message: String, flags: Flags) =
        log(LogType.INFO, tag, message, flags)

    @Deprecated("Deprecated, use Logger.w(tag: String, message: String).")
    fun w(message: String) =
        w(tag = callSite, message = message)

    fun w(tag: String?, message: String) =
        log(LogType.WARN, tag, message)

    @Deprecated("Deprecated, use Logger.e(tag: String, message: String).")
    fun e(message: String) =
        e(tag = callSite, message = message)

    fun e(tag: String?, message: String) =
        log(LogType.ERROR, tag, message)

    fun e(tag: String, throwable: Throwable) =
        e(tag, """
            $throwable
            cause:   ${throwable.cause}
            message: ${throwable.message}
        """.trimIndent())

    fun e(throwable: Throwable) =
        e(callSite, throwable)

    // What a Terrible Failure!
    @Deprecated("Deprecated, use Logger.wtf(tag: String, message: String).")
    fun wtf(message: String) =
        wtf(tag = callSite, message = message)

    fun wtf(tag: String?, message: String) =
        log(LogType.CRITICAL, tag, message)


    fun log(type: LogType, tag: String?, message: String, flags: Flags = 0) {
        val data = LogData(
            type = type,
            tag = tag ?: type.name,
            message = message,
            flags = flags
        )
        Log.println(data.type.priority, data.tag?: data.type.name, data.message)

        if (data.type != LogType.VERBOSE || showInternalLogs) {
            logPublishScope.launch {
                try {
                    EventHandler.fireEventWithScope(Events.Log.Create(data), this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}