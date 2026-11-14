package dev.mooner.starlight.listener.legacy

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.utils.toBase64

/**
 * The sender's profile image, for the legacy `response` event: MessengerBot's `imageDB`.
 *
 * It gives the sender's profile image (`getProfile*`) and the picture the message itself carries (`getImage*`, `null` when
 * the message has none). MessengerBot's hash variants (`getProfileMD5`, `getImageHash` and the like) and the room
 * image members don't exist. In the debug room the profile image is the debug sender's, and no message has a picture.
 */
data class ImageDB(
    private val bitmap: Bitmap,
    private val messageImage: Bitmap? = null,
) {
    private var base64Cache: String? = null
    private var imageBase64Cache: String? = null

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

    /**
     * The picture the message carries, as a PNG encoded in Base64.
     *
     * Android's default Base64 breaks it into lines of 76 characters and ends it with a line break.
     *
     * @return `null` when the message has no picture.
     */
    fun getImageBase64(): String? {
        val image = messageImage ?: return null
        if (imageBase64Cache == null)
            imageBase64Cache = image.toBase64()
        return imageBase64Cache
    }

    /**
     * The picture the message carries, as a PNG in Base64; the same as `getImageBase64()`.
     *
     * @return `null` when the message has no picture.
     */
    fun getImage(): String? =
        getImageBase64()

    /**
     * The picture the message carries, as an `android.graphics.Bitmap`.
     *
     * @return `null` when the message has no picture.
     */
    fun getImageBitmap(): Bitmap? =
        messageImage
}
