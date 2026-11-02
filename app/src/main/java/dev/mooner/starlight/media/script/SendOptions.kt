/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.script

import dev.mooner.starlight.media.media.FileTypes
import dev.mooner.starlight.media.media.InvalidSourceException
import dev.mooner.starlight.media.send.LeaveTo

/**
 * The options of a send: `{ fileName, timeout, cache, headers, leave }`.
 *
 * `leave` is where to go once KakaoTalk has the files: `true` or `"home"`, `"starlight"`, a package name, or `false`
 * or `"stay"`; absent, the plugin's setting.
 */
data class SendOptions(
    val fileName: String? = null,
    val timeoutMillis: Int? = null,
    val cache: Boolean? = null,
    val headers: Map<String, String> = emptyMap(),
    val leaveTo: LeaveTo? = null,
) {

    companion object {

        const val MIN_TIMEOUT_MILLIS = 1_000L
        const val MAX_TIMEOUT_MILLIS = 600_000L

        private val KEYS = setOf("fileName", "timeout", "cache", "headers", "leave")

        fun read(options: Any?): SendOptions {
            if (ScriptValues.isAbsent(options))
                return SendOptions()
            if (!ScriptValues.isObject(options))
                throw IllegalArgumentException("Options must be an object: { ${KEYS.joinToString()} }")
            ScriptValues.keys(options).firstOrNull { it !in KEYS }?.let { key ->
                throw IllegalArgumentException("Unknown option '$key'; options are ${KEYS.joinToString()}")
            }
            val fileName = ScriptValues.toText(ScriptValues.member(options, "fileName"))
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.also { name ->
                    if (!FileTypes.isValidFileName(name))
                        throw InvalidSourceException("Invalid file name: $name")
                }
            return SendOptions(
                fileName = fileName,
                timeoutMillis = ScriptValues.toLong(ScriptValues.member(options, "timeout"))?.let(::timeoutOf),
                cache = ScriptValues.toBoolean(ScriptValues.member(options, "cache")),
                headers = ScriptValues.member(options, "headers")?.let(ScriptValues::stringEntries).orEmpty(),
                leaveTo = ScriptValues.member(options, "leave")?.let(::leaveToOf),
            )
        }

        /** `true`/`"home"`, `"starlight"`, a package name, or `false`/`"stay"`; null if absent. */
        fun leaveToOf(value: Any?): LeaveTo? =
            when (val value = ScriptValues.unwrap(value)) {
                null            -> null
                is Boolean      -> if (value) LeaveTo.Home else LeaveTo.Stay
                is CharSequence -> LeaveTo.of(value.toString())
                else            -> throw IllegalArgumentException("leave must be true, false, \"home\", \"starlight\" or a package name")
            }

        /** Null (the setting) for zero or less; clamped otherwise. */
        private fun timeoutOf(millis: Long): Int? =
            millis.takeIf { it > 0 }?.coerceIn(MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS)?.toInt()
    }
}
