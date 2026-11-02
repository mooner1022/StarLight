package dev.mooner.starlight.media.room

import dev.mooner.starlight.plugincore.chat.ChatRoom
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Which rooms get the prepared files themselves instead of KakaoTalk's share screen. */
class StarLightRoomsTest {

    private fun debugRoom(showsFiles: Boolean) = DebugChatRoom(
        id = "debug",
        name = "Test room",
        isGroupChat = false,
        onSend = {},
        onMarkAsRead = {},
        onSendMedia = if (showsFiles) ({ _ -> }) else null,
    )

    @Test
    fun theDebugRoomShowsFilesItself() {
        val room = debugRoom(showsFiles = true)
        val target = directRoomOf(room)!!
        assertSame(room, target.room)
        assertEquals("Test room (debug)", target.label)
    }

    @Test
    fun otherRoomsGoThroughKakaoTalk() {
        // A debug room made without a way to show files, as an older plugin makes it, can't take them.
        assertNull(directRoomOf(debugRoom(showsFiles = false)))
        assertNull(directRoomOf("Test room"))
        assertNull(directRoomOf(null))
        val kakao = object : ChatRoom {
            override val id = "18478608147447180"
            override val name = "Friends"
            override val isGroupChat = true
            override val isDebugRoom = false
            override fun send(message: String) = true
            override fun markAsRead() = true
        }
        assertNull(directRoomOf(kakao))
        assertEquals(ChatRoomRef("18478608147447180", "Friends", false), starLightChatRoomOf(kakao))
    }
}
