/*
 * Api2Arguments.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import android.graphics.Bitmap
import android.util.Base64
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.listener.chat.ChatSessions
import dev.mooner.starlight.listener.chat.RoomKey
import dev.mooner.starlight.listener.chat.RoomKeyHolder
import dev.mooner.starlight.listener.chat.isMultiChat
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/*
 * The objects API2 hands its listeners, shaped as MessengerBot R's documentation describes them. Boolean fields are
 * properties, not methods: their getters are named get… so both engines see `msg.isGroupChat`, not `msg.isGroupChat()`.
 * IDs are BigInts.
 */

/** The text an API2 user of the debug room has as its [Api2User.hash]. */
private const val DEBUG_USER_HASH = "DEBUG ROOM"

/**
 * A chat message, as API2 `message` listeners receive it.
 *
 * Messages sent in the debug room differ: `isDebugRoom` is `true`, `channelId` is 0, `logId` is 0, `isMultiChat` is
 * `false`, `author.hash` is `"DEBUG ROOM"`, and `markAsRead()` returns `false`.
 *
 * @see guide:testing#how-debug-messages-differ
 */
open class Api2Message internal constructor(
    private val message: Message,
    author: Api2User? = null,
) {

    /** The underlying message. */
    internal val source: Message
        get() = message

    /** The room's ID as the messenger gave it, which a `Chat.waitFor` target matches by. */
    internal val rawRoomId: String
        get() = message.room.id

    /** The name of the room the message came from. */
    val room: String
        get() = message.room.name

    /**
     * The message's text.
     *
     * In the debug room screen, a mention written as `<@name>` arrives as `@name`.
     */
    val content: String
        get() = message.message

    /** The room's ID as a BigInt; `0` for a room without a numeric one, such as the debug room. */
    val channelId: BigInteger
        get() = RoomKey.idOf(message.room.id)

    /**
     * The message's ID in the messenger, as a BigInt.
     *
     * `0` in the debug room, and when the app's notification doesn't carry one.
     */
    val logId: BigInteger
        get() = if (message.chatLogId > 0L) BigInteger.valueOf(message.chatLogId) else BigInteger.ZERO

    /**
     * The package name of the app the message came from, such as `com.kakao.talk`.
     *
     * In the debug room it is the package set in the debug room's settings.
     */
    val packageName: String
        get() = message.packageName

    /** Who sent the message. */
    val author: Api2User = author ?: Api2User(
        message.sender.name,
        Api2Image(message.sender.profileBitmap),
        if (message.room.isDebugRoom) DEBUG_USER_HASH else message.sender.id,
    )

    /**
     * The sender's profile picture: the same object as `author.avatar`.
     *
     * It is not a picture the message carries. In the debug room it is the sender profile set in the debug room's
     * settings.
     */
    val image: Api2Image
        get() = author.avatar

    /**
     * Whether the room is a group chat.
     *
     * A property, not a method: `msg.isGroupChat`. In the debug room it follows the debug room's settings.
     */
    @get:JvmName("getIsGroupChat")
    val isGroupChat: Boolean
        get() = message.room.isGroupChat

    /**
     * Whether the message came from the debug room rather than a messenger.
     *
     * A property, not a method: `msg.isDebugRoom`.
     *
     * @see guide:testing#the-debug-room
     */
    @get:JvmName("getIsDebugRoom")
    val isDebugRoom: Boolean
        get() = message.room.isDebugRoom

    /**
     * Whether the message mentions someone.
     *
     * A property, not a method: `msg.isMention`. In the debug room it is `true` only for a message typed in the screen
     * with a mention written as `<@name>`.
     */
    @get:JvmName("getIsMention")
    val isMention: Boolean
        get() = message.hasMention

    /**
     * Whether the messenger is a dual-messenger copy: the notification belongs to an Android user other than the
     * device's owner, as for a secure folder or work profile. Always `false` in the debug room.
     *
     * A property, not a method: `msg.isMultiChat`.
     */
    @get:JvmName("getIsMultiChat")
    val isMultiChat: Boolean
        get() = message.room.isMultiChat()

    /**
     * Sends [content] to the room the message came from.
     *
     * In the debug room the reply shows there.
     *
     * @param content The text to send; any other value is sent as its text, `null` as `"null"`.
     * @return Whether the room has a session to reply through, or is the debug room; `false` when the room's reply
     * action has expired (its notification was dismissed). `true` doesn't mean the messenger delivered it.
     */
    fun reply(content: Any?): Boolean {
        val text = ScriptArgs.stringify(content)
        val room = message.room
        if (room.isDebugRoom)
            return room.send(text)
        val key = RoomKey.of(room)
        if (ChatSessions.hasSession(key, message.packageName))
            return ChatSessions.reply(key, text, message.packageName, cancelledIsSent = false)
        return room.send(text)
    }

    /**
     * Sends [content] to the room the message came from as a Markdown message.
     *
     * Messengers may refuse such a message; `true` only means it was handed over. KakaoTalk currently doesn't take it.
     * In the debug room the text shows there, as a reply.
     *
     * @param content The text to send; any other value is sent as its text, `null` as `"null"`.
     * @return Whether the share was launched, or `true` in the debug room; `false` when no app can take it.
     */
    fun replyWithMarkdown(content: Any?): Boolean {
        val text = ScriptArgs.stringify(content)
        val room = message.room
        if (room.isDebugRoom)
            return room.send(text)
        return ChatSessions.sendMarkdown(RoomKey.of(room), text, message.packageName)
    }

    /**
     * Marks the room the message came from as read.
     *
     * @return Whether the messenger's read action was sent; `false` when it has none or it has expired, and always
     * `false` in the debug room.
     */
    fun markAsRead(): Boolean {
        val room = message.room
        if (room.isDebugRoom)
            return false
        val key = RoomKey.of(room)
        if (ChatSessions.hasSession(key, message.packageName))
            return ChatSessions.markAsRead(key, message.packageName)
        return room.markAsRead()
    }
}

