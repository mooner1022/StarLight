/*
 * UncaughtErrors.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.errors

import dev.mooner.neonjs.NeonInterruptedException
import dev.mooner.starlight.languages.ScriptErrors
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectErrorListener
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private val LOG = LoggerFactory.logger {  }

/**
 * A script's `onUncaughtError`, as Node's `process.on("uncaughtException")`: told of the errors of a project's code that
 * nothing caught, after they are logged. Whether the project is then turned off is still its setting's to say.
 *
 * Each compiled version that defines the function installs its handler; errors reach the handler of the version that
 * runs, through [report]. An error of the handler itself isn't reported to it again, and calls are limited by
 * [UncaughtErrorPolicy].
 */
internal object UncaughtErrors {

    const val FUNCTION = "onUncaughtError"

    /** Where an error came from, as Node names it. */
    enum class Origin(val text: String) {
        EXCEPTION("uncaughtException"),
        REJECTION("unhandledRejection"),
    }

    /** A project, as far as this needs it. */
    interface Owner {
        val id: ProjectID
        /** Logs [message] in the project's log as a warning. */
        fun warn(message: String)
        /** Logs [message] in the project's log as an error. */
        fun error(message: String)
        fun describe(error: Throwable): String
        /** Hands the handler's error to the project's error path: logged, and the project turned off if its setting says. */
        fun fail(error: Throwable)
    }

    /** One compiled version's handler, as its engine calls it. An error it throws at once is thrown. */
    fun interface Handler {
        /**
         * Calls the script's function with the error, the origin's name, and where it came from.
         *
         * @return Whether its run ended: false when it returned a Promise, whose outcome comes later through
         * [handlerSucceededLater] or [handlerFailedLater].
         */
        fun call(error: Throwable, origin: String, source: String, args: Array<out Any?>): Boolean
    }

    /** The handler's own error, on its way to the project's error path: never reported to the handler again. */
    class HandlerFailure(cause: Throwable, description: String) :
        RuntimeException("onUncaughtError failed: $description", cause)

    private class State(val owner: Owner, val handler: Handler?, val policy: UncaughtErrorPolicy)

    private val states: MutableMap<ProjectID, State> = ConcurrentHashMap()

    /** Whether this thread runs a handler: what fails in it isn't reported to it. */
    private val inHandler = ThreadLocal<Boolean>()

    var clock: () -> Long = System::currentTimeMillis

    /** Passes on the errors of event functions and listeners, which the project logs and applies its setting to. */
    val listener = ProjectErrorListener { project, _, error, functionName, args ->
        report(project.info.id, error, Origin.EXCEPTION, functionName ?: "listener", args ?: emptyArray(), settingFollows = true)
    }

    /** The handler of the version that just compiled; null when its script has none. Replaces the last version's. */
    fun install(owner: Owner, handler: Handler?) {
        states[owner.id] = State(owner, handler, UncaughtErrorPolicy { clock() })
    }

    /** Forgets the project's handler: its version is ending, or failed to compile. */
    fun clear(id: ProjectID) {
        states -= id
    }

    /**
     * Tells the handler of the project [id] of [error], if it has one; never throws.
     *
     * @param source What ran: the event function's name, `listener`, `setTimeout`, `Schedule.daily`, `promise`…
     * @param args The arguments of the event, for the handler's `detail.args`.
     * @param settingFollows Whether the project's setting for errors is applied to [error] next, as for the errors of
     * event functions and listeners: the handler's own error is then only logged.
     */
    fun report(
        id: ProjectID,
        error: Throwable,
        origin: Origin,
        source: String,
        args: Array<out Any?> = emptyArray(),
        settingFollows: Boolean = false,
    ) {
        try {
            val state = states[id]
                ?: return
            val handler = state.handler
                ?: return
            if (error is HandlerFailure || isInterruption(error))
                return
            if (inHandler.get() == true)
                return
            when (state.policy.next()) {
                UncaughtErrorPolicy.Decision.CALL -> Unit
                UncaughtErrorPolicy.Decision.LIMITED_FIRST -> {
                    state.owner.warn(translate {
                        Locale.ENGLISH { "onUncaughtError is called at most 10 times a minute; further errors are only logged for now" }
                        Locale.KOREAN  { "오류가 잦아 onUncaughtError 호출을 잠시 쉬어요 (1분에 10번까지)" }
                    })
                    return
                }
                UncaughtErrorPolicy.Decision.LIMITED,
                UncaughtErrorPolicy.Decision.DISABLED -> return
            }
            inHandler.set(true)
            try {
                if (handler.call(error, origin.text, source, args))
                    state.policy.succeeded()
            } catch (e: Throwable) {
                failed(state, e, settingFollows)
            } finally {
                inHandler.remove()
            }
        } catch (e: Throwable) {
            warnQuietly("Couldn't hand an error of project $id to onUncaughtError: $e")
        }
    }

