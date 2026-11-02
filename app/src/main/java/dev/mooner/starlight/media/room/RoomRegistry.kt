/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.room

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * KakaoTalk chat rooms seen in received messages, so scripts can send to a room by its name.
 *
 * A room's ID (the notification tag) is the channel ID KakaoTalk's share screen takes. Rooms are kept across restarts
 * in [file], written at most every [SAVE_INTERVAL_MILLIS] and on [save].
 */
class RoomRegistry(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @Serializable
    data class Room(
        val id: String,
        val name: String,
        val isGroupChat: Boolean,
        val lastSeen: Long,
        /**
         * The Android user whose KakaoTalk the room's messages came to: 0 for the owner, another ID for a clone
         * such as Samsung's Dual Messenger. Null until a notification from the room is seen.
         */
        val userId: Int? = null,
    )

    private val rooms: MutableMap<String, Room> = ConcurrentHashMap()

    /** Users of rooms whose notification came before the message was parsed. */
    private val userOfRoom: MutableMap<String, Int> = ConcurrentHashMap()
    private val mVersion = MutableStateFlow(0L)

    @Volatile
    private var isDirty = false
    @Volatile
    private var lastSaved = 0L

    /** Changes whenever a room is added, renamed or removed. */
    val version: StateFlow<Long> = mVersion.asStateFlow()

    val size: Int
        get() = rooms.size

    fun load() {
        val loaded = runCatching { json.decodeFromString(ListSerializer(Room.serializer()), file.readText()) }
            .getOrDefault(emptyList())
        rooms.clear()
        for (room in loaded)
            rooms[room.id] = room
        mVersion.value++
    }

    /** Records a room a message came from. */
    fun record(id: String, name: String, isGroupChat: Boolean) {
        if (!isChannelId(id) || name.isBlank())
            return
        val now = clock()
        val userId = userOfRoom[id] ?: rooms[id]?.userId
        val previous = rooms.put(id, Room(id, name, isGroupChat, now, userId))
        isDirty = true
        if (previous == null || previous.name != name || previous.userId != userId) {
            trim()
            mVersion.value++
        }
        if (now - lastSaved >= SAVE_INTERVAL_MILLIS)
            save()
    }

    /** Records which Android user's KakaoTalk got a notification from the room [id]. */
    fun recordUser(id: String, userId: Int) {
        if (!isChannelId(id))
            return
        if (userOfRoom.put(id, userId) == userId)
            return
        val room = rooms[id] ?: return
        if (room.userId != userId) {
            rooms[id] = room.copy(userId = userId)
            isDirty = true
            mVersion.value++
        }
    }

    /** The room last seen with [name]; names are compared exactly, then ignoring case. */
    fun findByName(name: String): Room? {
        val name = name.trim()
        val candidates = rooms.values.filter { it.name == name }
            .ifEmpty { rooms.values.filter { it.name.equals(name, ignoreCase = true) } }
        return candidates.maxByOrNull { it.lastSeen }
    }

    fun get(id: String): Room? =
        rooms[id]

    /** The Android user whose KakaoTalk has the room [id], if a notification from it was seen. */
    fun userOf(id: String): Int? =
        userOfRoom[id] ?: rooms[id]?.userId

    /** Rooms, last seen first. */
    fun list(): List<Room> =
        rooms.values.sortedByDescending { it.lastSeen }

    fun clear() {
        rooms.clear()
        isDirty = true
        mVersion.value++
        save()
    }

    @Synchronized
    fun save() {
        if (!isDirty)
            return
        isDirty = false
        lastSaved = clock()
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(json.encodeToString(ListSerializer(Room.serializer()), list()))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }.onFailure { isDirty = true }
    }

    private fun trim() {
        if (rooms.size <= MAX_ROOMS)
            return
        rooms.values.sortedBy { it.lastSeen }
            .take(rooms.size - MAX_ROOMS)
            .forEach { rooms.remove(it.id) }
    }

    companion object {
        const val MAX_ROOMS = 1000
        private const val SAVE_INTERVAL_MILLIS = 60_000L

        private val json = Json { ignoreUnknownKeys = true }

        fun isChannelId(text: String): Boolean =
            text.isNotEmpty() && text.length <= 19 && text.all(Char::isDigit) && text.toLongOrNull() != null
    }
}