/**
 * A message whose first word starts with the bot's command prefix, as API2 `command` listeners receive it.
 *
 * It has every member of `Api2Message`, plus `command` and `args`. The message is split at every single space. With the
 * prefix `/`, the message `/angles 30 60` has the command `angles` and the arguments `30` and `60`; with the default
 * prefix `""`, every message is a command, its first word being the command.
 */
class Api2Command internal constructor(
    message: Message,
    /** The command's name: the message's first word, without the prefix. */
    val command: String,
    /**
     * The words after the command, split at single spaces, so two spaces in a row make an empty word; empty when there
     * are none.
     *
     * A Java list: `cmd.args[0]` and `cmd.args.length` work as they do in MessengerBot R. It isn't a JS array in
     * either engine, so use `Array.from(cmd.args)` for array methods such as `join`.
     */
    val args: List<String>,
    author: Api2User? = null,
) : Api2Message(message, author) {

    companion object {

        /**
         * Reads [content] as a command: its text before the first space starts with [prefix], which is removed to make
         * the command name, and the arguments are the pieces after it, split at every single space. `null` when it
         * isn't one.
         */
        internal fun parse(content: String, prefix: String): Pair<String, List<String>>? {
            val words = content.split(' ')
            val first = words[0]
            if (!first.startsWith(prefix))
                return null
            return first.removePrefix(prefix) to words.drop(1)
        }
    }
}

/** Who sent a message. */
class Api2User internal constructor(
    /** The sender's name, as the messenger shows it. */
    val name: String,
    /**
     * The sender's profile picture; the same object as the message's `image`.
     *
     * In the debug room it is the sender profile set in the debug room's settings.
     */
    val avatar: Api2Image,
    /**
     * The sender's ID, unique within a room.
     *
     * `"DEBUG ROOM"` in the debug room; `null` when the app's notification doesn't carry one.
     */
    val hash: String?,
) {
    /** What MessengerBot R's starting code calls [hash]. */
    val userHash: String?
        get() = hash

    /** The sender's name, so `"" + msg.author` and `String(msg.author)` give the name. */
    override fun toString(): String =
        name

    /** A description with the name, the avatar's Base64 text and the hash, as `User[name=… avatar=… hash=…]`. */
    fun toSource(): String =
        "User[name=$name avatar=$avatar hash=$hash]"
}

/** A picture of a message's sender. */
class Api2Image internal constructor(private val bitmap: Bitmap) {

    /**
     * The picture as JPEG at quality 100, encoded in Base64; `""` when it can't be encoded.
     *
     * The text has a line break every 76 characters, as Android's `Base64.DEFAULT` writes it; remove them where a
     * single line is needed. Each read encodes the picture again.
     */
    fun getBase64(): String =
        try {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            Base64.encodeToString(bytes.toByteArray(), Base64.DEFAULT)
        } catch (_: Exception) {
            ""
        }

    /** The picture as an Android `Bitmap`. */
    fun getBitmap(): Bitmap =
        bitmap

    /** The picture's Base64 text, as `getBase64()` gives it. */
    override fun toString(): String =
        getBase64()

    /** A description with the Base64 text, as `Image[base64=…]`. */
    fun toSource(): String =
        "Image[base64=${getBase64()}]"
}

/**
 * A room a bot can send to, from `BotManager.getRooms()`.
 *
 * Two room objects are equal when their names and IDs are. It can be passed to `bot.send`, `bot.canReply`,
 * `bot.markAsRead` and `bot.sendMarkdown`, and written out as `Room(name=…, id=…)`.
 */
class Api2Room internal constructor(
    private val key: RoomKey,
    private val room: ChatRoom?,
): RoomKeyHolder {

    /** The room's name and ID together, which identify it when it is passed back to a bot. */
    override val roomKey: RoomKey
        get() = key

    /** The room's name. */
    val name: String
        get() = key.name

    /** The room's ID, as a BigInt; `0` for a room without a numeric one. */
    val id: BigInteger
        get() = key.id

    /** The room's ID as a BigInt; `null` for a room without a numeric one. */
    val channelId: BigInteger?
        get() = room?.id?.toBigIntegerOrNull()

    /** Whether the room is a group chat; a property, not a method: `room.isGroupChat`. */
    @get:JvmName("getIsGroupChat")
    val isGroupChat: Boolean
        get() = room?.isGroupChat == true

    /**
     * Sends [message] to the room.
     *
     * @param message The text to send.
     * @return Whether it was handed to the messenger; `false` when the room's reply action has expired.
     */
    fun send(message: String): Boolean =
        room?.send(message) ?: ChatSessions.reply(key, message, cancelledIsSent = false)

    /**
     * Marks the room as read.
     *
     * @return Whether the messenger took it; `false` when the room's read action has expired.
     */
    fun markAsRead(): Boolean =
        room?.markAsRead() ?: ChatSessions.markAsRead(key)

    /** `Room(name=<name>, id=<id>)`. */
    override fun toString(): String =
        key.toString()

    override fun equals(other: Any?): Boolean =
        other is Api2Room && other.key == key

    override fun hashCode(): Int =
        key.hashCode()
}
