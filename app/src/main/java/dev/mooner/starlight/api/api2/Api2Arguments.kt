/*
 * Api2Arguments.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.utils.toBase64
import java.math.BigInteger

/*
 * The objects API2 hands its listeners, shaped as MessengerBot R's documentation describes them. Boolean fields are
 * properties, not methods: their getters are named get… so both engines see `msg.isGroupChat`, not `msg.isGroupChat()`.
 * IDs are BigInts.
 */

/**
 * A chat message, as API2 `message` listeners receive it.
 *
 * Messages sent in the debug room differ: `isDebugRoom` is `true`, `channelId`, `image` and `author.hash` are `null`,
 * and `logId` is `-1`.
 *
 * @see guide:testing#how-debug-messages-differ
 */
open class Api2Message internal constructor(private val message: Message) {

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

    /** The room's ID; `null` for a room without a numeric one, such as the debug room. */
    val channelId: BigInteger?
        get() = message.room.id.toBigIntegerOrNull()

    /**
     * The message's ID in the messenger, as a BigInt.
     *
     * `-1` in the debug room, and `0` when the app's notification doesn't carry one.
     */
    val logId: BigInteger
        get() = BigInteger.valueOf(message.chatLogId)

    /**
     * The package name of the app the message came from, such as `com.kakao.talk`.
     *
     * In the debug room it is the package set in the debug room's settings.
     */
    val packageName: String
        get() = message.packageName

    /** Who sent the message. */
    val author: Api2User =
        Api2User(message.sender.name, Api2Image(message.sender.profileBitmap), message.sender.id)

    /**
     * The picture the message carries; `null` if it has none.
     *
     * It is the picture the messenger puts in its notification, so a message without one there has none here. Always
     * `null` in the debug room.
     */
    val image: Api2Image? =
        message.image?.let(::Api2Image)

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

    /** Whether the messenger is a dual-messenger copy; always `false`, as StarLight doesn't tell these apart yet. */
    @get:JvmName("getIsMultiChat")
    val isMultiChat: Boolean
        get() = false

    /**
     * Sends [content] to the room the message came from.
     *
     * In the debug room the reply shows there.
     *
     * @param content The text to send.
     * @return Whether it was handed to the messenger; `false` when the room's reply action has expired.
     */
    fun reply(content: String): Boolean =
        message.room.send(content)

    /**
     * Marks the room the message came from as read.
     *
     * @return Whether the messenger took it; `false` when the room's read action has expired. Always `true` in the
     * debug room.
     */
    fun markAsRead(): Boolean =
        message.room.markAsRead()
}

/**
 * A message that starts with the bot's command prefix, as API2 `command` listeners receive it.
 *
 * It has every member of `Api2Message`, plus `command` and `args`. With the prefix `/`, the message `/angles 30 60`
 * has the command `angles` and the arguments `30` and `60`.
 */
class Api2Command internal constructor(
    message: Message,
    /** The command's name: the first word after the prefix. */
    val command: String,
    /**
     * The words after the command, split at whitespace; empty when there are none.
     *
     * A Java array: `cmd.args[0]` and `cmd.args.length` work as they do in MessengerBot R. It isn't a JS array in
     * either engine, so use `Array.from(cmd.args)` for array methods such as `join`.
     */
    val args: Array<String>,
) : Api2Message(message) {

    companion object {

        /**
         * Reads [content] as a command: it starts with [prefix], the command name follows it, and the arguments are
         * the words after that. `null` when it isn't one.
         */
        internal fun parse(content: String, prefix: String?): Pair<String, Array<String>>? {
            if (prefix.isNullOrEmpty() || !content.startsWith(prefix))
                return null
            val words = content.substring(prefix.length).trim().split(WHITESPACE).filter(String::isNotEmpty)
            val command = words.firstOrNull() ?: return null
            return command to words.drop(1).toTypedArray()
        }

        private val WHITESPACE = "\\s+".toRegex()
    }
}

/** Who sent a message. */
class Api2User internal constructor(
    /** The sender's name, as the messenger shows it. */
    val name: String,
    /**
     * The sender's profile picture.
     *
     * In the debug room it is the sender profile set in the debug room's settings.
     */
    val avatar: Api2Image,
    /**
     * The sender's ID, unique within a room.
     *
     * `null` in the debug room, and when the app's notification doesn't carry one.
     */
    val hash: String?,
) {
    /** What MessengerBot R's starting code calls [hash]. */
    val userHash: String?
        get() = hash
}

/** A picture from a message: the one it carries, or its sender's profile picture. */
class Api2Image internal constructor(private val bitmap: Bitmap) {

    /**
     * The picture as PNG, encoded in Base64.
     *
     * The text has a line break every 76 characters, as Android's `Base64.DEFAULT` writes it; remove them where a
     * single line is needed. Each read encodes the picture again.
     */
    fun getBase64(): String =
        bitmap.toBase64()

    /** The picture as an Android `Bitmap`. */
    fun getBitmap(): Bitmap =
        bitmap
}

/** A room a bot can send to, from `BotManager.getRooms()`. */
class Api2Room internal constructor(private val room: ChatRoom) {

    /** The room's name. */
    val name: String
        get() = room.name

    /** The room's ID, as a BigInt; `null` for a room without a numeric one. */
    val channelId: BigInteger?
        get() = room.id.toBigIntegerOrNull()

    /** Whether the room is a group chat; a property, not a method: `room.isGroupChat`. */
    @get:JvmName("getIsGroupChat")
    val isGroupChat: Boolean
        get() = room.isGroupChat

    /**
     * Sends [message] to the room.
     *
     * @param message The text to send.
     * @return Whether it was handed to the messenger; `false` when the room's reply action has expired.
     */
    fun send(message: String): Boolean =
        room.send(message)

    /**
     * Marks the room as read.
     *
     * @return Whether the messenger took it; `false` when the room's read action has expired.
     */
    fun markAsRead(): Boolean =
        room.markAsRead()
}
