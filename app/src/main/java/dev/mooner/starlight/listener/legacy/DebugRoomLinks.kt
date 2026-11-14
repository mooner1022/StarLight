/*
 * DebugRoomLinks.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.legacy

import dev.mooner.neonjs.NeonValue
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.project.ProjectID
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * The open debug room of each project, so that `Api.replyRoom` and the like can write into it.
 *
 * The debug room screen registers a [Link] while it is open and removes it when it closes. The debug room is never one
 * of the chat sessions, so the room APIs ask here first.
 */
internal object DebugRoomLinks {

    /**
     * A debug room that is open: its name, its ID if it has a numeric one, and how to show a bot message in it.
     */
    class Link(
        val roomName: String,
        val roomId: BigInteger?,
        val send: (message: String) -> Unit,
    )

    private val links: MutableMap<ProjectID, Link> = ConcurrentHashMap()

    /** Registers [link] as the open debug room of the project [projectId], replacing an earlier one. */
    fun register(projectId: ProjectID, link: Link) {
        links[projectId] = link
    }

    /** Removes the project's debug room, but only if [link] is still the registered one. */
    fun unregister(projectId: ProjectID, link: Link) {
        links.remove(projectId, link)
    }

    /** Whether the project has an open debug room. */
    fun isOpen(projectId: ProjectID): Boolean =
        links.containsKey(projectId)

    /**
     * Whether [room] names or numbers the project's open debug room: a string equal to its name, a whole number or
     * `BigInt` equal to its ID, or the debug room's own `ChatRoom`.
     */
    fun matches(projectId: ProjectID, room: Any?): Boolean {
        val link = links[projectId] ?: return false
        return matches(link, room)
    }

    /**
     * Shows [text] in the project's open debug room when [room] names or numbers it.
     *
     * @return `true` when the message was shown there.
     */
    fun send(projectId: ProjectID, room: Any?, text: String): Boolean {
        val link = links[projectId] ?: return false
        if (!matches(link, room))
            return false
        link.send(text)
        return true
    }

    private fun matches(link: Link, room: Any?): Boolean {
        if (room is ChatRoom)
            return room.isDebugRoom && room.name == link.roomName
        if (room is CharSequence || (room is NeonValue && room.isString))
            return ScriptArgs.stringify(room) == link.roomName
        val id = ScriptArgs.integral(room) ?: return false
        return link.roomId != null && link.roomId == id
    }
}
