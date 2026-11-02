package dev.mooner.starlight.media.send

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** File events as inotify reports them while KakaoTalk reads shared files through StarLight's FileProvider. */
class ReadTrackerTest {

    private var now = 0L

    private fun tracker(vararg paths: String) = ReadTracker(paths.toList()) { now }

    @Test
    fun doneOnceEveryFileWasOpenedAndClosed() {
        val tracker = tracker("a", "b")
        tracker.onOpen("a")
        tracker.onClose("a")
        assertFalse(tracker.done.isCompleted)
        tracker.onOpen("b")
        now = 1234
        tracker.onClose("b")
        assertTrue(tracker.done.isCompleted)
        assertEquals(1234L, runBlocking { tracker.done.await() })
    }

    @Test
    fun aFileStillOpenIsNotRead() {
        val tracker = tracker("a")
        tracker.onOpen("a")
        tracker.onOpen("a")
        tracker.onClose("a")
        assertFalse("one of two opens is still reading", tracker.done.isCompleted)
        tracker.onClose("a")
        assertTrue(tracker.done.isCompleted)
    }

    @Test
    fun closesWithoutAnOpenAreIgnored() {
        val tracker = tracker("a")
        tracker.onClose("a")
        assertFalse(tracker.done.isCompleted)
        tracker.onClose("other")
        assertFalse(tracker.done.isCompleted)
    }

    @Test
    fun theSameFileSentTwiceIsWatchedOnce() {
        val tracker = tracker("a", "a")
        tracker.onOpen("a")
        tracker.onClose("a")
        assertTrue(tracker.done.isCompleted)
    }

    @Test
    fun nothingToReadIsDone() {
        assertTrue(tracker().done.isCompleted)
    }
}
