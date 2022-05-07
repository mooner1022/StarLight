/*
 * LocalLogger.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.Session.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class ProjectLogger(
    private var _logs: MutableList<LogData>,
    private val file: File
) {

    companion object {

        fun create(directory: File): ProjectLogger {
            directory.mkdirs()
            val file = File(directory, "logs-local.json")
            file.createNewFile()
            return ProjectLogger(arrayListOf(), file)
        }

        fun fromFile(file: File): ProjectLogger {
            if (!file.isFile || !file.exists()) {
                throw IllegalArgumentException("Unable to open file ${file.name}")
            }
            val raw = file.readText()
            if (raw.isBlank()) return create(file.parentFile!!)
            val logs: MutableList<LogData> = try {
                json.decodeFromString(raw)
            } catch (e: Exception) {
                e.printStackTrace()
                file.delete()
                Logger.e(ProjectLogger::class.simpleName!!, "Cannot parse log file ${file.name}, removing old file")
                mutableListOf()
            }
            return ProjectLogger(logs, file)
        }
    }

    fun clear() {
        _logs.clear()
        flush()
    }

    val logs: MutableList<LogData>
        get() = this._logs

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
            type = LogType.WARN,
            tag = tag,
            message = message
        )
    )

    private fun log(data: LogData) {
        synchronized(_logs) {
            _logs += data
        }
        Logger.log(data)
        if (data.type != LogType.DEBUG)
            flush()
    }

    private fun flush() = runBlocking {
        withContext(Dispatchers.IO) {
            val logs = _logs.filterNot { it.type == LogType.DEBUG }
            flowOf(json.encodeToString(logs))
                .flowOn(Dispatchers.Default)
                .collect(file::writeText)
        }
    }
}