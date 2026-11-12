package dev.mooner.starlight.listener

import dev.mooner.starlight.plugincore.chat.ChatRoom
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRestoreTest {

    private class Room(override val id: String, override val name: String = "room $id") : ChatRoom {
        override val isGroupChat = false
        override val isDebugRoom = false
        override fun send(message: String) = true
        override fun markAsRead() = true
    }

    /** A notification still shown: when it was posted, and what parsing it gives. */
    private class Shown(val time: Long, val room: Room?, val fails: Boolean = false)

    private fun pick(shown: List<Shown>, known: Set<String> = emptySet()): List<ChatRoom> =
        RoomRestore.pick(
            shown = shown,
            postTime = Shown::time,
            parse = { if (it.fails) throw IllegalStateException("spec failed") else it.room },
            isKnown = known::contains,
        )

    @After
    fun tearDown() =
        RoomRestore.reset()

    @Test
    fun `a room with several notifications is taken from the one posted last`() {
        val older = Room("1", "old name")
        val newer = Room("1", "new name")
        val other = Room("2")
        val rooms = pick(listOf(Shown(100, older), Shown(300, newer), Shown(200, other), Shown(50, Room("1", "oldest"))))
        assertEquals(2, rooms.size)
        assertTrue(rooms.single { it.id == "1" } === newer)
        assertTrue(rooms.single { it.id == "2" } === other)
    }

    @Test
    fun `rooms already known are left out`() {
        val rooms = pick(listOf(Shown(100, Room("1")), Shown(100, Room("2"))), known = setOf("1"))
        assertEquals(listOf("2"), rooms.map(ChatRoom::id))
    }

    @Test
    fun `a notification that parses to nothing or throws is passed over`() {
        val rooms = pick(listOf(Shown(100, null), Shown(200, Room("9"), fails = true), Shown(300, Room("3"))))
        assertEquals(listOf("3"), rooms.map(ChatRoom::id))
    }

    @Test
    fun `no notifications give no rooms`() {
        assertTrue(pick(emptyList()).isEmpty())
    }

    @Test
    fun `restoring settles once tried, or ten seconds after the app started`() {
        var now = 1_000_000L
        RoomRestore.clock = { now }
        // Projects may compile before the app says it started.
        assertFalse(RoomRestore.isSettled())
        now += RoomRestore.SETTLE_TIMEOUT_MILLIS * 10
        assertFalse(RoomRestore.isSettled())

        RoomRestore.appStarted()
        now += RoomRestore.SETTLE_TIMEOUT_MILLIS - 1
        assertFalse(RoomRestore.isSettled())
        now += 1
        assertTrue(RoomRestore.isSettled())

        RoomRestore.reset()
        RoomRestore.clock = { now }
        RoomRestore.appStarted()
        assertFalse(RoomRestore.isSettled())
        RoomRestore.markAttempted()
        assertTrue(RoomRestore.isSettled())
    }
}
