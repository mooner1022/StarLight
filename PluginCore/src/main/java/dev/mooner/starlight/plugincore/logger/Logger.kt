/*
 * Logger.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

import android.util.Log
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.utils.TimeUtils
import dev.mooner.starlight.plugincore.utils.currentThread
import kotlinx.coroutines.*
import java.io.File

/**
 * Internal logger for Project StarLight
 * @author mooner1022
 */

object Logger {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logWriteScope: CoroutineScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1)) + SupervisorJob()

    private lateinit var logFile: File

    private var mLogs: MutableList<LogData> = mutableListOf()
    val logs: List<LogData> get() =
        if (showInternalLogs) mLogs else mLogs.filterNot { it.type == LogType.VERBOSE }

    private val showInternalLogs get() =
        Session.isInitComplete &&
                Session.globalConfig
                    .category("dev_mode_config")
                    .getBoolean("show_internal_log", false)
    private val writeInternalLogs get() =
        Session.isInitComplete &&
                Session.globalConfig
                    .category("dev_mode_config")
                    .getBoolean("write_internal_log", false)

    fun init(baseDir: File) {
        val dirName = TimeUtils.formatCurrentDate("yyyy-MM-dd")
        val dir = File(baseDir, "logs").resolve(dirName)
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }

        val logFiles = dir.listFiles()?.filter { it.nameWithoutExtension.toIntOrNull() != null }
        val fileIndex = (logFiles?.maxOfOrNull { it.nameWithoutExtension.toInt() } ?: -1) + 1
        val fileName = "$fileIndex.log"
        logFile = File(dir, fileName)
    }

    private val callSite get() = currentThread.stackTrace[4].className.split(".").last()

    fun v(message: String) = v(tag = callSite, message = message)

    fun v(tag: String?, message: String) = log(
        LogData(
            type = LogType.VERBOSE,
            tag = tag,
            message = message
        )
    )

    fun d(message: String) = d(tag = callSite, message = message)

    fun d(tag: String?, message: String) = log(
        LogData(
            type = LogType.DEBUG,
            tag = tag,
            message = message
        )
    )

    fun i(message: String) = i(tag = callSite, message = message)

    fun i(tag: String?, message: String) = log(
        LogData(
            type = LogType.INFO,
            tag = tag,
            message = message
        )
    )

    fun e(tag: String, throwable: Throwable) = e(tag, throwable.message?: "null")

    fun e(throwable: Throwable) = e(throwable.message?: "null")

    fun e(message: String) = e(tag = callSite, message = message)

    fun e(tag: String?, message: String) = log(
        LogData(
            type = LogType.ERROR,
            tag = tag,
            message = message
        )
    )

    // What a Terrible Failure!
    fun wtf(message: String) = wtf(tag = callSite, message = message)

    fun wtf(tag: String?, message: String) = log(
        LogData(
            type = LogType.CRITICAL,
            tag = tag,
            message = message
        )
    )

    fun w(message: String) = w(tag = callSite, message = message)

    fun w(tag: String?, message: String) = log(
        LogData(
            type = LogType.WARN,
            tag = tag,
            message = message
        )
    )

    fun log(data: LogData) {
        Log.println(data.type.priority, data.tag?: data.type.name, data.message)

        logWriteScope.launch {
            mLogs += data

            if (data.type != LogType.VERBOSE || showInternalLogs)
                EventHandler.fireEventWithScope(Events.Log.LogCreateEvent(data), this)

            if ((data.type.priority >= LogType.DEBUG.priority || writeInternalLogs) && this@Logger::logFile.isInitialized)
                logFile.appendText("$data\n")
        }
    }

    fun filter(type: LogType): List<LogData> = logs.filter { it.type == type }

    fun filterNot(type: LogType): List<LogData> = logs.filterNot { it.type == type }
}