package dev.mooner.starlight.plugincore.chat

import java.util.concurrent.CompletableFuture

interface ChatRoom {

    val id: String

    val name: String

    val isGroupChat: Boolean

    val isDebugRoom: Boolean

    fun send(message: String): Boolean

    fun markAsRead(): Boolean

    /**
     * Sends [files] to the room: an absolute path, an http(s) URL, a data URL, Base64 text, bytes or a Bitmap, or an
     * array of these. Returns at once; the result resolves once the room has the files or the send failed (a Promise
     * in NeonJS, a CompletableFuture in Rhino). Relative paths need the project, so they go through `MediaSharer.send`.
     */
    fun sendMedia(files: Any?): CompletableFuture<MediaSendResult> =
        sendMedia(files, null)

    /** [options]: `{ fileName, timeout, cache, headers, leave }`, as for `MediaSharer.send`. */
    fun sendMedia(files: Any?, options: Any?): CompletableFuture<MediaSendResult> =
        MediaSender.current?.send(this, files, options)
            ?: CompletableFuture.completedFuture(MediaSendResult.failure(name, "Sending files isn't available here"))
}