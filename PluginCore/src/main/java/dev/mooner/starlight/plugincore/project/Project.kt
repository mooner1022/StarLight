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

abstract class Project: CoroutineScope, ProjectLifecycleOwner {

    /**
     * Root directory of project itself.
     */
    abstract val directory: File

    /**
     * Information data of this project.
     */
    abstract val info: ProjectInfo

    /**
     * Project's dedicated config data, saved in project's local directory.
     */
    abstract val config: MutableConfig

    /**
     * Name of thread pool where scripts are being executed at.
     */
    abstract var threadPoolName: String?

    /**
     * The state of project compilation, true if scope isn't null.
     */
    abstract val isCompiled: Boolean

    /**
     * Logger used for project event logging.
     */
    abstract val logger: ProjectLogger

    /**
     * Calls an event with [name] and passes [args] as parameter.
     *
     * @param name the name of function or event being called.
     * @param args parameter values being passed to function.
     * @param onException called when exception is occurred while calling.
     */
    abstract fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit = {})

    /**
     * Execute the compile pipeline of this project. Releases its original scope and context if present.
     *
     * @param throwException if true, throws exception on compile failure.
     */
    abstract fun compile(throwException: Boolean = false): Boolean

    /**
     * Compiles the project with configured values.
     */
    abstract fun compileAsync(): Flow<Pair<PipelineStage<*, *>, Int>>

    /**
     * Set project's enabled state
     *
     * @param enabled the target state of project.
     */
    abstract fun setEnabled(enabled: Boolean): Boolean

    /**
     * Requests the application to update UI of the project.
     *
     * Ignored if application is on background.
     */
    abstract fun requestUpdate()

    /**
     * Save contents of [info] to a file.
     */
    abstract fun saveInfo()

    /**
     * Invalidate in-memory project info data and reload it from local file.
     */
    abstract fun loadInfo()

    /**
     * Returns the language used in the project.
     *
     * @return language used in the project.
     */
    abstract fun getLanguage(): Language

    /**
     * Returns the scope instance if project is compiled, else null.
     *
     * @return scope returned on compile
     */
    abstract fun getScope(): Any?

    /**
     * Count of jobs currently running in thread pool of this project.
     *
     * @return the size of running jobs.
     */
    abstract fun activeJobs(): Int

    /**
     * Cancels all running jobs.
     */
    abstract fun stopAllJobs()

    /**
     * Cancels all running jobs and releases [langScope] if the project is compiled.
     */
    open fun destroy() =
        destroy(false)

    /**
     * Cancels all running jobs and releases [langScope] if the project is compiled.
     *
     * @param requestUpdate if *true*, requests update of UI
     */
    abstract fun destroy(requestUpdate: Boolean = false)

    /**
     * Get the directory where data of project should be saved in. ex) database files, user data, etc.
     */
    abstract fun getDataDirectory(): File

    /**
     * Check whether call of ProjectEvent with id [eventId] is allowed or not.
     *
     * @param eventId the event id to check if it's allowed to be called.
     */
    abstract fun isEventCallAllowed(eventId: String): Boolean

    internal abstract fun getClassLoader(): ClassLoader

    override fun equals(other: Any?): Boolean {
        return other is Project && other.info.id == this.info.id
    }
}