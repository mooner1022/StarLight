package dev.mooner.starlight.listener.legacy

import dev.mooner.starlight.api.msgbot.ScriptVersion
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.languages.neonjs.whileWaitingForAnotherThread
import dev.mooner.starlight.listener.chat.ChatSessions
import dev.mooner.starlight.listener.chat.RoomKey
import dev.mooner.starlight.listener.chat.RoomResolver
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID

/**
 * Where a [Replier]'s message came from.
 *
 * @property roomName The room's name; in the debug room, the debug room's.
 * @property roomKey The room's key.
 * @property packageName The package name of the messenger the message came from, if known.
 * @property isDebug Whether the message came from the debug room.
 * @property projectId The project the replier belongs to, when it is made for one: the debug room screen's. A replier
 * shared by projects has none; the project it is handed to adds it ([Replier.forProject]).
 */
internal class ReplierOrigin(
    val roomName: String,
    val roomKey: RoomKey,
    val packageName: String?,
    val isDebug: Boolean,
    val projectId: ProjectID? = null,
)

/**
 * Sends replies for the legacy `response` event: MessengerBot's `replier`.
 *
 * `reply(message)` answers in the room the message came from. The forms with a room argument send to any room StarLight
 * has a reply session for, from any messenger: `room` is a name, a room ID (a `BigInt` or whole number) or a room object,
 * and a string of digits is a name, not an ID. They return `true` when the room has a session, whether or not the
 * messenger took the text, and `false` when no known room matches; they never fall back to this replier's own room and
 * show no toast. `hideToast` is accepted and ignored.
 *
 * `reply(message)` in the debug room shows the reply in the debug room. A room argument that names (a string) or
 * numbers (a whole number or `BigInt`) the project's open debug room shows the text there and returns `true`, from any
 * message, before any other room is looked up.
 *
 * @see guide:testing#how-debug-messages-differ
 */
