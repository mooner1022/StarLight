package com.mooner.starlight.plugincore.logger

data class LogData(
    val type: LogType,
    val tag: String,
    val message: String
) {
    override fun toString(): String {
        return "[${type.name}] $tag : $message"
    }
}
