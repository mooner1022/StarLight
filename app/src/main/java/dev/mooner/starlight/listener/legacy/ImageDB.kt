package dev.mooner.starlight.listener.legacy

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.utils.toBase64

/**
 * The sender's profile image, for the legacy `response` event: MessengerBot's `imageDB`.
 *
 * Only the sender's profile image is available; MessengerBot's members for a received image or the room's image,
 * such as `getImage` and `getRoomImage`, are missing. In the debug room it is the debug sender's profile image.
 */
data class ImageDB(
    private val bitmap: Bitmap
) {
    private var base64Cache: String? = null

    /**
     * The sender's profile image as a PNG, encoded in Base64.
     *
     * It is encoded once and kept. Android's default Base64 breaks it into lines of 76 characters and ends it with a
     * line break.
     */
    fun getProfileBase64(): String {
        if (base64Cache == null)
            base64Cache = bitmap.toBase64()
        return base64Cache!!
    }

    /**
     * A hash of the sender's profile image: the Java hash code of the `getProfileBase64()` text.
     *
     * It is a number, and different images can share one.
     */
    fun getProfileHash(): Int =
        getProfileBase64().hashCode()

    /**
     * The sender's profile image as a PNG in Base64; the same as `getProfileBase64()`.
     */
    fun getProfileImage(): String =
        getProfileBase64()

    /**
     * The sender's profile image, as an `android.graphics.Bitmap`.
     */
    fun getProfileBitmap(): Bitmap =
        bitmap
}