class Replier internal constructor(
    private val origin: ReplierOrigin? = null,
    /** The project this replier was handed to, whose state ends a delayed reply; `null` when it isn't known. */
    private val project: Project? = null,
    /**
     * The function StarLight sends replies through: the room's name (`null` for the replier's own room), the text and
     * whether to skip the toast.
     */
    private val onSend: (room: String?, message: String, hideToast: Boolean) -> Boolean
) {

    /** This replier as the project [project] gets it: the same room and sending, plus the project's debug room and state. */
    internal fun forProject(project: Project): Replier =
        Replier(
            origin?.let { ReplierOrigin(it.roomName, it.roomKey, it.packageName, it.isDebug, project.info.id) },
            project,
            onSend,
        )

    /**
     * Sends [message] to the room the message came from, and says whether it was sent.
     *
     * A value that is not a string is turned into text first: a number as it is written (`5`), `null` as `"null"`.
     *
     * @param message The text to send.
     * @return `false` if the room couldn't be reached, or if the messenger's reply action failed.
     */
    fun reply(message: Any?): Boolean =
        onSend(null, ScriptArgs.stringify(message), true)

    /**
     * Sends [message] to [room] and says whether the room has a reply session.
     *
     * @param room The room's name, ID or object.
     * @param message The text to send.
     * @return `false` if no known room matches; `true` if the room has a session, even when the messenger refused the
     * text. `true` for the name or ID of the project's open debug room.
     */
    fun reply(room: Any?, message: Any?): Boolean =
        sendTo(room, ScriptArgs.stringify(message), 0L, markdown = false)

    /**
     * Sends [message] to [room]; the same as the form without [hideToast], which is ignored.
     *
     * @param room The room's name, ID or object.
     * @param message The text to send.
     * @param hideToast Not used; no toast is shown.
     * @throws IllegalArgumentException `"hideToast must be a boolean"` when it is an object.
     */
    fun reply(room: Any?, message: Any?, hideToast: Any?): Boolean {
        ScriptArgs.boolean(hideToast, "hideToast", false)
        return reply(room, message)
    }

    /**
     * Waits [delay] milliseconds, then sends [message] to the room the message came from.
     *
     * The wait blocks the script that calls it, as `Thread.sleep` does, so two calls in a row take the sum of their
     * delays and send in order. A delay of `0`, a negative one or `null` doesn't wait. The wait ends early, without
     * sending, and the call returns `false`, when the project is turned off, recompiled or deleted: it checks every
     * 50 ms. `Api.interruptThreads` cannot interrupt it, as nothing interrupts the thread that sleeps.
     *
     * @param message The text to send.
     * @param delay The wait in milliseconds.
     * @return `false` if the room couldn't be reached, or the project stopped during the wait.
     */
    fun replyDelayed(message: Any?, delay: Any?): Boolean {
        val millis = ScriptArgs.long(delay, "delay", 0L)
        val text = ScriptArgs.stringify(message)
        if (!pause(millis))
            return false
        return onSend(null, text, true)
    }

    /**
     * Waits [delay] milliseconds, then sends [message] to [room] as `reply(room, message)` does.
     *
     * An unknown room returns `false` at once, without waiting. The name or ID of the project's open debug room waits,
     * shows the message and returns `true`. The wait ends early, and the call returns `false`, as the form without a
     * room does.
     *
     * @param room The room's name, ID or object.
     * @param message The text to send.
     * @param delay The wait in milliseconds.
     */
    fun replyDelayed(room: Any?, message: Any?, delay: Any?): Boolean =
        sendTo(room, ScriptArgs.stringify(message), ScriptArgs.long(delay, "delay", 0L), markdown = false)

    /**
     * Waits [delay] milliseconds, then sends [message] to [room]; [hideToast] is ignored.
     *
     * @param room The room's name, ID or object.
     * @param message The text to send.
     * @param delay The wait in milliseconds.
     * @param hideToast Not used; no toast is shown.
     */
    fun replyDelayed(room: Any?, message: Any?, delay: Any?, hideToast: Any?): Boolean {
        ScriptArgs.boolean(hideToast, "hideToast", false)
        return replyDelayed(room, message, delay)
    }

    /**
     * Sends [message] as a Markdown message to the room the message came from.
     *
     * The messenger is handed a share that marks the text as Markdown. KakaoTalk sends it, but shows the formatting only
     * on this device: the other people in the room see plain text (KakaoTalk 26.9.1). `true` only says the share was
     * launched, not that the message arrived. In the debug room the text is shown as it is, and `true` is returned.
     *
     * @param message The text to send.
     * @return `false` if no messenger is known for the room, or nothing can take the share.
     */
    fun replyWithMarkdown(message: Any?): Boolean {
        val text = ScriptArgs.stringify(message)
        val origin = origin ?: return false
        if (origin.isDebug) {
            onSend(null, text, true)
            return true
        }
        val pkg = origin.packageName ?: ChatSessions.packageOf(origin.roomKey) ?: return false
        return ChatSessions.sendMarkdown(origin.roomKey, text, pkg)
    }

    /**
     * Sends [message] as Markdown to [room]: the room's latest messenger gets the share.
     *
     * @param room The room's name, ID or object.
     * @param message The text to send.
     * @return `false` if no known room matches or nothing can take the share. The name or ID of the project's open
     * debug room shows the text there and returns `true`.
     */
    fun replyWithMarkdown(room: Any?, message: Any?): Boolean =
        sendTo(room, ScriptArgs.stringify(message), 0L, markdown = true)

    /**
     * Sends [message] as Markdown to [room]; [hideToast] is ignored.
     *
     * @param room The room's name, ID or object.
     * @param message The text to send.
     * @param hideToast Not used; no toast is shown.
     */
    fun replyWithMarkdown(room: Any?, message: Any?, hideToast: Any?): Boolean {
        ScriptArgs.boolean(hideToast, "hideToast", false)
        return replyWithMarkdown(room, message)
    }

    /**
     * Marks the messages of the room this message came from as read, through the messenger it came from.
     *
     * @return `true` when the messenger's read action was sent. Always `false` in the debug room.
     */
    fun markAsRead(): Boolean {
        val origin = origin ?: return false
        if (origin.isDebug)
            return false
        val pkg = origin.packageName ?: return false
        return ChatSessions.markAsRead(origin.roomKey, pkg)
    }

    /**
     * Marks the messages of [room] as read, through every messenger that has a session of it.
     *
     * @param room The room's name, ID or object.
     * @return `true` when at least one read action was sent; `false` for an unknown room and in the debug room.
     * @throws IllegalArgumentException `"room must be a string"` when [room] is `null` or `undefined`.
     */
    fun markAsRead(room: Any?): Boolean {
        if (ScriptArgs.isNullish(room))
            throw IllegalArgumentException("room must be a string")
        if (origin?.isDebug == true)
            return false
        return ChatSessions.markRoomAsRead(room)
    }

    /**
     * Marks the messages of [room] as read, through the messenger [packName] only.
     *
     * @param room The room's name, ID or object.
     * @param packName The messenger's package name, such as `com.kakao.talk`.
     * @return `true` when that messenger's read action was sent; `false` when it has no session of the room, and in the
     * debug room.
     * @throws IllegalArgumentException `"room must be a string"` or `"packName must be a string"` for `null` or
     * `undefined`.
     */
    fun markAsRead(room: Any?, packName: Any?): Boolean {
        if (ScriptArgs.isNullish(room))
            throw IllegalArgumentException("room must be a string")
        val pkg = ScriptArgs.string(packName, "packName")
        if (origin?.isDebug == true)
            return false
        return ChatSessions.markRoomAsRead(room, pkg)
    }

    /**
     * The replier as text: `Replier[packName=<package> room=Room(name=<name>, id=<id>) isDebugChat=<bool>]`.
     */
    override fun toString(): String {
        val origin = origin
        val room = origin?.roomKey?.toString() ?: "null"
        return "Replier[packName=${origin?.packageName} room=$room isDebugChat=${origin?.isDebug == true}]"
    }

    /** The project's id, from the project this replier was handed to or, failing that, from its origin. */
    private val projectId: ProjectID?
        get() = project?.info?.id ?: origin?.projectId

    /**
     * Waits [millis] milliseconds, in slices of 50 ms, and says whether it waited the whole time. It stops early when
     * the project it belongs to is turned off, recompiled or deleted, which it sees as its script scope changing or
     * going, or its switch going off if it was on; the state is taken at the start. Without a project it just sleeps.
     */
    private fun pause(millis: Long): Boolean {
        if (millis <= 0L)
            return true
        val project = project
            ?: return whileWaitingForAnotherThread { Thread.sleep(millis); true }
        // Not compiled, and not compiling either: its top-level code, which runs while it compiles, may wait.
        if (project.getScope() == null && project.getLifecycle().state != ProjectLifecycle.State.COMPILING)
            return false
        val version = ScriptVersion.of(project)
        // The debug room runs a project that is off too: only a switch that was on and goes off ends the wait.
        val wasOn = project.info.isEnabled
        // The wait lets go of a NeonJS context, which the main thread or another worker may need meanwhile. The
        // checks read the project's state only, never a script value.
        return whileWaitingForAnotherThread {
            val end = System.nanoTime() + millis * 1_000_000L
            var running = true
            while (running) {
                val left = (end - System.nanoTime()) / 1_000_000L
                if (left <= 0L)
                    break
                Thread.sleep(minOf(left, PAUSE_SLICE_MILLIS))
                running = ScriptVersion.isCurrent(project, version) && (!wasOn || project.info.isEnabled)
            }
            running
        }
    }

    private fun sendTo(room: Any?, text: String, delay: Long, markdown: Boolean): Boolean {
        // The project's open debug room is no chat session: it is reached by its name or ID, from any message.
        val id = projectId
        if (id != null && DebugRoomLinks.matches(id, room)) {
            if (!pause(delay))
                return false
            DebugRoomLinks.send(id, room, text)
            return true
        }
        val key = RoomResolver.resolve(room)
        if (key == null) {
            // Without a project id, the debug room can only be named from a debug message.
            if (id == null && origin?.isDebug == true && ScriptArgs.stringOrNull(room) == origin.roomName) {
                if (!pause(delay))
                    return false
                onSend(null, text, true)
                return true
            }
            return false
        }
        if (!pause(delay))
            return false
        return if (markdown) {
            val pkg = ChatSessions.packageOf(key) ?: return false
            ChatSessions.sendMarkdown(key, text, pkg)
        } else {
            ChatSessions.reply(key, text)
        }
    }

    private companion object {
        const val PAUSE_SLICE_MILLIS = 50L
    }
}
