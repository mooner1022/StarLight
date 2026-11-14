package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.listener.chat.ActionChoice
import dev.mooner.starlight.listener.chat.RoomKey
import dev.mooner.starlight.listener.chat.RoomKeyHolder
import dev.mooner.starlight.listener.chat.RoomResolver
import dev.mooner.starlight.listener.chat.RoomSession
import dev.mooner.starlight.listener.chat.SessionRegistry
import dev.mooner.starlight.plugincore.chat.ChatRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class ChatSessionsTest {

    private class Room(override val id: String, override val name: String) : ChatRoom {
        override val isGroupChat = false
        override val isDebugRoom = false
        override fun send(message: String) = true
        override fun markAsRead() = true
    }

    private fun session(pkg: String, name: String, id: Long): RoomSession {
        val room = Room(id.toString(), name)
        return RoomSession(pkg, RoomKey(name, BigInteger.valueOf(id)), room, null, null)
    }

    private fun registry() = SessionRegistry().apply {
        put(session("a", "one", 11))
        put(session("b", "two", 22))
        put(session("b", "one", 33))
    }

    @Test
    fun lookupsFollowTheLatestRoomSeen() {
        val r = registry()
        assertEquals(RoomKey("one", BigInteger.valueOf(33)), r.byName("one"))
        assertEquals(RoomKey("one", BigInteger.valueOf(11)), r.byId(BigInteger.valueOf(11)))
        assertEquals(listOf("one", "two", "one"), r.rooms().map { it.name })
        assertEquals(listOf("two", "one"), r.roomsOf("b").map { it.name })
        assertEquals("b", r.packageOf(RoomKey("one", BigInteger.valueOf(33))))
        assertTrue(r.hasSession(RoomKey("one", BigInteger.valueOf(11)), "a"))
        assertFalse(r.hasSession(RoomKey("one", BigInteger.valueOf(11)), "b"))
    }

    @Test
    fun putIfAbsentKeepsWhatIsThere() {
        val r = registry()
        assertFalse(r.putIfAbsent(session("a", "one", 11)))
        assertTrue(r.putIfAbsent(session("c", "one", 44)))
        assertEquals(BigInteger.valueOf(33), r.byName("one")!!.id)
        assertEquals(4, r.rooms().size)
    }

    @Test
    fun stringsAreNamesOnly() {
        val r = registry()
        assertEquals(RoomKey("two", BigInteger.valueOf(22)), RoomResolver.resolve(registry = r, room = "two"))
        assertNull(RoomResolver.resolve(registry = r, room = "22"))
        assertNull(RoomResolver.resolve(registry = r, room = "nobody"))
    }

    @Test
    fun wholeNumbersAreIds() {
        val r = registry()
        val two = RoomKey("two", BigInteger.valueOf(22))
        assertEquals(two, RoomResolver.resolve(registry = r, room = 22))
        assertEquals(two, RoomResolver.resolve(registry = r, room = 22L))
        assertEquals(two, RoomResolver.resolve(registry = r, room = 22.0))
        assertEquals(two, RoomResolver.resolve(registry = r, room = BigInteger.valueOf(22)))
        assertNull(RoomResolver.resolve(registry = r, room = 22.5))
        assertNull(RoomResolver.resolve(registry = r, room = 99))
    }

    @Test
    fun pairsAndKeysResolveExactly() {
        val r = registry()
        val key = RoomKey("ghost", BigInteger.TEN)
        assertEquals(key, RoomResolver.resolve(registry = r, room = key))
        assertEquals(key, RoomResolver.resolve(registry = r, room = listOf("ghost", 10.0)))
        assertEquals(key, RoomResolver.resolve(registry = r, room = "ghost" to BigInteger.TEN))
        assertNull(RoomResolver.resolve(registry = r, room = listOf("ghost")))
        val holder = object : RoomKeyHolder { override val roomKey = key }
        assertEquals(key, RoomResolver.resolve(registry = r, room = holder))
        assertEquals(RoomKey("x", BigInteger.valueOf(5)), RoomResolver.resolve(registry = r, room = Room("5", "x")))
        assertEquals(RoomKey("x", BigInteger.ZERO), RoomResolver.resolve(registry = r, room = Room("debug", "x")))
    }

    @Test
    fun otherValuesResolveToNothing() {
        val r = registry()
        assertNull(RoomResolver.resolve(registry = r, room = null))
        assertNull(RoomResolver.resolve(registry = r, room = true))
        assertNull(RoomResolver.resolve(registry = r, room = Any()))
    }

    @Test
    fun packageNarrowsTheRoom() {
        val r = registry()
        assertEquals(RoomKey("two", BigInteger.valueOf(22)), RoomResolver.resolve(registry = r, room = "two", "b"))
        assertNull(RoomResolver.resolve(registry = r, room = "two", "a"))
    }

    private data class Act(val semantic: Int, val input: Boolean, val title: String?)

    private fun reply(vararg a: Act) = ActionChoice.reply(a.toList(), Act::semantic, Act::input, Act::title)
    private fun read(vararg a: Act) = ActionChoice.read(a.toList(), Act::semantic, Act::input, Act::title)

    @Test
    fun replyActionOrder() {
        val plain = Act(0, true, "Answer")
        val semantic = Act(1, true, "x")
        assertEquals(semantic, reply(plain, semantic))
        assertEquals(plain, reply(Act(1, false, "r"), plain))
        assertEquals(Act(0, false, "답장하기"), reply(Act(0, false, "Mute"), Act(0, false, "답장하기")))
        assertNull(reply(Act(0, false, "Mute")))
    }

    @Test
    fun readActionOrder() {
        val semantic = Act(2, false, null)
        assertEquals(semantic, read(Act(0, false, "Mark as READ"), semantic))
        assertEquals(Act(0, false, "Mark as READ"), read(Act(0, true, "read"), Act(0, false, "Mark as READ")))
        assertEquals(Act(0, false, "읽음"), read(Act(0, false, "읽음")))
        assertNull(read(Act(0, true, "read")))
    }
}
