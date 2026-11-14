package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.listener.legacy.DebugRoomLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.UUID

class LegacyDebugRoomLinksTest {

    private val project = UUID.randomUUID()

    private fun open(sent: MutableList<String>): DebugRoomLinks.Link =
        DebugRoomLinks.Link("debug", BigInteger.valueOf(42), { sent += it }).also {
            DebugRoomLinks.register(project, it)
        }

    @Test
    fun aNameOrIdOfTheOpenRoomWritesToIt() {
        val sent = mutableListOf<String>()
        val link = open(sent)
        assertTrue(DebugRoomLinks.send(project, "debug", "a"))
        assertTrue(DebugRoomLinks.send(project, 42, "b"))
        assertTrue(DebugRoomLinks.send(project, BigInteger.valueOf(42), "c"))
        assertEquals(listOf("a", "b", "c"), sent)
        DebugRoomLinks.unregister(project, link)
    }

    @Test
    fun otherRoomsAndNonRoomsDoNotMatch() {
        val sent = mutableListOf<String>()
        val link = open(sent)
        assertFalse(DebugRoomLinks.send(project, "other", "a"))
        assertFalse(DebugRoomLinks.send(project, "42", "a"))
        assertFalse(DebugRoomLinks.send(project, 7, "a"))
        assertFalse(DebugRoomLinks.send(project, null, "a"))
        assertTrue(sent.isEmpty())
        DebugRoomLinks.unregister(project, link)
    }

    @Test
    fun aClosedRoomMatchesNothing() {
        val link = open(mutableListOf())
        DebugRoomLinks.unregister(project, link)
        assertFalse(DebugRoomLinks.isOpen(project))
        assertFalse(DebugRoomLinks.send(project, "debug", "a"))
    }
}
