package dev.mooner.starlight.agent.tools

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How long a look at the debug room waits, and that replies coming after it are kept. */
class DebugWaitTest {

    private fun reply(text: String) = DebugMessenger.Event(1, "hi", 0L, System.currentTimeMillis(), text = text)

    /** Sends [texts] into a channel, each after the delay before it. */
    private fun kotlinx.coroutines.CoroutineScope.bot(channel: Channel<DebugMessenger.Event>, vararg texts: Pair<Long, String>) =
        launch {
            for ((wait, text) in texts) {
                delay(wait)
                channel.send(reply(text))
            }
        }

    @Test
    fun waitSecondsAndExpectRepliesPickTheMode() {
        DebugMessenger.Wait.of(null, null).let {
            assertTrue(it.untilQuiet)
            assertEquals(DebugMessenger.DEFAULT_WAIT_SECONDS * 1000L, it.maxMillis)
        }
        DebugMessenger.Wait.of(12, null).let {
            assertTrue(!it.untilQuiet)
            assertNull(it.expect)
            assertEquals(12_000L, it.maxMillis)
        }
        DebugMessenger.Wait.of(999, null).let { assertEquals(DebugMessenger.MAX_WAIT_SECONDS * 1000L, it.maxMillis) }
        DebugMessenger.Wait.of(null, 2).let {
            assertEquals(2, it.expect)
            assertEquals(DebugMessenger.EXPECT_WAIT_SECONDS * 1000L, it.maxMillis)
        }
    }

    @Test
    fun byDefaultStopsOnceTheBotIsQuiet() = runBlocking {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        val bot = bot(channel, 0L to "working on it", 20L to "still here", 400L to "done")
        val taken = DebugMessenger.collect(channel, DebugMessenger.Wait(3_000L, untilQuiet = true), quietMillis = 150L)
        assertEquals(listOf("working on it", "still here"), taken.map { it.text })
        bot.join()
        // The late one isn't lost: the next look gets it.
        assertEquals(listOf("done"), DebugMessenger.drain(channel).map { it.text })
    }

    @Test
    fun waitSecondsCollectsForTheWholeTime() = runBlocking {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        bot(channel, 0L to "working on it", 250L to "done")
        val started = System.currentTimeMillis()
        val taken = DebugMessenger.collect(channel, DebugMessenger.Wait(500L), quietMillis = 50L)
        assertEquals(listOf("working on it", "done"), taken.map { it.text })
        assertTrue(System.currentTimeMillis() - started >= 500L)
    }

    @Test
    fun expectRepliesReturnsAsSoonAsTheyCame() = runBlocking {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        bot(channel, 0L to "working on it", 200L to "done")
        val started = System.currentTimeMillis()
        val taken = DebugMessenger.collect(channel, DebugMessenger.Wait(5_000L, expect = 2))
        assertEquals(listOf("working on it", "done"), taken.map { it.text })
        assertTrue(System.currentTimeMillis() - started < 2_000L)
    }

    @Test
    fun repliesTakenBeforeCountTowardsExpectReplies() = runBlocking {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        val started = System.currentTimeMillis()
        val taken = DebugMessenger.collect(channel, DebugMessenger.Wait(5_000L, expect = 1), before = listOf(reply("early")))
        assertEquals(listOf("early"), taken.map { it.text })
        assertTrue(System.currentTimeMillis() - started < 1_000L)
    }

    @Test
    fun anErrorEndsTheWait() = runBlocking {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        launch {
            delay(50L)
            channel.send(DebugMessenger.Event(1, "hi", 0L, System.currentTimeMillis(), error = IllegalStateException("boom")))
        }
        val started = System.currentTimeMillis()
        val taken = DebugMessenger.collect(channel, DebugMessenger.Wait(5_000L))
        assertEquals("boom", taken.single().error?.message)
        assertTrue(System.currentTimeMillis() - started < 2_000L)
    }

    @Test
    fun anEarlierMessagesReplyNeitherCountsNorEndsTheWait() = runBlocking {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        launch {
            delay(20L)
            channel.send(DebugMessenger.Event(1, "earlier", 0L, System.currentTimeMillis(), text = "late answer"))
            delay(300L)
            channel.send(DebugMessenger.Event(2, "now", 0L, System.currentTimeMillis(), text = "this answer"))
        }
        val taken = DebugMessenger.collect(channel, DebugMessenger.Wait(3_000L, untilQuiet = true),
            ours = { it.messageId == 2 }, quietMillis = 100L)
        assertEquals(listOf("late answer", "this answer"), taken.map { it.text })
    }

    @Test
    fun oldRepliesAreDropped() {
        val channel = Channel<DebugMessenger.Event>(Channel.UNLIMITED)
        val now = System.currentTimeMillis()
        channel.trySend(DebugMessenger.Event(1, "hi", 0L, now - 60 * 60_000L, text = "an hour ago"))
        channel.trySend(DebugMessenger.Event(1, "hi", 0L, now - 1_000L, text = "just now"))
        assertEquals(listOf("just now"), DebugMessenger.drain(channel, now).map { it.text })
    }
}