    /** The Promise [handler] returned was fulfilled. Ignored when [handler] is no longer the project's. */
    fun handlerSucceededLater(id: ProjectID, handler: Handler) {
        states[id]?.takeIf { it.handler === handler }?.policy?.succeeded()
    }

    /** The Promise [handler] returned was rejected with [error]. Ignored when [handler] is no longer the project's. */
    fun handlerFailedLater(id: ProjectID, handler: Handler, error: Throwable) {
        try {
            val state = states[id]?.takeIf { it.handler === handler }
                ?: return
            failed(state, error, settingFollows = false)
        } catch (e: Throwable) {
            warnQuietly("Couldn't handle a failure of onUncaughtError of project $id: $e")
        }
    }

    /** Callers are engines' error paths, which this must never break: not even by a log that fails. */
    private fun warnQuietly(message: String) {
        runCatching { LOG.warn { message } }
    }

    private fun failed(state: State, error: Throwable, settingFollows: Boolean) {
        // Stopped by the engine, as when the project compiles again: not the handler's fault.
        if (isInterruption(error))
            return
        val description = state.owner.describe(error)
        if (settingFollows)
            state.owner.error(translate {
                Locale.ENGLISH { "onUncaughtError failed: $description" }
                Locale.KOREAN  { "onUncaughtError 처리 중 오류: $description" }
            })
        else
            // Logged there, as an event's error is, and the project turned off if its setting says.
            state.owner.fail(HandlerFailure(error, description))
        if (state.policy.failed())
            state.owner.warn(translate {
                Locale.ENGLISH { "onUncaughtError failed 3 times in a row, so it isn't called until the project compiles again" }
                Locale.KOREAN  { "onUncaughtError가 3번 연속 실패해 다음 컴파일까지 부르지 않아요" }
            })
    }

    /** An engine stopping the code, rather than the code failing. */
    private fun isInterruption(error: Throwable): Boolean =
        error is CancellationException ||
            generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
                .take(MAX_CAUSES)
                .any { it is NeonInterruptedException }

    private const val MAX_CAUSES = 8

    /** [project] as the handler's owner. */
    fun ownerOf(project: Project): Owner =
        ProjectOwner(project)

    /**
     * Installs [handler], of the version [project] is compiling, once the compile has ended, and forgets it when that
     * version ends: as the project compiles again, or is unloaded. One for each compile.
     *
     * Not at once: until the compile ends, the project's version is still the last one, whose errors would otherwise
     * reach the new handler.
     */
    fun watch(project: Project, handler: Handler?) {
        val observer = object : ProjectLifecycleObserver.ExplicitObserver {
            // The new version runs from here, and the last one's scope is gone.
            override fun onCompileEnd(project: Project) =
                install(ownerOf(project), handler)

            // Before the new code runs: errors of the old one while it compiles don't reach its handler.
            override fun onCompileStart(project: Project) {
                clear(project.info.id)
                project.getLifecycle().unregisterObserver(this)
            }

            override fun onDestroy(project: Project) {
                clear(project.info.id)
                project.getLifecycle().unregisterObserver(this)
            }
        }
        project.getLifecycle().registerObserver(observer)
    }

    private class ProjectOwner(private val project: Project) : Owner {

        override val id: ProjectID
            get() = project.info.id

        override fun warn(message: String) =
            project.logger.w(message)

        override fun error(message: String) =
            project.logger.e(message)

        override fun describe(error: Throwable): String =
            ScriptErrors.describe(error, project.directory)

        override fun fail(error: Throwable) {
            // Not for a version a compile has replaced since: the error would be the new one's.
            val scope = project.getScope()
            project.runEvent({
                if (project.getScope() === scope)
                    throw error
            }) { }
        }
    }
}
