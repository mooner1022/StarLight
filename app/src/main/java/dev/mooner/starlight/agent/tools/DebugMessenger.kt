package dev.mooner.starlight.agent.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.mooner.starlight.listener.event.ProjectOnMessageEvent
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends a message to a project's `onMessage` the way the debug room does, without showing it there, and collects what
 * the bot sends back to the room.
 */
internal object DebugMessenger {

    class Reply(val replies: List<String>, val error: Throwable?)

    class Options(
        val text: String,
        val sender: String?,
        val room: String?,
        val isGroupChat: Boolean?,
        val waitMillis: Long,
    )

    suspend fun send(project: Project, options: Options): Reply {
        if (!project.isCompiled)
            throw ToolException("${project.info.name} isn't compiled: compile it first")
        val eventId = ProjectEventManager.validateAndGetEventID(ProjectOnMessageEvent::class)
            ?: throw ToolException("StarLight's message event isn't registered")
        if (!project.isEventCallAllowed(eventId))
            throw ToolException("${project.info.name} doesn't receive messages: allow the event $eventId with set_project_events")

        val events = Channel<Any>(Channel.UNLIMITED)
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
                onSend = { events.trySend(it) },
                onMarkAsRead = {},
            ),
            packageName = roomConfig.getString("package", PACKAGE_KAKAO_TALK),
            hasMention = false,
            chatLogId = -1L,
        )
        val functionName = ProjectOnMessageEvent::class.getInstance().functionName
        project.callFunction(functionName, arrayOf(message)) { events.trySend(it) }

        val replies = mutableListOf<String>()
        var error: Throwable? = null
        val deadline = System.currentTimeMillis() + options.waitMillis
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0)
                break
            // A bot may send several messages, so a reply only ends the wait after a quiet moment.
            val answered = replies.isNotEmpty() || error != null
            val event = withTimeoutOrNull(if (answered) minOf(remaining, QUIET_MILLIS) else remaining) { events.receive() }
                ?: break
            when (event) {
                is String    -> replies += event
                is Throwable -> error = event
            }
        }
        events.close()
        return Reply(replies, error)
    }

    @Volatile
    private var blankProfile: Bitmap? = null

    private fun profileBitmap(): Bitmap =
        GlobalConfig.category("d_user").getString("profile_image_path")
            ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
            ?: blankProfile
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { blankProfile = it }

    private const val QUIET_MILLIS = 1_000L
    private const val PACKAGE_KAKAO_TALK = "com.kakao.talk"
    private const val DEFAULT_SENDER = "debug_sender"
    private const val DEFAULT_ROOM = "undefined"
    private const val DEFAULT_ROOM_ID = "agent_debug_room"
}
