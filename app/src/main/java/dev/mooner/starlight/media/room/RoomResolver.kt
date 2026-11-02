/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.room

import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import java.math.BigInteger

/** Where a send goes. */
sealed interface Destination {
    /** The room for logs and results. */
    val label: String
}

/**
 * A chat room to send to: KakaoTalk's channel ID, a name for logs, and the Android user whose KakaoTalk has the room
 * (null if no notification from it was seen).
 */
data class Target(val channelId: Long, override val label: String, val userId: Int? = null) : Destination

/** A room that shows the files itself, such as the debug room; KakaoTalk isn't involved. */
class DirectRoom(val room: DebugChatRoom) : Destination {
    override val label: String = "${room.name} (debug)"
}

/** What the resolver needs of StarLight's `ChatRoom`. */
data class ChatRoomRef(val id: String, val name: String, val isDebugRoom: Boolean)

class InvalidRoomException(message: String) : IllegalArgumentException(message)

/**
 * Reads the room a script names: a chat room or message object (read by [chatRoomOf]), a channel ID (text, BigInt
 * or a number small enough to be exact) or the name of a room a message was received from.
 */
class RoomResolver(
    private val registry: RoomRegistry,
    /** The chat room of a StarLight `ChatRoom` or `Message`; null for other values. */
    private val chatRoomOf: (Any) -> ChatRoomRef?,
) {

    fun resolve(value: Any?): Target {
        if (ScriptValues.isAbsent(value))
            throw InvalidRoomException("No room given (null or undefined)")
        val value = ScriptValues.unwrap(value)
            ?: throw InvalidRoomException("No room given (null or undefined)")
        chatRoomOf(value)?.let { return fromRoom(it) }
        return when (value) {
            is BigInteger   -> fromId(value.toString())
            is Double       -> fromNumber(value)
            is Float        -> fromNumber(value.toDouble())
            is Long, is Int, is Short -> fromId(value.toString())
            is CharSequence -> fromText(value.toString())
            else            -> throw InvalidRoomException("Unsupported room value: ${value.javaClass.name}")
        }
    }

    private fun fromRoom(room: ChatRoomRef): Target {
        if (room.isDebugRoom)
            throw InvalidRoomException("This debug room can't show files")
        if (!RoomRegistry.isChannelId(room.id))
            throw InvalidRoomException("Room '${room.name}' has no KakaoTalk channel ID (id: ${room.id})")
        return Target(room.id.toLong(), "${room.name} (${room.id})", registry.userOf(room.id))
    }

    private fun fromNumber(value: Double): Target {
        if (value % 1.0 != 0.0 || value < 0)
            throw InvalidRoomException("Not a channel ID: $value")
        // Channel IDs are usually above 2^53 and lose digits as JS numbers.
        if (value > MAX_SAFE_INTEGER)
            throw InvalidRoomException("Channel ID $value is too large for a JS number; pass it as a string or BigInt")
        return fromId(value.toLong().toString())
    }

    private fun fromText(text: String): Target {
        val text = text.trim()
        if (text.isEmpty())
            throw InvalidRoomException("Empty room name")
        if (RoomRegistry.isChannelId(text))
            return fromId(text)
        val room = registry.findByName(text)
            ?: throw InvalidRoomException(
                "Unknown room '$text': a room is known by name once a message from it was received; " +
                    "pass msg.room or the channel ID instead"
            )
        return Target(room.id.toLong(), "${room.name} (${room.id})", registry.userOf(room.id))
    }

    private fun fromId(id: String): Target {
        val channelId = id.toLongOrNull()?.takeIf { it > 0 }
            ?: throw InvalidRoomException("Not a channel ID: $id")
        val name = registry.get(id)?.name
        return Target(channelId, if (name != null) "$name ($id)" else id, registry.userOf(id))
    }

    private companion object {
        const val MAX_SAFE_INTEGER = 9007199254740991.0
    }
}
