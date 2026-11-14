/*
 * ChatWaits.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.chat

import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectCallGate
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.script.ScriptTimers
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scripts' waits for answers (`Chat.waitFor`), and the gate that holds an answer back from the script's event
 * functions and listeners.
 *
 * A message reaches a project as several calls, each run in turn on the project's workers: `onMessage`, the API2
 * `message` and `command` listeners, the legacy `response`. The first of them to run decides for all: when a wait
 * takes the message, none of them runs; when none does, all of them run, even if a wait that would take it starts
 * meanwhile, as one the message's own `onMessage` starts. Deciding as the call runs rather than as the message comes
 * in lets a wait started by a message take the next one, which may already be queued behind it.
 */
internal object ChatWaits : ProjectCallGate {

    /** The functions that get a chat message: StarLight's, with the message as its only argument, and the legacy one. */
    private const val MESSAGE_FUNCTION = "onMessage"
    private const val LEGACY_FUNCTION = "response"

    /** Enough for the calls of one message to find the decision its first call made. */
    private const val DECISIONS = 128

    /** Waits one project may have at once; a wait more ends the oldest. */
    const val MAX_WAITS = 100

    /** Where and from whom a wait takes the next message. */
    class Target(
        /** The room's ID; null to match by [roomName], as for a room without one. */
        val roomId: String?,
        val roomName: String,
        /** Who: `id:<sender ID>` or `name:<sender name>`; null for anyone in the room. */
        val sender: String?,
    ) {
        fun accepts(message: Message): Boolean =
            (if (roomId != null) message.room.id == roomId else message.room.name == roomName) &&
                (sender == null || senderOf(message) == sender)

        fun sameAs(other: Target): Boolean =
            roomId == other.roomId && roomName == other.roomName && sender == other.sender
    }

    class Wait(
        val target: Target,
        /** Given the answer, or null when the time is up or a newer wait for the same took its place. */
        private val onDone: (Message?) -> Unit,
        /** Called instead when the version that started the wait is gone, as after a compile. */
        private val onRelease: () -> Unit,
    ) {
        private val ended = AtomicBoolean()

        @Volatile
        var timeout: ScheduledFuture<*>? = null

        /** Ends the wait with [answer]; only the first end counts. */
        fun finish(answer: Message?) {
            if (ended.compareAndSet(false, true)) {
                timeout?.cancel(false)
                onDone(answer)
            }
        }

        /** Ends the wait without an answer and without calling back, for [release] to tell later. */
        fun drop() {
            if (ended.compareAndSet(false, true))
                timeout?.cancel(false)
        }

        fun release() =
            onRelease()
    }

    private class Decision(message: Message, val taken: Boolean) {
        val message = WeakReference(message)
    }

    private class State {
        val waits = ArrayList<Wait>()
        /** The latest messages' decisions, by identity: one equal to another, as the same text twice, is its own. */
        val decisions = ArrayDeque<Decision>()

        fun decisionOf(message: Message): Boolean? =
            decisions.firstOrNull { it.message.get() === message }?.taken

        fun decide(message: Message, taken: Boolean) {
            decisions.addLast(Decision(message, taken))
            while (decisions.size > DECISIONS)
                decisions.removeFirst()
        }

        /**
         * The message of each legacy `response` call not run yet, by its argument array, which carries no message.
         * Taken as the call runs, so a message isn't kept past it: a weak map would keep it until its next use, and
         * with it the debug room screen the message's room points to.
         */
        val legacyCalls = ArrayDeque<Pair<Array<out Any>, Message>>()

        fun noteLegacy(args: Array<out Any>, message: Message) {
            legacyCalls.addLast(args to message)
            // Calls that never run, as ones dropped with a full queue, don't pile up.
            while (legacyCalls.size > DECISIONS)
                legacyCalls.removeFirst()
        }

        fun takeLegacy(args: Array<out Any>): Message? {
            val index = legacyCalls.indexOfFirst { it.first === args }
            return if (index < 0) null else legacyCalls.removeAt(index).second
        }
    }

    private val states: MutableMap<ProjectID, State> = ConcurrentHashMap()

    private val delivering = ThreadLocal<Message?>()

    fun senderOf(message: Message): String =
        message.sender.id?.let { "id:$it" } ?: "name:${message.sender.name}"

