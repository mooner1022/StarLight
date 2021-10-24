package com.mooner.starlight.plugincore.logger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LogData(
    @SerialName("t")
    val type: LogType,
    @SerialName("a")
    val tag: String? = null,
    @SerialName("")
    val threadName: String = Thread.currentThread().name,
    @SerialName("m")
    val message: String,
    @SerialName("l")
    val millis: Long = System.currentTimeMillis(),
    @Transient
    val isLocal: Boolean = false
) {
    fun toString(excludeThread: Boolean = false): String {
        val append = StringBuilder("[").apply {
            if (!excludeThread)
                append("$threadName/")
            if (isLocal)
                append("LOCAL/")
            append("${type.name}]: ")
            if (tag != null)
                append("[$tag] ")
            append(message)
        }
        return append.toString()
    }

    override fun toString(): String {
        return toString(excludeThread = false)
    }
}
