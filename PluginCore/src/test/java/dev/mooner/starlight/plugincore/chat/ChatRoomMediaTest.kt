package dev.mooner.starlight.plugincore.chat

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture

/** What `room.sendMedia` does for any room: hand the files to StarLight's sender, never throw at the script. */
class ChatRoomMediaTest {

    private val room = object : ChatRoom {
        override val id = "1"
        override val name = "Friends"
        override val isGroupChat = true
        override val isDebugRoom = false
        override fun send(message: String) = true
        override fun markAsRead() = true
    }

    @After
    fun forgetSender() {
        MediaSender.current = null
    }

    @Test
    fun withoutASenderTheSendFailsQuietly() {
        MediaSender.current = null
        val result = room.sendMedia("https://example.com/a.png").get()
        assertFalse(result.ok)
        assertNotNull(result.error)
        assertEquals("Friends", result.room)
    }

    @Test
    fun theSenderGetsTheRoomFilesAndOptions() {
        var got: Triple<ChatRoom, Any?, Any?>? = null
        MediaSender.current = MediaSender { room, files, options ->
            got = Triple(room, files, options)
            CompletableFuture.completedFuture(MediaSendResult(true, room.name, 1, 0, null, emptyList(), null))
        }
        val options = mapOf("fileName" to "cat")
        assertTrue(room.sendMedia("a.png", options).get().ok)
        assertSame(room, got!!.first)
        assertEquals("a.png", got!!.second)
        assertSame(options, got!!.third)
        // Without options, the sender gets null for them.
        room.sendMedia("b.png").get()
        assertEquals(null, got!!.third)
    }
}
