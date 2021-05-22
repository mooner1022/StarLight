package com.mooner.starlight.plugincore.logger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogData(
    @SerialName("t")
    val type: LogType,
    @SerialName("a")
    val tag: String,
    @SerialName("m")
    val message: String,
    @SerialName("l")
    val millis: Long
) {
    override fun toString(): String {
        return "[${type.name}] $tag : $message"
    }
}
