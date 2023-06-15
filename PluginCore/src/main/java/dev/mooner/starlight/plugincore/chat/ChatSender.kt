package dev.mooner.starlight.plugincore.chat

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.utils.toBase64

data class ChatSender(
    val name: String,
    // null if Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val id: String?,
    val profileBitmap: Bitmap
) {
    private var base64Cache: String? = null

    val profileBase64: String
        get() {
            if (base64Cache == null)
                base64Cache = profileBitmap.toBase64()
            return base64Cache!!
        }

    val profileHash: Int
        get() = profileBase64.hashCode()
}
