package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.utils.TimeUtils.Companion.formatCurrentDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.min

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

    companion object {
        @JvmStatic
        private var maxLength: Int = 0

        @JvmStatic
        private var maxThreadLength: Int = 0

        private const val MAX_INDENT = 40

        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
    }

    fun toString(excludeThread: Boolean = false): String {
        val indent = if (tag != null) {
            val diff = min(maxLength - tag.length, MAX_INDENT)
            when {
                diff > 0 -> " ".repeat(diff)
                diff == MAX_INDENT -> ""
                else -> {
                    maxLength = tag.length
                    ""
                }
            }
        } else " ".repeat(maxLength)

        val diff = min(maxThreadLength - threadName.length, MAX_INDENT)
        val threadIndent = when {
            diff > 0 -> " ".repeat(diff)
            diff == MAX_INDENT -> ""
            else -> {
                maxThreadLength = threadName.length
                ""
            }
        }

        val append = StringBuilder(formatCurrentDate(TIMESTAMP_FORMAT)).apply {
            append(" ${" ".repeat(type.indentSize)}${type.name} ")
            append("[")
            if (!excludeThread) {
                append(threadName)
                append(threadIndent)
            }
            if (isLocal)
                append("/LOCAL")
            append("] - ")
            if (tag != null)
                append("$tag$indent: ")
            else
                append("$indent: ")
            append(message)
        }
        return append.toString()
    }

    override fun toString(): String {
        return toString(excludeThread = false)
    }
}
