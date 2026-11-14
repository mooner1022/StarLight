/*
 * SessionRegistry.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.chat

import android.app.Notification
import dev.mooner.starlight.plugincore.chat.ChatRoom
import java.math.BigInteger

/**
 * A room as the messenger-bot sessions know it: its name and its numeric ID, [BigInteger.ZERO] for a room without one
 * (a room a script bound with `SessionManager.bindSession`, or one whose ID is not a number).
 *
 * Two keys are the same room when both name and ID are equal.
 */
data class RoomKey(
    /** The room's name, as the messenger shows it. */
    val name: String,
    /** The room's ID as a BigInt; `0` when it has no numeric one. */
    val id: BigInteger = BigInteger.ZERO,
) {

    override fun toString(): String = "Room(name=$name, id=$id)"

    companion object {

        /** The key of [room]: its name, and its ID when that is a whole number, else zero. */
        fun of(room: ChatRoom): RoomKey =
            RoomKey(room.name, idOf(room.id))

        /** [id] as a number: its value when it is a whole number, else [BigInteger.ZERO]. */
        fun idOf(id: String): BigInteger =
            id.trim().toBigIntegerOrNull() ?: BigInteger.ZERO
    }
}

/** An object that stands for exactly one [RoomKey], such as a room object API2 scripts hold. */
interface RoomKeyHolder {
    val roomKey: RoomKey
}

/**
 * What a room can be sent to or marked as read through, in one package.
 *
 * @property reply The notification action that takes a reply, with its remote input.
 * @property read The notification action that marks the room as read.
 * @property room The room these belong to: the one a message came from, or a stand-in over the two actions.
 */
class RoomSession(
    val packageName: String,
    val key: RoomKey,
    val room: ChatRoom,
    val reply: Notification.Action?,
    val read: Notification.Action?,
)

/**
 * The rooms StarLight can reply to, of every package: the registry sessions are kept in.
 *
 * Rooms are kept per package, and the latest of each room across packages, with lookups by name (the room last seen
 * with that name), by ID (the room last seen with that ID) and in the order rooms were first seen. Nothing is removed
 * when a notification goes away. All members are safe to call from any thread.
 */
class SessionRegistry {

    private val lock = Any()
    private val byPackage = LinkedHashMap<String, LinkedHashMap<RoomKey, RoomSession>>()
    private val latest = HashMap<RoomKey, RoomSession>()
    private val nameToKey = LinkedHashMap<String, RoomKey>()
    private val idToKey = HashMap<BigInteger, RoomKey>()
    private val order = LinkedHashSet<RoomKey>()
    private val rawIds = HashMap<String, ChatRoom>()

    /** Registers [session] for its package, replacing that room's earlier one; it becomes the room's latest. */
    fun put(session: RoomSession) {
        synchronized(lock) {
            byPackage.getOrPut(session.packageName) { LinkedHashMap() }[session.key] = session
            latest[session.key] = session
            nameToKey[session.key.name] = session.key
            idToKey[session.key.id] = session.key
            order.add(session.key)
            if (session.room !is SessionChatRoom)
                rawIds[session.room.id] = session.room
        }
    }

    /**
     * Registers [session] unless its package already has the room. A room known by the same name or ID keeps the
     * name or ID.
     *
     * @return Whether it was registered.
     */
    fun putIfAbsent(session: RoomSession): Boolean {
        synchronized(lock) {
            if (byPackage[session.packageName]?.containsKey(session.key) == true)
                return false
            byPackage.getOrPut(session.packageName) { LinkedHashMap() }[session.key] = session
            latest.putIfAbsent(session.key, session)
            nameToKey.putIfAbsent(session.key.name, session.key)
            idToKey.putIfAbsent(session.key.id, session.key)
            order.add(session.key)
            if (session.room !is SessionChatRoom)
                rawIds.putIfAbsent(session.room.id, session.room)
            return true
        }
    }

    /** The session of [key] in [packageName], or its latest one in any package when [packageName] is `null`. */
    fun session(key: RoomKey, packageName: String? = null): RoomSession? = synchronized(lock) {
        if (packageName == null) latest[key] else byPackage[packageName]?.get(key)
    }

    /** The sessions of [key] in every package, in the order the packages were first seen. */
    fun sessions(key: RoomKey): List<RoomSession> = synchronized(lock) {
        byPackage.values.mapNotNull { it[key] }
    }

    /** Whether [key] has a session, in [packageName] or, when it is `null`, in any package. */
    fun hasSession(key: RoomKey, packageName: String? = null): Boolean =
        session(key, packageName) != null

    /** The room last seen with [name], in any package. */
    fun byName(name: String): RoomKey? = synchronized(lock) { nameToKey[name] }

    /** The room last seen with [id], in any package. */
    fun byId(id: BigInteger): RoomKey? = synchronized(lock) { idToKey[id] }

    /** Whether a room has been seen with [name]. */
    fun hasName(name: String): Boolean = synchronized(lock) { name in nameToKey }

    /** The names rooms have been seen with. */
    fun names(): Set<String> = synchronized(lock) { LinkedHashSet(nameToKey.keys) }

    /** Every room, in the order they were first seen. */
    fun rooms(): List<RoomKey> = synchronized(lock) { order.toList() }

    /** The rooms of [packageName], in the order they were first seen there; empty for an unknown package. */
    fun roomsOf(packageName: String): List<RoomKey> = synchronized(lock) {
        byPackage[packageName]?.keys?.toList().orEmpty()
    }

    /** The package of the latest session of [key], or `null` when it has none. */
    fun packageOf(key: RoomKey): String? = synchronized(lock) { latest[key]?.packageName }

    /** The room objects of every room, in the order they were first seen: each room's latest. */
    fun chatRooms(): List<ChatRoom> = synchronized(lock) {
        order.mapNotNull { latest[it]?.room }
    }

    /** The room object of [key]'s latest session. */
    fun chatRoom(key: RoomKey): ChatRoom? = synchronized(lock) { latest[key]?.room }

    /** The room a message came from whose [ChatRoom.id] is [id], as the messenger gives it. */
    fun chatRoomByRawId(id: String): ChatRoom? = synchronized(lock) { rawIds[id] }

    /** Whether a room a message came from has the [ChatRoom.id] [id]. */
    fun hasRawId(id: String): Boolean = synchronized(lock) { id in rawIds }
}
