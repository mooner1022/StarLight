/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.send

import dev.mooner.starlight.media.media.MediaSource
import dev.mooner.starlight.media.room.Destination

/** Where to go when leaving KakaoTalk after a send. */
sealed interface LeaveTo {

    /** Stay in KakaoTalk. */
    data object Stay : LeaveTo

    /** The home screen. */
    data object Home : LeaveTo

    /** StarLight itself. */
    data object StarLight : LeaveTo

    /** The launcher activity of another app. */
    data class App(val packageName: String) : LeaveTo

    companion object {
        const val KEY_STAY = "stay"
        const val KEY_HOME = "home"
        const val KEY_STARLIGHT = "starlight"

        /**
         * The choice saved in the plugin's settings; the home screen when none was saved. A chat room left open gets no
         * notifications, so a bot that stays misses that room's messages until the screen goes off.
         */
        fun ofSetting(key: String?): LeaveTo =
            when (key) {
                KEY_STAY      -> Stay
                KEY_STARLIGHT -> StarLight
                else          -> Home
            }

        /** A script's choice: `"stay"`, `"home"`, `"starlight"`, or a package name. */
        fun of(key: String): LeaveTo =
            when (key.trim().lowercase()) {
                KEY_STAY      -> Stay
                KEY_HOME, ""  -> Home
                KEY_STARLIGHT -> StarLight
                else          -> App(key.trim())
            }
    }
}

data class SendSettings(
    val downloadTimeoutMillis: Int = 30_000,
    val maxFileBytes: Long = 300L shl 20,
    val cacheByDefault: Boolean = false,
    val cacheMaxItems: Int = 200,
    val cacheMaxBytes: Long = 500L shl 20,
    /** How long copies and downloads are kept after a send, for KakaoTalk to read them. */
    val tempRetentionMillis: Long = 60_000,
    /** Least time between two sends; KakaoTalk may drop a share that arrives while it is handling another. */
    val sendIntervalMillis: Long = 0,
    /** Where sends leave KakaoTalk for unless a script says otherwise. */
    val leaveTo: LeaveTo = LeaveTo.Home,
    /**
     * Longest stay in KakaoTalk after a send: KakaoTalk is left once it has read the files, or after this if their
     * reading isn't seen. While a chat room is open, KakaoTalk posts no notifications for it, so its messages are missed.
     */
    val leaveMaxWaitMillis: Long = 5_000,
    /** Time left to KakaoTalk after it read the files, to post the message before it goes to the background. */
    val leaveGraceMillis: Long = 500,
    val logSends: Boolean = false,
)

/** When to leave KakaoTalk. */
sealed interface LeaveTiming {

    /** Once KakaoTalk has read the files of the latest send (see [SendSettings.leaveMaxWaitMillis]). */
    data object AfterRead : LeaveTiming

    /** After a fixed time. */
    data class After(val millis: Long) : LeaveTiming
}

/** A send as a script asked for it, with every script value already read. */
data class SendRequest(
    val target: Destination,
    val sources: List<MediaSource>,
    /** Name to send under (`name_1`, `name_2`, ... for several files); null keeps the files' own names. */
    val fileName: String? = null,
    val timeoutMillis: Int? = null,
    /** Null uses the plugin's setting. */
    val useCache: Boolean? = null,
    /** Extra request headers for downloads. */
    val headers: Map<String, String> = emptyMap(),
    /** Where to leave KakaoTalk for once it has the files; null uses the plugin's setting. */
    val leaveTo: LeaveTo? = null,
)

/** What a send handed to KakaoTalk. */
data class SendOutcome(
    val sent: Int,
    /** Why each file left out of the send failed, as `source: reason`. */
    val failures: List<String>,
    /** How KakaoTalk was reached if not directly: another user's, or through Android's chooser. */
    val via: String?,
)

class SendException(message: String) : Exception(message)

/** Where a send reports what went wrong with single files: the project's log. */
interface SendLog {
    val projectName: String
    fun info(message: String)
    fun warn(message: String)
}
