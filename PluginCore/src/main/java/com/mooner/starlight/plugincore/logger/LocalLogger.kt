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
            val logs: ArrayList<LogData>
            try {
                logs = Json.decodeFromString(raw)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IllegalArgumentException("Cannot parse log file ${file.name}")
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

    fun d(tag: String, message: String) = log(LogType.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(LogType.INFO, tag, message)

    fun e(tag: String, message: String) = log(LogType.ERROR, tag, message)

    fun w(tag: String, message: String) = log(LogType.WARNING, tag, message)

    private fun log(type: LogType, tag: String, message: String) {
        val data = LogData(
            type = type,
            tag = tag,
            message = message,
            System.currentTimeMillis()
        )
        _logs.add(data)
        flush()
        println("[LOCAL/${type.name}] $tag : $message")
    }

    private fun flush() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                file.writeText(Json.encodeToString(_logs))
            } catch (e: ConcurrentModificationException) {

            }
        }
    }
}