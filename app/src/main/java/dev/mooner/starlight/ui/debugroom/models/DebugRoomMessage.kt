package dev.mooner.starlight.ui.debugroom.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DebugRoomMessage(
    @SerialName("s")
    val sender: String,
    @SerialName("m")
    val message: String,
    @SerialName("f")
    val fileName: String? = null,
    @SerialName("v")
    var viewType: Int,
    @SerialName("t")
    val timestamp: Long = System.currentTimeMillis(),
    /** The type of a file the bot sent, kept under `chats/` as [fileName]. */
    @SerialName("mt")
    val mimeType: String? = null,
)