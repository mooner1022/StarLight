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

/** A message, given to `message` listeners. */
open class Api2Message internal constructor(private val message: Message) {

    val room: String
        get() = message.room.name

    val content: String
        get() = message.message

    /** The room's ID; `null` for a room without a numeric one, such as the debug room. */
    val channelId: BigInteger?
        get() = message.room.id.toBigIntegerOrNull()

    val logId: BigInteger
        get() = BigInteger.valueOf(message.chatLogId)

    val packageName: String
        get() = message.packageName

    val author: Api2User =
        Api2User(message.sender.name, Api2Image(message.sender.profileBitmap), message.sender.id)

    val image: Api2Image? =
        message.image?.let(::Api2Image)

    @get:JvmName("getIsGroupChat")
    val isGroupChat: Boolean
        get() = message.room.isGroupChat

    @get:JvmName("getIsDebugRoom")
    val isDebugRoom: Boolean
        get() = message.room.isDebugRoom

    @get:JvmName("getIsMention")
    val isMention: Boolean
        get() = message.hasMention

    /** Whether the messenger is a dual-messenger copy; StarLight doesn't tell these apart yet. */
    @get:JvmName("getIsMultiChat")
    val isMultiChat: Boolean
        get() = false

    fun reply(content: String): Boolean =
        message.room.send(content)

    fun markAsRead(): Boolean =
        message.room.markAsRead()
}

/** A message that starts with the bot's command prefix, given to `command` listeners. */
class Api2Command internal constructor(
    message: Message,
    val command: String,
    /** An array, so `cmd.args[0]` and `cmd.args.length` work as they do in MessengerBot R. */
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
    val name: String,
    val avatar: Api2Image,
    /** The sender's ID, unique within a room. */
    val hash: String?,
) {
    /** What MessengerBot R's starting code calls [hash]. */
    val userHash: String?
        get() = hash
}

class Api2Image internal constructor(private val bitmap: Bitmap) {

    fun getBase64(): String =
        bitmap.toBase64()

    fun getBitmap(): Bitmap =
        bitmap
}

/** A room a bot can send to, from `BotManager.getRooms()`. */
class Api2Room internal constructor(private val room: ChatRoom) {

    val name: String
        get() = room.name

    val channelId: BigInteger?
        get() = room.id.toBigIntegerOrNull()

    @get:JvmName("getIsGroupChat")
    val isGroupChat: Boolean
        get() = room.isGroupChat

    fun send(message: String): Boolean =
        room.send(message)

    fun markAsRead(): Boolean =
        room.markAsRead()
}
