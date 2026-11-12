package dev.mooner.starlight.script.chat

import android.graphics.Bitmap
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ChatWaitsTest {

    private val project = UUID.randomUUID()

    private val rooms = mutableMapOf<String, DebugChatRoom>()

    private fun message(text: String, sender: String = "kim", senderId: String? = null, room: String = "room1") =
        Message(
            message = text,
            image = null,
            sender = ChatSender(sender, senderId, BITMAP),
            room = rooms.getOrPut(room) { DebugChatRoom(id = "id-$room", name = room, isGroupChat = true, onSend = {}, onMarkAsRead = {}) },
            packageName = "com.kakao.talk",
            hasMention = false,
            chatLogId = -1,
        )

    private val answers = Collections.synchronizedList(mutableListOf<String?>())
    private var released = 0

    private fun wait(target: Message, timeout: Long = 60_000) =
        ChatWaits.start(project, ChatWaits.Target(target.room.id, target.room.name, ChatWaits.senderOf(target)), timeout,
            onDone = { answers += it?.message }, onRelease = { released++ })

    private fun taken(message: Message) =
        ChatWaits.claim(project, message) != ChatWaits.Claim.PASSED

    private fun waitInRoom(target: Message, timeout: Long = 60_000) =
        ChatWaits.start(project, ChatWaits.Target(target.room.id, target.room.name, null), timeout,
            onDone = { answers += it?.message }, onRelease = { released++ })

    @Test
    fun `the next message from the sender answers, and all its calls are held back`() {
        val question = message("/join")
        assertFalse(taken(question))
        wait(question)
        // The message that started the wait gets its other calls, as its API2 listener, as usual.
        assertFalse(taken(question))
        assertFalse(taken(message("hi", sender = "lee")))
        assertFalse(taken(message("hi", room = "room2")))
        val answer = message("moon")
        assertEquals(ChatWaits.Claim.ANSWERED, ChatWaits.claim(project, answer))
        assertEquals(ChatWaits.Claim.HELD, ChatWaits.claim(project, answer))
        assertEquals(listOf("moon"), answers)
        // Answered once: the next message goes to the event functions.
        assertFalse(taken(message("again")))
    }

    @Test
    fun `a message decided before a wait started isn't taken by it, but an equal one after it is`() {
        val first = message("yes")
        assertFalse(taken(first))
        waitInRoom(first)
        assertFalse(taken(first))
        // The same text from the same sender in the debug room, where every message has the same log ID: its own.
        val second = message("yes")
        assertEquals(first, second)
        assertTrue(taken(second))
        assertEquals(listOf("yes"), answers)
    }

    @Test
    fun `a room's wait takes anyone, and a sender's ID tells senders apart`() {
        val question = message("/vote", senderId = "1")
        waitInRoom(question)
        wait(question)
        assertFalse(taken(message("x", room = "room2")))
        // From someone else with the same name: the room's wait takes it, the sender's doesn't.
        assertTrue(taken(message("other", sender = "kim", senderId = "2")))
        assertEquals(listOf("other"), answers)
        assertTrue(taken(message("mine", senderId = "1")))
        assertEquals(listOf("other", "mine"), answers)
    }

    @Test
    fun `one message answers every wait it matches`() {
        val question = message("/start")
        waitInRoom(question)
        wait(question)
        assertTrue(taken(message("ok")))
        assertEquals(listOf("ok", "ok"), answers)
    }

    @Test
    fun `a new wait for the same target ends the earlier one with null`() {
        val question = message("/join")
        val earlier = wait(question)
        val later = wait(question)
        assertTrue(earlier !== later)
        assertEquals(listOf<String?>(null), answers)
        assertTrue(taken(message("moon")))
        assertEquals(listOf(null, "moon"), answers)
    }

    @Test
    fun `a wait with no answer in time ends with null`() {
        val done = CountDownLatch(1)
        val question = message("/join")
        ChatWaits.start(project, ChatWaits.Target(question.room.id, question.room.name, null), 50,
            onDone = { answers += it?.message; done.countDown() }, onRelease = {})
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf<String?>(null), answers)
        assertFalse(taken(message("late")))
    }

    @Test
    fun `detached waits answer nothing, don't time out, and are released when told`() {
        val question = message("/join")
        wait(question, timeout = 50)
        val detached = ChatWaits.detach(project)
        assertEquals(1, detached.size)
        assertFalse(taken(message("moon")))
        Thread.sleep(200)
        assertTrue(answers.isEmpty())
        ChatWaits.release(detached)
        assertEquals(1, released)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun `a target without a room ID matches the room by name`() {
        val target = ChatWaits.Target(null, "room1", "name:kim")
        assertTrue(target.accepts(message("a")))
        assertFalse(target.accepts(message("a", room = "room2")))
        assertFalse(target.accepts(message("a", sender = "lee")))
    }

    private companion object {
        /** A Bitmap without Android: messages only carry it here. */
        val BITMAP: Bitmap = run {
            // Not on Android's compile class path, but there when the tests run on the JVM.
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Bitmap::class.java) as Bitmap
        }
    }
}
