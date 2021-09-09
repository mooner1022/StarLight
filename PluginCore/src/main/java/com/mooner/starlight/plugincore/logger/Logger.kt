package com.mooner.starlight.plugincore.logger

class Logger {

    companion object {
        private val listeners: MutableMap<String, (log: LogData) -> Unit> = hashMapOf()
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
                threadName = Thread.currentThread().name,
                isLocal = false
            )
        )

        fun i(tag: String, message: String) = log(
            LogData(
                type = LogType.INFO,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name,
                isLocal = false
            )
        )

        fun e(tag: String, message: String) = log(
            LogData(
                type = LogType.ERROR,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name,
                isLocal = false
            )
        )

        fun w(tag: String, message: String) = log(
            LogData(
                type = LogType.WARNING,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name,
                isLocal = false
            )
        )

        fun log(data: LogData) {
            println(data.toString())
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