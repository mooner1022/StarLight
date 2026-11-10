package dev.mooner.starlight.plugincore.logger

import dev.mooner.starlight.plugincore.utils.TimeUtils.Companion.formatMillis
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * One log entry, as an item of `Log.logs`: its level, tag, text, time and thread.
 */
@Serializable
data class LogData(
    /** The entry's level, such as `INFO` or `ERROR`; `entry.type.name()` gives it as a string. */
    @SerialName("t")
    val type: LogType,
    /** The tag the entry was logged under, or `null` if it was logged without one. */
    @SerialName("a")
    val tag: String? = null,
    /** The name of the thread that logged the entry. */
    @SerialName("th")
    val threadName: String = Thread.currentThread().name,
    /** The logged text. */
    @SerialName("m")
    val message: String,
    /**
     * When the entry was logged, in milliseconds since 1970-01-01 UTC.
     *
     * In NeonJS it is a number, so `new Date(entry.millis)` reads it.
     */
    @EncodeDefault
    @SerialName("l")
    val millis: Long = System.currentTimeMillis(),
    /**
     * Not used by StarLight: the entries it makes leave it `false`. It isn't saved with the log.
     *
     * @suppress
     */
    @Transient
    val isLocal: Boolean = false,
    /**
     * How StarLight's own log shows the entry: [FLAG_NOTIFY] and [FLAG_COMPILE_RESULT] as bits, `0` for none.
     *
     * A project's entries always have `0`. It isn't saved with the log.
     *
     * @suppress
     */
    @Transient
    val flags: Int = 0,
) {

    companion object {
        private const val MAX_THREAD_INDENT = 27
        private const val MAX_TAG_INDENT    = 20

        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        /** In [flags]: an info entry pops up as an alert, as warnings and errors do. */
        const val FLAG_NOTIFY         = 1 shl 0
        /** In [flags]: the entry reports a compile result, which the main screen doesn't pop up. */
        const val FLAG_COMPILE_RESULT = 1 shl 1
    }

    override fun equals(other: Any?): Boolean {
        return other != null && (other is LogData && equals(other))
    }

    private fun equals(data: LogData): Boolean {
        return hashCode() == data.hashCode()
    }

    /**
     * The entry as short text: `I 12:34:56.789 - tag:`, then the message on the next line.
     *
     * The first letter is the level's, and the time is on the device's clock. An entry without a tag shows `null` in
     * its place.
     */
    fun toSimpleString(): String {
        return "%s %s - %s:\n%s"
            .format(type.name.substring(0, 1), formatMillis(millis, "HH:mm:ss.SSS"), tag, message)
    }

    fun toString(excludeThread: Boolean = false): String {
        return if (excludeThread)
            "%s %7.7s - %$MAX_TAG_INDENT.${MAX_TAG_INDENT}s: %s"
                .format(formatMillis(millis, TIMESTAMP_FORMAT), type.name, tag, message)
        else
            "%s %7.7s [%-$MAX_THREAD_INDENT.${MAX_THREAD_INDENT}s] - %$MAX_TAG_INDENT.${MAX_TAG_INDENT}s: %s"
                .format(formatMillis(millis, TIMESTAMP_FORMAT), type.name, threadName, tag, message)
    }

    override fun toString(): String {
        return toString(excludeThread = false)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + threadName.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + millis.hashCode()
        result = 31 * result + isLocal.hashCode()
        return result
    }
}
