/*
 * StarLightRooms.kt created by Minki Moon(mooner1022) on 26. 11. 2. 오후 9:37
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.room

import dev.mooner.starlight.media.script.ScriptValues
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import dev.mooner.starlight.plugincore.chat.Message

/** The chat room of StarLight's `msg.room` or `msg`. */
fun starLightChatRoomOf(value: Any): ChatRoomRef? {
    val room = chatRoomOf(value) ?: return null
    return ChatRoomRef(room.id, room.name, room.isDebugRoom)
}

/** The debug room [value] names, as `msg` or `msg.room`, if it shows files itself; null for anything else. */
fun directRoomOf(value: Any?): DirectRoom? {
    val room = ScriptValues.unwrap(value)?.let(::chatRoomOf) as? DebugChatRoom ?: return null
    return if (room.onSendMedia != null) DirectRoom(room) else null
}

private fun chatRoomOf(value: Any): ChatRoom? =
    when (value) {
        is ChatRoom -> value
        is Message  -> value.room
        else        -> null
    }
