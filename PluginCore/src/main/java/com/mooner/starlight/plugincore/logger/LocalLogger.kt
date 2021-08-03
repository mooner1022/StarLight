package com.mooner.starlight.plugincore.logger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalLogger(private var _logs: ArrayList<LogData>, private val file: File) {
    companion object {
        fun create(directory: File): LocalLogger {
            directory.mkdirs()
            val file = File(directory, "logs_local.json")
            file.writeText("")
            return LocalLogger(arrayListOf(), file)
        }

        fun fromFile(file: File): LocalLogger {
            if (!file.isFile || !file.exists()) {
                throw IllegalArgumentException("Unable to open file ${file.name}")
            }
            val raw = file.readText()
            if (raw.isBlank()) return create(file.parentFile!!)
            val logs: ArrayList<LogData> = try {
                Json.decodeFromString(raw)
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

    val logs: ArrayList<LogData>
        get() = this._logs

    fun d(tag: String, message: String) = log(
        LogData(
            LogType.DEBUG,
            tag,
            message,
            isLocal = true
        )
    )

    fun i(tag: String, message: String) = log(
        LogData(
            LogType.INFO,
            tag,
            message,
            isLocal = true
        )
    )

    fun e(tag: String, message: String) = log(
        LogData(
            LogType.ERROR,
            tag,
            message,
            isLocal = true
        )
    )

    fun w(tag: String, message: String) = log(
        LogData(
            LogType.WARNING,
            tag,
            message,
            isLocal = true
        )
    )

    private fun log(data: LogData) {
        _logs.add(data)
        Logger.log(data)
        flush()
    }

    private fun flush() {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(_logs) {
                file.writeText(Json.encodeToString(_logs))
            }
        }
    }
}