package dev.mooner.starlight.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDeduperTest {

    private val deduper = MessageDeduper()

    @Test
    fun aChatLogIdSeenLastIsARepeat() {
        assertFalse(deduper.isRepeat(100, "kakao|1", 10))
        assertTrue(deduper.isRepeat(100, "kakao|1", 11))
        assertFalse(deduper.isRepeat(101, "kakao|1", 12))
        assertEquals(101, deduper.lastChatLogId)
    }

    @Test
    fun messagesWithoutAnIdAreNewWhenPostedAgain() {
        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "other|1", 10))
        // Before, every message after the first one read as the same id and was dropped.
        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "other|1", 20))
        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "other|2", 20))
    }

    @Test
    fun theSameNotificationWithoutAnIdHandedOverTwiceIsARepeat() {
        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "other|1", 10))
        assertTrue(deduper.isRepeat(MessageDeduper.NO_ID, "other|1", 10))
    }

    @Test
    fun aMessageWithoutAnIdDoesNotMatchOneWithAnId() {
        assertFalse(deduper.isRepeat(100, "kakao|1", 10))
        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "kakao|1", 10))
        assertFalse(deduper.isRepeat(100, "kakao|1", 10))
    }

    @Test
    fun afterAResetTheLastMessageCountsAsNew() {
        assertFalse(deduper.isRepeat(100, "kakao|1", 10))
        deduper.reset()
        assertFalse(deduper.isRepeat(100, "kakao|1", 10))

        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "other|1", 10))
        deduper.reset()
        assertFalse(deduper.isRepeat(MessageDeduper.NO_ID, "other|1", 10))
    }
}
