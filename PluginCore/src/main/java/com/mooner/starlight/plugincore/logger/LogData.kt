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
    @SerialName("m")
    val message: String,
    @SerialName("l")
    val millis: Long = System.currentTimeMillis(),
    @Transient
    val isLocal: Boolean = false
) {
    override fun toString(): String {
        return if (isLocal) {
            "[LOCAL/${type.name}] $tag : $message"
        } else {
            "[${type.name}] $tag : $message"
        }
    }
}
