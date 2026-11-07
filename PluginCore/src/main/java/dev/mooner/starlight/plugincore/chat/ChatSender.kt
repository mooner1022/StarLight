package dev.mooner.starlight.plugincore.chat

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.utils.toBase64

/**
 * The sender of a message, as `event.sender` gives it: name, ID and profile picture.
 *
 * @see guide:testing#how-debug-messages-differ
 */
data class ChatSender(
    /**
     * The sender's name, as the messenger's notification shows it.
     *
     * In the debug room, the sender name set in its settings, or the one `send_debug_message` was given.
     */
    val name: String,
    // null if Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    /**
     * An ID for the sender, as the messenger gives it; `null` when it gives none.
     *
     * For KakaoTalk, it is read from the notification on Android 9 and later, and is `null` on older versions. Always
     * `null` in the debug room.
     */
    val id: String?,
    /**
     * The sender's profile picture, as the messenger's notification shows it.
     *
     * In the debug room, the profile image set in its settings, or a placeholder. It can be passed as it is to
     * `room.sendMedia` or `MediaSharer.send`.
     */
    val profileBitmap: Bitmap
) {
    private var base64Cache: String? = null

    /**
     * The profile picture as a PNG, in Base64 text.
     *
     * It is made on first read and kept. The text has a line break every 76 characters, as Android's `Base64.DEFAULT`
     * writes it.
     */
    val profileBase64: String
        get() {
            if (base64Cache == null)
                base64Cache = profileBitmap.toBase64()
            return base64Cache!!
        }

    /**
     * A number that identifies the profile picture: the hash code of [profileBase64].
     *
     * The same picture gives the same number, as legacy `imageDB.getProfileHash()` does. Names can repeat, so it helps
     * tell senders apart, but it changes when the sender changes their picture, and senders with the same picture
     * share it.
     */
    val profileHash: Int
        get() = profileBase64.hashCode()
}
