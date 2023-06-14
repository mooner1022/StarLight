package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.utils.TimeUtils.Companion.formatCurrentDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LogData(
    @SerialName("t")
    val type: LogType,
    @SerialName("a")
    val tag: String? = null,
    @SerialName("th")
    val threadName: String = Thread.currentThread().name,
    @SerialName("m")
    val message: String,
    @SerialName("l")
    val millis: Long = System.currentTimeMillis(),
    @Transient
    val isLocal: Boolean = false,
    @Transient
    val flags: Int = 0,
) {

    companion object {
        private const val MAX_THREAD_INDENT = 27
        private const val MAX_TAG_INDENT    = 20

        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        const val FLAG_NOTIFY = 1
    }

    override fun equals(other: Any?): Boolean {
        return other != null && (other is LogData && equals(other))
    }

    private fun equals(data: LogData): Boolean {
        return hashCode() == data.hashCode()
    }

    fun toString(excludeThread: Boolean = false): String {
        return if (excludeThread)
            "%s %7.7s - %$MAX_TAG_INDENT.${MAX_TAG_INDENT}s: %s"
                .format(formatCurrentDate(TIMESTAMP_FORMAT), type.name, tag, message)
        else
            "%s %7.7s [%-$MAX_THREAD_INDENT.${MAX_THREAD_INDENT}s] - %$MAX_TAG_INDENT.${MAX_TAG_INDENT}s: %s"
                .format(formatCurrentDate(TIMESTAMP_FORMAT), type.name, threadName, tag, message)
    }

    override fun toString(): String {
        return toString(excludeThread = false)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (tag?.hashCode() ?: 0)
        result = 31 * result + threadName.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + millis.hashCode()
        result = 31 * result + isLocal.hashCode()
        return result
    }
}
