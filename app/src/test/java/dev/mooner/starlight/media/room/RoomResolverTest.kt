package dev.mooner.starlight.media.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigInteger

class RoomResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000L

    private fun registry() = RoomRegistry(tmp.root.resolve("rooms.json")) { now }

    /** Stands in for StarLight's ChatRoom. */
    private class FakeRoom(val id: String, val name: String, val isDebugRoom: Boolean)

    private fun room(id: String, name: String, debug: Boolean = false) =
        FakeRoom(id, name, debug)

    private fun resolver(registry: RoomRegistry = registry()) =
        RoomResolver(registry) { value -> (value as? FakeRoom)?.let { ChatRoomRef(it.id, it.name, it.isDebugRoom) } }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (_: InvalidRoomException) {
            return
        }
        throw AssertionError("Expected InvalidRoomException")
    }

    @Test
    fun channelIds() {
        val resolver = resolver()
        assertEquals(18478608147447180L, resolver.resolve("18478608147447180").channelId)
        assertEquals(18478608147447180L, resolver.resolve(BigInteger("18478608147447180")).channelId)
        assertEquals(123456L, resolver.resolve(123456.0).channelId)
        assertEquals(123456L, resolver.resolve(123456L).channelId)
        // Above 2^53 a JS number has lost digits already.
        assertRejected { resolver.resolve(18478608147447180.0) }
        assertRejected { resolver.resolve(1.5) }
        assertRejected { resolver.resolve(null) }
        assertRejected { resolver.resolve("  ") }
    }

    @Test
    fun chatRooms() {
        val resolver = resolver()
        val target = resolver.resolve(room("4242", "Friends"))
        assertEquals(4242L, target.channelId)
        assertEquals("Friends (4242)", target.label)
        assertRejected { resolver.resolve(room("4242", "Debug", debug = true)) }
        assertRejected { resolver.resolve(room("not-a-number", "Other")) }
    }

    @Test
    fun namesOfRoomsMessagesCameFrom() {
        val registry = registry()
        val resolver = resolver(registry)
        assertRejected { resolver.resolve("Friends") }
        registry.record("111", "Friends", true)
        now += 10
        registry.record("222", "Friends", true)
        registry.record("333", "Family", true)
        assertEquals("the room seen last wins", 222L, resolver.resolve("Friends").channelId)
        assertEquals(333L, resolver.resolve("family").channelId)
        assertEquals("Family (333)", resolver.resolve("333").label)
    }

    @Test
    fun roomsAreKept() {
        registry().apply {
            record("111", "Friends", true)
            record("222", "Alice", false)
            save()
        }
        val reloaded = registry().apply { load() }
        assertEquals(2, reloaded.size)
        assertEquals("111", reloaded.findByName("Friends")?.id)
        reloaded.clear()
        assertNull(registry().apply { load() }.findByName("Friends"))
    }

    @Test
    fun usersOfRooms() {
        val registry = registry()
        val resolver = resolver(registry)
        // StarLight fires the notification before the parsed message.
        registry.recordUser("111", 95)
        registry.record("111", "Bots", true)
        registry.record("222", "Friends", true)
        assertEquals(95, resolver.resolve("Bots").userId)
        assertEquals(95, resolver.resolve(room("111", "Bots")).userId)
        assertEquals(95, resolver.resolve("111").userId)
        assertNull("no notification seen yet", resolver.resolve("Friends").userId)
        registry.recordUser("222", 0)
        assertEquals(0, resolver.resolve("Friends").userId)
        // Kept across restarts.
        registry.save()
        val reloaded = registry().apply { load() }
        assertEquals(95, reloaded.get("111")?.userId)
        assertEquals(0, reloaded.userOf("222"))
    }

    @Test
    fun onlyChannelIdsAreRecorded() {
        val registry = registry()
        registry.record("debug_room", "Debug", false)
        registry.record("", "Empty", false)
        assertEquals(0, registry.size)
        assertTrue(RoomRegistry.isChannelId("18478608147447180"))
    }
}
