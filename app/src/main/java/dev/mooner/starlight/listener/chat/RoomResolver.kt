/*
 * RoomResolver.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.chat

import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.plugincore.chat.ChatRoom
import java.math.BigInteger

/**
 * Turns what a script passes as a room into a [RoomKey], by the rules MessengerBot applies:
 *
 * - A string is a room name, only; a string of digits is a name, not an ID.
 * - A `BigInt` or any whole number (`Int`, `Long`, `Short`, `Byte`, a `Double` without a fraction) is a room ID.
 * - A [RoomKey], a [RoomKeyHolder] (such as an API2 room object) or a [ChatRoom] is that room, exactly, whether or not
 *   it has a session.
 * - A pair `[name, id]` (a script array, a Java list or a Kotlin [Pair]) is that room, exactly.
 * - Anything else, `null` and `undefined` included, is no room.
 *
 * A name or ID resolves only if a room was seen with it. When a package name is given, the room must also have a
 * session in that package, or it resolves to nothing.
 */
object RoomResolver {

    /** [resolve] against [ChatSessions.registry]. */
    fun resolve(room: Any?, packageName: String? = null): RoomKey? =
        resolve(ChatSessions.registry, room, packageName)

    internal fun resolve(registry: SessionRegistry, room: Any?, packageName: String? = null): RoomKey? {
        val key = keyOf(registry, room) ?: return null
        if (packageName != null && !registry.hasSession(key, packageName))
            return null
        return key
    }

    private fun keyOf(registry: SessionRegistry, room: Any?): RoomKey? {
        when (room) {
            null -> return null
            is RoomKey -> return room
            is RoomKeyHolder -> return room.roomKey
            is ChatRoom -> return RoomKey.of(room)
            is Pair<*, *> -> return pairKey(room.first, room.second)
        }
        if (ScriptArgs.isNullish(room))
            return null
        nameOf(room)?.let { return registry.byName(it) }
        ScriptArgs.integral(room)?.let { return registry.byId(it) }
        pairOf(room)?.let { (name, id) -> return pairKey(name, id) }
        return null
    }

    private fun pairKey(name: Any?, id: Any?): RoomKey? {
        val roomName = nameOf(name) ?: return null
        val roomId = idOf(id) ?: return null
        return RoomKey(roomName, roomId)
    }

    private fun idOf(value: Any?): BigInteger? =
        ScriptArgs.integral(value) ?: nameOf(value)?.trim()?.toBigIntegerOrNull()

    private fun nameOf(value: Any?): String? = when {
        value is CharSequence                  -> value.toString()
        value is NeonValue && value.isString   -> value.asString()
        else                                   -> null
    }

    /** The two items of a script array of exactly two, as either engine passes it. */
    private fun pairOf(value: Any?): Pair<Any?, Any?>? = when {
        value is List<*>       -> if (value.size == 2) value[0] to value[1] else null
        value is Array<*>      -> if (value.size == 2) value[0] to value[1] else null
        value is NeonValue && value.isArray ->
            if (value.arraySize == 2L) value.getMember("0") to value.getMember("1") else null
        else -> null
    }
}
