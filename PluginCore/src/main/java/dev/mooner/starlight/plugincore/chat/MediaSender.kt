package dev.mooner.starlight.plugincore.chat

import java.io.File
import java.util.concurrent.CompletableFuture

/** Sends files to chat rooms for [ChatRoom.sendMedia]. StarLight provides it. */
fun interface MediaSender {

    /**
     * Sends [files] to [room] with [options], both as a script passes them. Never throws: a send that fails resolves
     * with the reason.
     */
    fun send(room: ChatRoom, files: Any?, options: Any?): CompletableFuture<MediaSendResult>

    companion object {

        /** The sender rooms use; StarLight sets it while it starts. */
        @Volatile
        var current: MediaSender? = null
    }
}

/** A file ready to show in a room that takes files itself, such as the debug room. */
class MediaFile(
    val file: File,
    val mimeType: String,
    /** The name it is sent under. */
    val name: String = file.name,
)
