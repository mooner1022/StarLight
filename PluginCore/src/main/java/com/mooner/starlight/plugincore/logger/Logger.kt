package com.mooner.starlight.plugincore.logger

class Logger {
    private val listeners: ArrayList<(log: LogData) -> Unit> = arrayListOf()
    val logs: ArrayList<LogData> = arrayListOf()

    fun bindListener(listener: (log: LogData) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun d(tag: String, message: String) = log(LogType.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(LogType.INFO, tag, message)

    fun e(tag: String, message: String) = log(LogType.ERROR, tag, message)

    fun w(tag: String, message: String) = log(LogType.WARNING, tag, message)

    fun log(type: LogType, tag: String, message: String) {
        val data = LogData(
            type = type,
            tag = tag,
            message = message
        )
        println("[${type.name}] $tag : $message")
        logs.add(data)

        for (listener in listeners) {
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