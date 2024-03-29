package dev.mooner.starlight.listener.legacy

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.utils.toBase64

data class ImageDB(
    private val bitmap: Bitmap
) {
    private var base64Cache: String? = null

    fun getProfileBase64(): String {
        if (base64Cache == null)
            base64Cache = bitmap.toBase64()
        return base64Cache!!
    }

    fun getProfileHash(): Int =
        getProfileBase64().hashCode()

    fun getProfileImage(): String =
        getProfileBase64()

    fun getProfileBitmap(): Bitmap =
        bitmap
}