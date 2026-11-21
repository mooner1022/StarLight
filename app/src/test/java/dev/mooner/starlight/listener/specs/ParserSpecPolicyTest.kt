package dev.mooner.starlight.listener.specs

import dev.mooner.starlight.media.room.RoomRegistry
import dev.mooner.starlight.plugincore.version.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

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
        assertEquals(ParserSpecPolicy.CONVERSATION_SHORTCUT, ParserSpecPolicy.specIdFor(androidR, Version(26, 9, 99)))
    }

    @Test
    fun `KakaoTalk tags with the account's ID from 26_10_0 on`() {
        assertEquals(ParserSpecPolicy.ACCOUNT_IN_TAG, ParserSpecPolicy.specIdFor(androidR, Version(26, 10, 0)))
        assertEquals(ParserSpecPolicy.ACCOUNT_IN_TAG, ParserSpecPolicy.specIdFor(androidR, Version(26, 10, 3)))
        assertEquals(ParserSpecPolicy.ACCOUNT_IN_TAG, ParserSpecPolicy.specIdFor(androidR, Version(27, 0, 0)))
        // Below Android 11 the default spec reads both tags.
        assertEquals(ParserSpecPolicy.DEFAULT, ParserSpecPolicy.specIdFor(androidQ, Version(26, 10, 0)))
    }

    @Test
    fun `an unknown KakaoTalk version gets the newest spec`() {
        assertEquals(ParserSpecPolicy.ACCOUNT_IN_TAG, ParserSpecPolicy.specIdFor(androidR, null))
    }

    @Test
    fun `a tag of KakaoTalk 26_10_0 or later gives the room's ID, after the account's`() {
        val roomId = ParserSpecPolicy.roomIdOf("423005562_18452224509561830")
        assertEquals("18452224509561830", roomId)
        // What the room's ID is for: the API2 channelId and the channel KakaoTalk's share screen takes.
        assertTrue(RoomRegistry.isChannelId(roomId))
        assertEquals(BigInteger("18452224509561830"), roomId.toBigIntegerOrNull())
        assertFalse(RoomRegistry.isChannelId("423005562_18452224509561830"))
    }

    @Test
    fun `an older tag, or one that isn't an account's and a room's ID, stays as it is`() {
        assertEquals("18452224509561830", ParserSpecPolicy.roomIdOf("18452224509561830"))
        for (tag in listOf("", "_18452224509561830", "423005562_", "a_1", "1_a", "1_2_3", "423005562-18452224509561830"))
            assertEquals(tag, ParserSpecPolicy.roomIdOf(tag))
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
