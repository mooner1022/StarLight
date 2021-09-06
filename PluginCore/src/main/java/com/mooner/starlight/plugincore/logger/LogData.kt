package com.mooner.starlight.plugincore.logger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LogData(
    @SerialName("t")
    val type: LogType,
    @SerialName("a")
    val tag: String,
    @SerialName("")
    val threadName: String = "undefined",
    @SerialName("m")
    val message: String,
    @SerialName("l")
    val millis: Long = System.currentTimeMillis(),
    @Transient
    val isLocal: Boolean = false
) {
    override fun toString(): String {
        return if (isLocal) {
            "[$threadName] [LOCAL/${type.name}] $tag : $message"
        } else {
            "[$threadName] [${type.name}] $tag : $message"
        }
    }
}
