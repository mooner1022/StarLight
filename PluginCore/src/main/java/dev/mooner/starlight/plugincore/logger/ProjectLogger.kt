/*
 * LocalLogger.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.logger.internal.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class ProjectLogger private constructor(
    private var _logs: MutableList<LogData>,
    private val file: File
) {

    val logs: MutableList<LogData>
        get() = this._logs

    fun clear() {
        _logs.clear()
        flush()
    }

    fun v(message: String) =
        v(tag = null, message = message)

    fun v(tag: String?, message: String) =
        log(LogType.VERBOSE, tag, message)

    fun verbose(message: String) =
        v(tag = null, message = message)

    fun verbose(tag: String?, message: String) =
        v(tag = tag, message = message)

    fun d(message: String) =
        d(tag = null, message = message)

    fun d(tag: String?, message: String) =
        log(LogType.DEBUG, tag, message)

    fun debug(message: String) =
        d(tag = null, message = message)

    fun debug(tag: String?, message: String) =
        d(tag = tag, message = message)

    fun i(message: String) =
        i(tag = null, message = message)

    fun i(tag: String?, message: String) =
        log(LogType.INFO, tag, message)

    fun info(message: String) =
        i(tag = null, message = message)

    fun info(tag: String?, message: String) =
        i(tag = tag, message = message)

    fun w(message: String) =
        w(tag = null, message = message)

    fun w(tag: String?, message: String) =
        log(LogType.WARN, tag, message)

    fun warn(message: String) =
        w(tag = null, message = message)

    fun warn(tag: String?, message: String) =
        w(tag = tag, message = message)

    fun e(message: String) =
        e(tag = null, message = message)

    fun e(tag: String?, message: String) =
        log(LogType.ERROR, tag, message)

    fun error(message: String) =
        e(tag = null, message = message)

    fun error(tag: String?, message: String) =
        e(tag = tag, message = message)

    // What a Terrible Failure!
    fun wtf(message: String) =
        wtf(tag = null, message = message)

    fun wtf(tag: String?, message: String) =
        log(LogType.CRITICAL, tag, message)

    private fun log(type: LogType, tag: String?, message: String) {
        Logger.log(type, tag, message)

        val data = LogData(
            type = type,
            tag = tag,
            message = message
        )
        if (data.type != LogType.DEBUG) {
            synchronized(_logs) {
                _logs += data
                flush()
            }
        }
    }

    private fun flush() {
        try {
            flowOf(json.encodeToString(logs))
                .onEach(file::writeText)
                .catch { it.printStackTrace() }
                .launchIn(logFlushScope)
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.v("ProjectLogger", "Failed to flush project log: $e")
        }
    }

    companion object {

        @OptIn(ExperimentalCoroutinesApi::class)
        private val logFlushScope: CoroutineScope =
            CoroutineScope(Dispatchers.IO.limitedParallelism(1)) + SupervisorJob()

        fun create(directory: File): ProjectLogger {
            directory.mkdirs()
            val file = File(directory, "logs.json")
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
                Logger.e(ProjectLogger::class.simpleName!!, "Failed to parse log file ${file.name}, removing file")
                mutableListOf()
            }
            return ProjectLogger(logs, file)
        }
    }
}