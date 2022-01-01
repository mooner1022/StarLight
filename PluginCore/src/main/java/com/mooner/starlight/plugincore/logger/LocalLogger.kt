package com.mooner.starlight.plugincore.logger

import com.mooner.starlight.plugincore.Session.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class LocalLogger(
    private var _logs: MutableList<LogData>,
    private val file: File
) {

    companion object {

        fun create(directory: File): LocalLogger {
            directory.mkdirs()
            val file = File(directory, "logs-local.json")
            file.createNewFile()
            return LocalLogger(arrayListOf(), file)
        }

        fun fromFile(file: File): LocalLogger {
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
                Logger.e(LocalLogger::class.simpleName!!, "Cannot parse log file ${file.name}, removing old file")
                arrayListOf()
            }
            return LocalLogger(logs, file)
        }
    }

    fun clear() {
        _logs.clear()
        flush()
    }

    val logs: MutableList<LogData>
        get() = this._logs

    fun v(message: String) = v(tag = null, message = message)

    fun v(tag: String?, message: String) = Logger.log(
        LogData(
            type = LogType.VERBOSE,
            tag = tag,
            message = message
        )
    )

    fun d(message: String) = d(tag = null, message = message)

    fun d(tag: String?, message: String) = Logger.log(
        LogData(
            type = LogType.DEBUG,
            tag = tag,
            message = message
        )
    )

    fun i(message: String) = i(tag = null, message = message)

    fun i(tag: String?, message: String) = Logger.log(
        LogData(
            type = LogType.INFO,
            tag = tag,
            message = message
        )
    )

    fun e(message: String) = e(tag = null, message = message)

    fun e(tag: String?, message: String) = Logger.log(
        LogData(
            type = LogType.ERROR,
            tag = tag,
            message = message
        )
    )

    // What a Terrible Failure!
    fun wtf(message: String) = wtf(tag = null, message = message)

    fun wtf(tag: String?, message: String) = Logger.log(
        LogData(
            type = LogType.CRITICAL,
            tag = tag,
            message = message
        )
    )

    fun w(message: String) = w(tag = null, message = message)

    fun w(tag: String?, message: String) = Logger.log(
        LogData(
            type = LogType.WARN,
            tag = tag,
            message = message
        )
    )

    private fun log(data: LogData) {
        synchronized(_logs) {
            _logs.add(data)
        }
        Logger.log(data)
        if (data.type != LogType.DEBUG) {
            flush()
        }
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