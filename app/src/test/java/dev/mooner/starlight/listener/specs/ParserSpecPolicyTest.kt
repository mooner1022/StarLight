package dev.mooner.starlight.listener.specs

import dev.mooner.starlight.plugincore.version.Version
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which spec a device gets, and which of a notification's fields names the room. */
class ParserSpecPolicyTest {

    private val androidQ = 29
    private val androidR = 30

    @Test
    fun `below Android 11 every KakaoTalk reads with the default spec`() {
        assertEquals(ParserSpecPolicy.DEFAULT, ParserSpecPolicy.specIdFor(androidQ, Version(26, 9, 1)))
        assertEquals(ParserSpecPolicy.DEFAULT, ParserSpecPolicy.specIdFor(androidQ, Version(26, 6, 3)))
        assertEquals(ParserSpecPolicy.DEFAULT, ParserSpecPolicy.specIdFor(androidQ, null))
    }

    @Test
    fun `the last KakaoTalk with the room name in the notification keeps the old spec`() {
        assertEquals(ParserSpecPolicy.ANDROID_R, ParserSpecPolicy.specIdFor(androidR, Version(26, 6, 3)))
        assertEquals(ParserSpecPolicy.ANDROID_R, ParserSpecPolicy.specIdFor(androidR, Version(25, 11, 0)))
    }

    @Test
    fun `KakaoTalk reads from the conversation shortcut from 26_7_0 on`() {
        assertEquals(ParserSpecPolicy.CONVERSATION_SHORTCUT, ParserSpecPolicy.specIdFor(androidR, Version(26, 7, 0)))
        assertEquals(ParserSpecPolicy.CONVERSATION_SHORTCUT, ParserSpecPolicy.specIdFor(androidR, Version(26, 9, 1)))
        assertEquals(ParserSpecPolicy.CONVERSATION_SHORTCUT, ParserSpecPolicy.specIdFor(androidR, Version(27, 0, 0)))
    }

    @Test
    fun `an unknown KakaoTalk version gets the newest spec`() {
        assertEquals(ParserSpecPolicy.CONVERSATION_SHORTCUT, ParserSpecPolicy.specIdFor(androidR, null))
    }

    @Test
    fun `the room is the first field that has a name`() {
        assertEquals("room", ParserSpecPolicy.pickRoomName("sender", null, "room", "other"))
        assertEquals("room", ParserSpecPolicy.pickRoomName("sender", "", "  ", "room"))
    }

    @Test
    fun `a notification without a room name is a chat named after its sender`() {
        assertEquals("sender", ParserSpecPolicy.pickRoomName("sender", null, null))
        assertEquals("sender", ParserSpecPolicy.pickRoomName("sender"))
    }
}
