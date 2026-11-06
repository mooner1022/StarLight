package dev.mooner.starlight.plugincore.chat

import java.util.concurrent.CompletableFuture

/**
 * A chat room, as `event.room` gives it: where a message came from, and where replies go.
 *
 * A script may keep a room and send to it later, such as after `await` or from a timer.
 *
 * @see guide:testing#how-debug-messages-differ
 */
interface ChatRoom {

    /**
     * The room's ID, as the messenger gives it: for KakaoTalk, the chat room's numeric ID as a string.
     *
     * In the debug room, the ID kept in its settings, which isn't numeric.
     */
    val id: String

    /**
     * The room's name; in a one-to-one chat, the other person's name.
     *
     * In the debug room, the room name set in its settings, `"undefined"` until the user sets one, or the one
     * `send_debug_message` was given.
     */
    val name: String

    /**
     * Whether the room is a group chat rather than a one-to-one chat, as far as the messenger's notification shows.
     *
     * Scripts read it as `room.groupChat`, or call `room.isGroupChat()`; `room.isGroupChat` without the call is the
     * method itself, which is always truthy. In the debug room, it follows its settings or what `send_debug_message`
     * was given.
     */
    val isGroupChat: Boolean

    /**
     * Whether this is the debug room, where messages are typed in the app or sent by `send_debug_message` instead of
     * coming from a messenger.
     *
     * Scripts read it as `room.debugRoom`, or call `room.isDebugRoom()`. Without the call, `room.isDebugRoom` is the
     * method itself, which is always truthy.
     *
     * @see guide:testing#the-debug-room
     */
    val isDebugRoom: Boolean

    /**
     * Sends [message] to the room as a text message.
     *
     * For KakaoTalk, the text goes through the reply action of the room's notification, the same way a reply typed in
     * the notification would. In the debug room, it shows as the bot's reply.
     *
     * @param message The text to send. In NeonJS it must be a string: convert other values with `String(x)`, or the
     *   call throws a TypeError.
     * @return Whether the room took the message. For KakaoTalk, `true` means the reply was handed to KakaoTalk, not
     *   that it was delivered; `false` means KakaoTalk no longer takes replies through that notification. Always `true`
     *   in the debug room.
     */
    fun send(message: String): Boolean

    /**
     * Marks the room's messages as read in the messenger.
     *
     * For KakaoTalk, this uses the "mark as read" action of the room's notification. In the debug room screen it only
     * shows a notice; for `send_debug_message` it does nothing.
     *
     * @return Whether the messenger took the request; `false` when its action is no longer available. Always `true` in
     *   the debug room.
     */
    fun markAsRead(): Boolean

    /**
     * Sends [files] to the room: an absolute path, an http(s) URL, a data URL, Base64 text, bytes or a Bitmap, or an
     * array of these. Returns at once; the result resolves once the room has the files or the send failed (a Promise
     * in NeonJS, a CompletableFuture in Rhino). Relative paths need the project, so they go through `MediaSharer.send`.
     *
     * @param files The file or files to send, in any of the forms above.
     * @return What the send resolves to; a failed send resolves too, with the reason in its `error`.
     */
    fun sendMedia(files: Any?): CompletableFuture<MediaSendResult> =
        sendMedia(files, null)

    /**
     * Sends [files] to the room as `sendMedia(files)` does, with [options]: `{ fileName, timeout, cache, headers,
     * leave }`, as for `MediaSharer.send`.
     *
     * @param files The file or files to send, in the forms `sendMedia(files)` takes.
     * @param options The options object, or `null` for none.
     * @return What the send resolves to; a failed send resolves too, with the reason in its `error`.
     */
    fun sendMedia(files: Any?, options: Any?): CompletableFuture<MediaSendResult> =
        MediaSender.current?.send(this, files, options)
            ?: CompletableFuture.completedFuture(MediaSendResult.failure(name, "Sending files isn't available here"))
}