    /**
     * Waits for the next message [target] accepts, for at most [timeoutMillis]. A wait of the same project for the
     * same target ends, with null.
     */
    fun start(project: ProjectID, target: Target, timeoutMillis: Long, onDone: (Message?) -> Unit, onRelease: () -> Unit): Wait {
        val wait = Wait(target, onDone, onRelease)
        val state = stateOf(project)
        val ended = ArrayList<Wait>()
        synchronized(state) {
            state.waits.removeAll { other -> other.target.sameAs(target).also { if (it) ended += other } }
            if (state.waits.size >= MAX_WAITS)
                ended += state.waits.removeAt(0)
            state.waits += wait
        }
        wait.timeout = ScriptTimers.schedule(timeoutMillis) { end(project, wait) }
        for (other in ended)
            other.finish(null)
        return wait
    }

    /** What became of a message for a project's calls. */
    enum class Claim {
        /** A wait took it, on this call: the first of the message's calls. */
        ANSWERED,
        /** A wait took it on an earlier call of the message. */
        HELD,
        /** No wait took it: the calls run. */
        PASSED,
    }

    /**
     * Whether [message] is taken by one of [project]'s waits, and so held back from its event functions and listeners.
     * The first call for a message decides; later ones for it get the same answer.
     */
    fun claim(project: ProjectID, message: Message): Claim {
        val state = stateOf(project)
        val answered: List<Wait>
        synchronized(state) {
            state.decisionOf(message)?.let { taken -> return if (taken) Claim.HELD else Claim.PASSED }
            answered = state.waits.filter { it.target.accepts(message) }
            state.waits.removeAll(answered.toSet())
            state.decide(message, taken = answered.isNotEmpty())
        }
        if (answered.isEmpty())
            return Claim.PASSED
        for (wait in answered)
            wait.finish(message)
        return Claim.ANSWERED
    }

    /** Whether [message] is held back from [project]'s calls; noted in its log once, as a wait takes it. */
    fun claim(project: Project, message: Message): Boolean {
        val claim = claim(project.info.id, message)
        if (claim == Claim.ANSWERED)
            project.logger.d(translate {
                Locale.ENGLISH { "A message from ${message.sender.name} answered Chat.waitFor, so the event functions don't get it" }
                Locale.KOREAN  { "${message.sender.name}의 메시지가 Chat.waitFor의 답이 되어 이벤트 함수에는 전달하지 않았어요" }
            })
        return claim != Claim.PASSED
    }

    override fun allows(project: Project, functionName: String, args: Array<out Any>): Boolean {
        val message = when (functionName) {
            MESSAGE_FUNCTION -> args.singleOrNull() as? Message
            LEGACY_FUNCTION  -> states[project.info.id]?.let { state -> synchronized(state) { state.takeLegacy(args) } }
            else             -> null
        } ?: return true
        return !claim(project, message)
    }

    /** Runs [block], which hands [message] to projects, so that legacy calls made meanwhile are known to be for it. */
    fun <T> delivering(message: Message, block: () -> T): T {
        val previous = delivering.get()
        delivering.set(message)
        return try {
            block()
        } finally {
            delivering.set(previous)
        }
    }

    /** Notes [project]'s legacy call with [args] as one for the message being delivered on this thread, if any. */
    fun noteLegacyCall(project: ProjectID, args: Array<out Any>) {
        val message = delivering.get()
            ?: return
        val state = stateOf(project)
        synchronized(state) { state.noteLegacy(args, message) }
    }

    /**
     * Takes [project]'s waits away: no message will answer them and they won't time out. For a compile, which ends
     * the version that started them; [release] tells them once that version can no longer act on it.
     */
    fun detach(project: ProjectID): List<Wait> {
        val state = states[project]
            ?: return emptyList()
        val waits = synchronized(state) {
            // The old version's legacy calls won't run.
            state.legacyCalls.clear()
            state.waits.toList().also { state.waits.clear() }
        }
        waits.forEach(Wait::drop)
        return waits
    }

    fun release(waits: List<Wait>) {
        for (wait in waits)
            runCatching(wait::release)
    }

    private fun end(project: ProjectID, wait: Wait) {
        val state = states[project]
        if (state != null)
            synchronized(state) { state.waits.remove(wait) }
        wait.finish(null)
    }

    private fun stateOf(project: ProjectID): State =
        states.computeIfAbsent(project) { State() }
}
