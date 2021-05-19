package com.mooner.starlight.plugincore.logger

import kotlinx.serialization.Serializable

@Serializable
data class LogData(
    val type: LogType,
    val tag: String,
    val message: String,
    val millis: Long
) {
    override fun toString(): String {
        return "[${type.name}] $tag : $message"
    }
}
