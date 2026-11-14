package dev.mooner.starlight.listener.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A notification KakaoTalk takes away right after StarLight had the room read is that read, not a deleted message. */
class OwnReadsTest {

    private var now = 1_000L
    private val reads = OwnReads { now }

    @Test
    fun `a read explains the room's removal for its time only`() {
        reads.note("123", OwnReads.READ_MILLIS)
        now += 200
        assertTrue(reads.explains("123"))
        now += OwnReads.READ_MILLIS
        assertFalse(reads.explains("123"))
    }

    @Test
    fun `a read explains its own room only`() {
        reads.note("123", OwnReads.READ_MILLIS)
        assertFalse(reads.explains("456"))
        assertFalse(reads.explains(null))
    }

    @Test
    fun `a read of a room that can't be named explains every room`() {
        reads.note(null, OwnReads.READ_MILLIS)
        assertTrue(reads.explains("123"))
        assertTrue(reads.explains(null))
        reads.note("0", OwnReads.READ_MILLIS)
        now += OwnReads.READ_MILLIS + 1
        assertFalse(reads.explains("123"))
    }

    @Test
    fun `a share explains longer, and a later short read doesn't cut it short`() {
        reads.note("123", OwnReads.SHARE_MILLIS)
        reads.note("123", OwnReads.READ_MILLIS)
        now += OwnReads.READ_MILLIS + 1
        assertTrue(reads.explains("123"))
        now += OwnReads.SHARE_MILLIS
        assertFalse(reads.explains("123"))
    }

    @Test
    fun `nothing noted explains nothing`() {
        assertFalse(reads.explains("123"))
        assertFalse(reads.explains(null))
    }
}
