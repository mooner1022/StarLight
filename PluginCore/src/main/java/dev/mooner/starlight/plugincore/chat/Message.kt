package dev.mooner.starlight.plugincore.chat

import android.graphics.Bitmap

/**
 * A chat message that arrived, as `onMessage(event)` receives it: its text, sender and room.
 *
 * Reply in the same room with `event.room.send(text)`:
 *
 * ```js
 * export function onMessage(event) {
 *     if (event.message === "!ping")
 *         event.room.send("pong");
 * }
 * ```
 *
 * In Rhino the function is declared without `export`. Messages typed in the debug room arrive as this class too, with
 * `event.room.isDebugRoom()` true and a few values that differ from a messenger's. MessengerBot API2 listeners get the
 * same message, wrapped, as their `msg`.
 *
 * @see guide:testing#how-debug-messages-differ
 */
data class Message(
    /** The message's text, as the messenger's notification shows it. */
    val message     : String,
    /**
     * The image the message's notification carries, such as a photo that was sent; `null` when there is none.
     *
     * Always `null` in the debug room. It can be passed as it is to `room.sendMedia` or `MediaSharer.send`.
     */
    val image       : Bitmap?,
    /** Who sent the message: name, ID and profile picture. */
    val sender      : ChatSender,
    /**
     * The room the message came from, and the one to reply to with `event.room.send(text)`.
     *
     * In the debug room, a room whose replies go back to the debug room.
     */
    val room        : ChatRoom,
    /**
     * The package name of the messenger app the message came from, such as `com.kakao.talk` for KakaoTalk.
     *
     * In the debug room, the app package set in its settings, `com.kakao.talk` by default. A plugin that brings
     * another messenger sets its own value.
     */
    val packageName : String,
    /**
     * Whether the message contains a mention, as far as the messenger's notification shows.
     *
     * In the debug room screen, a message typed with `<@name>` counts as a mention and arrives with `@name` in its
     * text; messages from `send_debug_message` never do.
     */
    val hasMention  : Boolean,
    /**
     * The messenger's ID for this message, such as KakaoTalk's chat log ID; `0` when the notification has none.
     *
     * `-1` in the debug room. The deleted message given to `onMessageDeleted` carries the same ID. In NeonJS it is a
     * number, or a BigInt beyond 2^53; `String(event.chatLogId)` gives its digits either way.
     */
    val chatLogId   : Long,
)