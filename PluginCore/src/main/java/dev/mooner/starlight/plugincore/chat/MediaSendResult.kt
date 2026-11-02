package dev.mooner.starlight.plugincore.chat

/**
 * What sending files to a chat room resolves to, whether or not it worked: `r.ok`, `r.error`, ...
 *
 * A failed send resolves too (never rejects), so a send nobody awaits doesn't end in an unhandled rejection.
 */
class MediaSendResult(
    /** Whether the room got the files (some of them, if [failed] > 0). */
    val ok: Boolean,
    /** The room, as `name (id)`. */
    val room: String,
    /** Files the room got. */
    val sent: Int,
    /** Files left out because they couldn't be prepared; see [errors]. */
    val failed: Int,
    /** Why nothing was sent; null if the room got the files. */
    val error: String?,
    /** Why each file left out failed, as `source: reason`. */
    val errors: List<String>,
    /** How the room was reached if not directly, such as `"user 95"` (Dual Messenger) or `"chooser"`; else null. */
    val via: String?,
) {

    override fun toString(): String =
        if (ok) "MediaSendResult(ok, room=$room, sent=$sent, failed=$failed${via?.let { ", via=$it" } ?: ""})"
        else "MediaSendResult(failed, room=$room, error=$error)"

    companion object {

        fun failure(room: String, error: String): MediaSendResult =
            MediaSendResult(false, room, 0, 0, error, emptyList(), null)
    }
}
