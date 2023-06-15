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

    abstract val directory: File

    abstract val info: ProjectInfo

    abstract val config: MutableConfig

    abstract var threadPoolName: String?

    abstract val isCompiled: Boolean

    abstract val logger: ProjectLogger

    /**
     * Calls an event with [name] and passes [args] as parameter.
     *
     * @param name the name of function or event being called.
     * @param args parameter values being passed to function.
     * @param onException called when exception is occurred while calling.
     */
    abstract fun callFunction(name: String, args: Array<out Any>, onException: (Throwable) -> Unit = {})

    abstract fun compile(throwException: Boolean = false): Boolean

    /**
     * Compiles the project with configured values.
     */
    abstract fun compileAsync(): Flow<Pair<PipelineStage<*, *>, Int>>

    abstract fun setEnabled(enabled: Boolean): Boolean

    /**
     * Requests the application to update UI of the project.
     *
     * Ignored if application is on background.
     */
    abstract fun requestUpdate()

    /**
     * Saves contents of [info] to a file.
     */
    abstract fun saveInfo()

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
     *
     * @param requestUpdate if *true*, requests update of UI
     */
    abstract fun destroy(requestUpdate: Boolean = false)

    abstract fun getDataDirectory(): File

    abstract fun isEventCallAllowed(eventId: String): Boolean

    internal abstract fun getClassLoader(): ClassLoader
}