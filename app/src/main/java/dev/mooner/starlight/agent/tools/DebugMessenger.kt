package dev.mooner.starlight.agent.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import dev.mooner.starlight.listener.event.ProjectOnMessageEvent
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends a message to a project's `onMessage` the way the debug room does, without showing it there, and collects what
 * the bot sends back to the room.
 *
 * Each project has an inbox that every message's room keeps feeding, so a reply that comes after the tool has
 * returned (a timer, a network request, an `await`) isn't lost: the next look picks it up.
 */
internal object DebugMessenger {

    /** Something the bot did in reply to message [messageId], [trigger]: sent [text], or failed with [error]. */
    class Event(
        val messageId: Int,
        val trigger: String,
        val sentMillis: Long,
        val millis: Long,
        val text: String? = null,
        val error: Throwable? = null,
    ) {
        /** How long after the message this came. */
        val seconds: Double
            get() = (millis - sentMillis) / 1000.0
    }

    /**
     * How long a look waits. Without [expect] and with [untilQuiet], it ends once the bot has been quiet for a moment
     * after replying; without either, it takes the whole [maxMillis]. An error always ends it.
     */
    class Wait(val maxMillis: Long, val expect: Int? = null, val untilQuiet: Boolean = false) {
        companion object {
            /** What each tool's waitSeconds and expectReplies ask for. */
            fun of(waitSeconds: Int?, expectReplies: Int?): Wait {
                val seconds = waitSeconds?.coerceIn(0, MAX_WAIT_SECONDS)
                return when {
                    expectReplies != null -> Wait((seconds ?: EXPECT_WAIT_SECONDS) * 1000L, expect = expectReplies.coerceAtLeast(1))
                    seconds != null       -> Wait(seconds * 1000L)
                    else                  -> Wait(DEFAULT_WAIT_SECONDS * 1000L, untilQuiet = true)
                }
            }
        }
    }

    class Sent(val events: List<Event>, val earlier: List<Event>)

    private class Inbox {
        val events = Channel<Event>(MAX_KEPT, BufferOverflow.DROP_OLDEST)
        /** When the agent last looked, for the logs written since. */
        @Volatile
        var checkedMillis = System.currentTimeMillis()
    }

    private val inboxes = ConcurrentHashMap<String, Inbox>()
    private val lastMessageId = AtomicInteger()

    private fun inbox(project: Project): Inbox =
        inboxes.getOrPut(project.info.name, ::Inbox)

    suspend fun send(project: Project, options: Options, wait: Wait): Sent {
        if (!project.isCompiled)
            throw ToolException("${project.info.name} isn't compiled: compile it first")
        val eventId = ProjectEventManager.validateAndGetEventID(ProjectOnMessageEvent::class)
            ?: throw ToolException("StarLight's message event isn't registered")
        if (!project.isEventCallAllowed(eventId))
            throw ToolException("${project.info.name} doesn't receive messages: allow the event $eventId with set_project_events")

        val inbox = inbox(project)
        // Whatever came in since the last look answers an earlier message, not this one.
        val earlier = drain(inbox.events)
        val id = lastMessageId.incrementAndGet()
        val sentMillis = System.currentTimeMillis()
        fun deliver(text: String? = null, error: Throwable? = null) {
            inbox.events.trySend(Event(id, options.text, sentMillis, System.currentTimeMillis(), text, error))
        }

        val roomConfig = GlobalConfig.category("d_room")
        val message = Message(
            message = options.text,
            image = null,
            sender = ChatSender(
                name = options.sender ?: GlobalConfig.category("d_user").getString("name", DEFAULT_SENDER),
                id = null,
                profileBitmap = profileBitmap(),
            ),
            room = DebugChatRoom(
                id = roomConfig.getString("id") ?: DEFAULT_ROOM_ID,
                name = options.room ?: roomConfig.getString("name", DEFAULT_ROOM),
                isGroupChat = options.isGroupChat ?: roomConfig.getBoolean("is_group_chat", false),
                onSend = { deliver(text = it) },
                onMarkAsRead = {},
            ),
            packageName = roomConfig.getString("package", PACKAGE_KAKAO_TALK),
            hasMention = false,
            chatLogId = -1L,
        )
        val functionName = ProjectOnMessageEvent::class.getInstance().functionName
        project.callFunction(functionName, arrayOf(message)) { deliver(error = it) }

        // An earlier message's late reply may come while this one waits; it's reported apart.
        val (events, late) = collect(inbox.events, wait, ours = { it.messageId == id }).partition { it.messageId == id }
        inbox.checkedMillis = System.currentTimeMillis()
        return Sent(events, earlier + late)
    }

    /** What the bot sent since the last look, waiting as [wait] says; and when that last look was. */
    suspend fun read(project: Project, wait: Wait): Pair<List<Event>, Long> {
        val inbox = inbox(project)
        val since = inbox.checkedMillis
        val events = collect(inbox.events, wait, before = drain(inbox.events))
        inbox.checkedMillis = System.currentTimeMillis()
        return events to since
    }

    /** What is waiting in [events], without waiting; replies that are too old to make sense of are dropped. */
    fun drain(events: ReceiveChannel<Event>, now: Long = System.currentTimeMillis()): List<Event> =
        generateSequence { events.tryReceive().getOrNull() }
            .filter { now - it.millis <= KEEP_MILLIS }
            .toList()

    /**
     * Takes events from [events] until [wait] is over, counting those taken [before] as part of it. Only events that
     * are [ours] count towards [Wait.expect] and start the quiet moment; the others are taken along.
     */
    suspend fun collect(
        events: ReceiveChannel<Event>,
        wait: Wait,
        before: List<Event> = emptyList(),
        ours: (Event) -> Boolean = { true },
        quietMillis: Long = QUIET_MILLIS,
    ): List<Event> {
        val taken = before.toMutableList()
        val deadline = System.currentTimeMillis() + wait.maxMillis
        while (true) {
            if (taken.any { it.error != null && ours(it) })
                break
            if (wait.expect != null && taken.count { it.text != null && ours(it) } >= wait.expect)
                break
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0)
                break
            // A bot may send several messages, so in this mode a reply ends the wait only after a quiet moment.
            val answered = taken.any(ours)
            val timeout = if (wait.untilQuiet && answered) minOf(remaining, quietMillis) else remaining
            taken += withTimeoutOrNull(timeout) { events.receive() } ?: break
        }
        // Anything that came at the same moment belongs with the rest.
        return taken + drain(events)
    }

    class Options(
        val text: String,
        val sender: String?,
        val room: String?,
        val isGroupChat: Boolean?,
    )

    @Volatile
    private var blankProfile: Bitmap? = null

    private fun profileBitmap(): Bitmap =
        GlobalConfig.category("d_user").getString("profile_image_path")
            ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
            ?: blankProfile
            ?: createBitmap(1, 1).also { blankProfile = it }

    const val DEFAULT_WAIT_SECONDS = 5
    const val EXPECT_WAIT_SECONDS = 30
    const val MAX_WAIT_SECONDS = 60
    private const val QUIET_MILLIS = 1_000L
    private const val MAX_KEPT = 50
    /** Late replies older than this are from tests long past. */
    private const val KEEP_MILLIS = 10 * 60_000L
    private const val PACKAGE_KAKAO_TALK = "com.kakao.talk"
    private const val DEFAULT_SENDER = "debug_sender"
    private const val DEFAULT_ROOM = "undefined"
    private const val DEFAULT_ROOM_ID = "agent_debug_room"
}
