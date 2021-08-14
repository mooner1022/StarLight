package com.mooner.starlight.plugincore.logger

class Logger {

    init {

    }

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
                LogType.DEBUG,
                tag,
                message
            )
        )

        fun i(tag: String, message: String) = log(
            LogData(
                LogType.INFO,
                tag,
                message
            )
        )

        fun e(tag: String, message: String) = log(
            LogData(
                LogType.ERROR,
                tag,
                message
            )
        )

        fun w(tag: String, message: String) = log(
            LogData(
                LogType.WARNING,
                tag,
                message
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