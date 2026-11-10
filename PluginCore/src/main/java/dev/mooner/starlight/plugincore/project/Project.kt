/*
 * Project.kt created by Minki Moon(mooner1022) on 4/22/23, 3:57 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.config.data.MutableConfig
import dev.mooner.starlight.plugincore.language.Language
import dev.mooner.starlight.plugincore.logger.ProjectLogger
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * A project: one bot, as `Projects.getSelf()`, `Projects.ofName(name)` and `Projects.ofId(id)` return it.
 *
 * Scripts mostly read its `info` and `compiled`, and call its functions with `callFunction`. Its other public members
 * are StarLight's and plugins' own, and aren't listed here.
 *
 * @see guide:projects#calling-other-projects
 */
abstract class Project: CoroutineScope, ProjectLifecycleOwner {

    /**
     * The project's folder, with its `project.json`, scripts and `data` folder.
     */
    abstract val directory: File

    /**
     * The project's info, as its `project.json` holds it: `info.name`, `info.id` and the rest.
     */
    abstract val info: ProjectInfo

    /**
     * Project's dedicated config data, saved in project's local directory.
     * @suppress
     */
    abstract val config: MutableConfig

    /**
     * Name of thread pool where scripts are being executed at.
     * @suppress
     */
    abstract var threadPoolName: String?

    /**
     * Whether the project is compiled, so that its functions can be called; scripts read it as `compiled`.
     */
    abstract val isCompiled: Boolean

    /**
     * The project's log: what its scripts reach as `Log`.
     */
    abstract val logger: ProjectLogger

    /**
     * Calls the function [name] of the project's script with [args], on the project's workers, and returns at once.
     *
     * The function's result isn't returned: pass values back through another call, or `Broadcast` in API2 projects. All
     * three arguments are needed; there is no shorter form. Nothing happens if the script has no such function.
     *
     * A project's top-level code runs before its compile finishes, so a call from there to its own project fails with
     * `onException`: call the function directly instead.
     *
     * @param name The function's name. In NeonJS the function must be exported.
     * @param args The arguments, as an array: a JS array works.
     * @param onException Called with the error when the project isn't compiled, or when the function throws. A thrown
     *   error is also logged and, as an event's error does, may unload that project: turned off, and not compiled
     *   until it is compiled again (guide:projects#errors).
     */
    abstract fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit = {})

    /**
     * Runs [task] as one of the project's events: on its workers, among its other events, and with the same handling of
     * an error as a called function gets, which may unload the project.
     * @suppress
     */
    abstract fun runEvent(task: () -> Unit, onException: (Throwable) -> Unit = {})

    /**
     * Compiles the project, as its compile button does, and waits until that is done.
     *
     * Compiling the project that runs the script replaces the code that is running.
     *
     * @param throwException Whether a failed compile throws its error rather than returning `false`.
     * @return Whether it compiled.
     */
    abstract fun compile(throwException: Boolean = false): Boolean

    /**
     * Compiles the project with configured values.
     * @suppress
     */
    abstract fun compileAsync(): Flow<Pair<PipelineStage<*, *>, Int>>

    /**
     * Turns the project on or off, as its switch in the app does.
     *
     * @param enabled `true` to turn it on.
     * @return `false`, with nothing changed, if the project isn't compiled.
     */
    abstract fun setEnabled(enabled: Boolean): Boolean

    /**
     * Requests the application to update UI of the project.
     *
     * Ignored if application is on background.
     * @suppress
     */
    abstract fun requestUpdate()

    /**
     * Save contents of [info] to a file.
     * @suppress
     */
    abstract fun saveInfo()

    /**
     * Invalidate in-memory project info data and reload it from local file.
     * @suppress
     */
    abstract fun loadInfo()

    /**
     * The project's language: `getLanguage().id` is `js_neon` for NeonJS, `JS_RHINO` for Rhino.
     */
    abstract fun getLanguage(): Language

    /**
     * Returns the scope instance if project is compiled, else null.
     *
     * @return scope returned on compile
     * @suppress
     */
    abstract fun getScope(): Any?

    /**
     * The number of events the project is running right now.
     */
    abstract fun activeJobs(): Int

    /**
     * The number of events waiting for the project's workers.
     */
    open fun queuedEvents(): Int = 0

    /**
     * Cancels all running jobs.
     * @suppress
     */
    abstract fun stopAllJobs()

    /**
     * Cancels all running jobs and releases `langScope` if the project is compiled.
     * @suppress
     */
    open fun destroy() =
        destroy(false)

    /**
     * Cancels all running jobs and releases `langScope` if the project is compiled.
     *
     * @param requestUpdate if *true*, requests update of UI
     * @suppress
     */
    abstract fun destroy(requestUpdate: Boolean = false)

    /**
     * Update the name of project
     *
     * @param name you know what this is, right?
     * @suppress
     */
    open fun rename(name: String) =
        rename(name, false)

    /**
     * Update the name of project
     *
     * @param name you know what this is, right?
     * @param preserveMainScript if false, the main script for the project will also be renamed.
     * @suppress
     */
    abstract fun rename(name: String, preserveMainScript: Boolean)

    /**
     * The project's `data` folder, created when missing: the place for what its script saves, where `DataBase` writes.
     */
    abstract fun getDataDirectory(): File

    /**
     * Check whether call of ProjectEvent with id [eventId] is allowed or not.
     *
     * @param eventId the event id to check if it's allowed to be called.
     * @suppress
     */
    abstract fun isEventCallAllowed(eventId: String): Boolean

    internal abstract fun getClassLoader(): ClassLoader

    override fun equals(other: Any?): Boolean {
        return other is Project && other.info.id == this.info.id
    }

    override fun hashCode(): Int {
        return info.id.hashCode()
    }
}