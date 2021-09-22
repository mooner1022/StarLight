package com.mooner.starlight.plugincore.logger

import android.util.Log
import java.io.File

class Logger {

    companion object {
        private val listeners: MutableMap<String, (log: LogData) -> Unit> = hashMapOf()
        private lateinit var logFile: File
        var logs: ArrayList<LogData> = arrayListOf()

        fun bindListener(key: String, listener: (log: LogData) -> Unit) {
            listeners[key] = listener
        }

        fun unbindListener(key: String) {
            if (listeners.containsKey(key)) {
                listeners.remove(key)
            }
        }

        fun d(tag: String, message: String) = log(
            LogData(
                type = LogType.DEBUG,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name
            )
        )

        fun i(tag: String, message: String) = log(
            LogData(
                type = LogType.INFO,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name
            )
        )

        fun e(tag: String, message: String) = log(
            LogData(
                type = LogType.ERROR,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name
            )
        )

        // What a Terrible Failure!
        fun wtf(tag: String, message: String) = log(
            LogData(
                type = LogType.CRITICAL,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name
            )
        )

        fun w(tag: String, message: String) = log(
            LogData(
                type = LogType.WARNING,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name
            )
        )

        fun log(data: LogData) {
            Log.println(data.type.priority, data.tag, data.message)
            logs.add(data)

            for ((_, listener) in listeners) {
                listener(data)
            }
        }

        fun filter(type: LogType): List<LogData> {
            return logs.filter { it.type == type }
        }

        fun filterNot(type: LogType): List<LogData> {
            return logs.filterNot { it.type == type }
        }
    }
}