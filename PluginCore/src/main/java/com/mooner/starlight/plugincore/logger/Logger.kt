package com.mooner.starlight.plugincore.logger

import android.util.Log
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

/**
 * Internal logger for Project StarLight
 * @author mooner1022
 */

object Logger {

    private val listeners: MutableMap<String, (log: LogData) -> Unit> = WeakHashMap()
    private lateinit var logFile: File

    private var mLogs: MutableList<LogData> = mutableListOf()
    val logs: List<LogData> get() = if (showInternalLogs) mLogs else mLogs.filterNot { it.type == LogType.VERBOSE }

    private val showInternalLogs get() = Session.isInitComplete && Session.globalConfig.getCategory("dev_mode_config").getBoolean("show_internal_log", false)

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

    /**
     * Binds a listener, which is called when a log is created, to the logger
     *
     * @param key an id for the listener, used to remove or release the listener
     * @param listener called when a log is created
     */
    fun bindListener(key: String, listener: (log: LogData) -> Unit) {
        listeners[key] = listener
    }

    /**
     * Unbinds a listener which is registered by key
     *
     * @param key an id for the listener, set when binding the listener
     */
    fun unbindListener(key: String) {
        if (listeners.containsKey(key)) {
            listeners.remove(key)
        }
    }

    fun v(message: String) = v(tag = null, message = message)

    fun v(tag: String?, message: String) = log(
        LogData(
            type = LogType.VERBOSE,
            tag = tag,
            message = message
        )
    )

    fun d(message: String) = d(tag = null, message = message)

    fun d(tag: String?, message: String) = log(
        LogData(
            type = LogType.DEBUG,
            tag = tag,
            message = message
        )
    )

    fun i(message: String) = i(tag = null, message = message)

    fun i(tag: String?, message: String) = log(
        LogData(
            type = LogType.INFO,
            tag = tag,
            message = message
        )
    )

    fun e(tag: String, throwable: Throwable) = e(tag, throwable.message?: "null")

    fun e(message: String) = e(tag = null, message = message)

    fun e(tag: String?, message: String) = log(
        LogData(
            type = LogType.ERROR,
            tag = tag,
            message = message
        )
    )

    // What a Terrible Failure!
    fun wtf(message: String) = wtf(tag = null, message = message)

    fun wtf(tag: String?, message: String) = log(
        LogData(
            type = LogType.CRITICAL,
            tag = tag,
            message = message
        )
    )

    fun w(message: String) = w(tag = null, message = message)

    fun w(tag: String?, message: String) = log(
        LogData(
            type = LogType.WARNING,
            tag = tag,
            message = message
        )
    )

    fun log(data: LogData) {
        Log.println(data.type.priority, data.tag?: data.type.name, data.message)
        synchronized(mLogs) {
            mLogs += data
        }

        if (data.type != LogType.VERBOSE || showInternalLogs)
            for ((_, listener) in listeners) {
                listener(data)
            }

        if (data.type.priority >= LogType.DEBUG.priority && this::logFile.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                val timestamp = TimeUtils.getTimestamp(fullTimestamp = true)
                val log = data.toString()
                synchronized(logFile) {
                    logFile.appendText("[$timestamp]$log\n")
                }
            }
        }
    }

    fun filter(type: LogType): List<LogData> = logs.filter { it.type == type }

    fun filterNot(type: LogType): List<LogData> = logs.filterNot { it.type == type }
}