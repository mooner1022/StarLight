package dev.mooner.starlight.media.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Matches as Android 16 reports them with KakaoTalk 26.9.1 installed for the owner (user 0) and in the Dual Messenger
 * clone profile (user 95): every activity once per user.
 */
class KakaoShareTest {

    private val memo = "com.kakao.talk.activity.MemoChatConnectActivity"

    @Test
    fun theShareReceiverOfBothUsersIsOneActivity() {
        assertEquals(KakaoShare.SHARE_ACTIVITY, KakaoShare.chooseShareActivity(listOf(KakaoShare.SHARE_ACTIVITY, KakaoShare.SHARE_ACTIVITY)))
    }

    @Test
    fun textAlsoMatchesTheMemoChat() {
        assertEquals(KakaoShare.SHARE_ACTIVITY, KakaoShare.chooseShareActivity(listOf(memo, KakaoShare.SHARE_ACTIVITY, memo)))
    }

    @Test
    fun aRenamedReceiverIsStillFound() {
        assertEquals("com.kakao.talk.activity.NewShareActivity", KakaoShare.chooseShareActivity(listOf(memo, "com.kakao.talk.activity.NewShareActivity")))
        assertEquals(memo, KakaoShare.chooseShareActivity(listOf(memo)))
        assertNull(KakaoShare.chooseShareActivity(emptyList()))
    }

    @Test
    fun theRoomsUserWins() {
        assertEquals(95, KakaoShare.targetUser(roomUserId = 95, kakaoUsers = listOf(0, 95), myUserId = 0))
        assertEquals(0, KakaoShare.targetUser(roomUserId = 0, kakaoUsers = listOf(0, 95), myUserId = 0))
    }

    @Test
    fun anUnknownRoomGoesToTheOnlyKakaoTalk() {
        assertEquals(0, KakaoShare.targetUser(roomUserId = null, kakaoUsers = listOf(0, 0), myUserId = 0))
        assertEquals(95, KakaoShare.targetUser(roomUserId = null, kakaoUsers = listOf(95), myUserId = 0))
        // Nothing found (the query failed): StarLight's own user, as before.
        assertEquals(0, KakaoShare.targetUser(roomUserId = null, kakaoUsers = emptyList(), myUserId = 0))
    }

    @Test
    fun anUnknownRoomWithTwoKakaoTalksIsAsked() {
        assertNull(KakaoShare.targetUser(roomUserId = null, kakaoUsers = listOf(0, 95), myUserId = 0))
    }
}
