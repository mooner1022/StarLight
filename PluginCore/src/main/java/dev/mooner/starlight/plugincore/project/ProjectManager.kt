/*
 * ProjectManager.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import java.io.File
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.jvmName

typealias StateChangeListener = (project: Project) -> Unit
typealias ListUpdateListener = (projects: List<Project>, project: Project?) -> Unit

class ProjectManager(
    private val projectDir: File
) {

    private val stateChangeListeners: MutableMap<String, StateChangeListener> = hashMapOf()
    private val listUpdateListeners: MutableMap<String, ListUpdateListener> = hashMapOf()
    internal var projects: MutableMap<String, Project> = hashMapOf()

    fun getEnabledProjects(): List<Project> {
        return projects.values.filter { it.info.isEnabled }
    }

    internal fun onStateChanged(project: Project) {
        for (listener in stateChangeListeners.values)
            listener(project)
    }

    fun addOnStateChangedListener(key: String, listener: (project: Project) -> Unit) {
        if (key !in stateChangeListeners)
            stateChangeListeners[key] = listener
    }

    fun removeOnStateChangedListener(key: String) {
        if (key in stateChangeListeners)
            stateChangeListeners -= key
    }

    fun addOnListUpdatedListener(key: String, listener: ListUpdateListener) {
        if (key !in listUpdateListeners)
            listUpdateListeners[key] = listener
    }

    fun removeOnListUpdatedListener(key: String) {
        if (key in listUpdateListeners)
            listUpdateListeners -= key
    }

    fun getProjects(): List<Project> {
        return this.projects.values.toList()
    }

    fun getProject(name: String): Project? {
        return projects[name]
    }

    fun updateProjectConfig(name: String, callListener: Boolean = false, block: ProjectInfo.() -> Unit) {
        val project = projects[name]?: throw IllegalArgumentException("Cannot find project [$name]")
        project.info.block()
        project.saveInfo()
        if (callListener) {
            callListeners(project)
        }
    }

    fun newProject(info: ProjectInfo, dir: File = projectDir) {
        Project.create(dir, info).also { project ->
            projects[info.name] = project
            callListeners(project)
            Session.eventManager.fireEventSync(Events.Project.ProjectCreateEvent(project))
        }
    }

    fun newProject(dir: File = projectDir, block: ProjectInfoBuilder.() -> Unit) {
        val info = ProjectInfoBuilder().apply(block).build()
        newProject(info, dir)
    }

    fun fireEvent(eventId: String, functionName: String, args: Array<out Any>, onFailure: (e: Throwable) -> Unit) {
        //if (!Session.eventManager.hasEvent(eventId)) {
        //    Logger.e(ProjectManager::class.simpleName, "Rejecting event call from '$eventId' which is not registered on EventManager")
        //    return
        //}
        val projects = this.projects.values.filter { it.isCompiled && it.info.isEnabled && it.isEventCallAllowed(eventId) }
        if (projects.isEmpty()) return
        for (project in projects) {
            project.callFunction(functionName, args, onFailure)
        }
    }

    fun removeProject(project: Project, removeFiles: Boolean = true) {
        removeProject(project.info.name, removeFiles)
    }

    fun removeProject(name: String, removeFiles: Boolean = true) {
        if (projects.containsKey(name)) {
            if (removeFiles) projects[name]!!.directory.deleteRecursively()
            projects -= name
        }
        callListeners(null)
    }

    private fun callListeners(project: Project?) {
        val projects = projects.values.toList()
        for ((_, listener) in listUpdateListeners) {
            listener(projects, project)
        }
    }

    internal fun purge() {
        listUpdateListeners.clear()
        stateChangeListeners.clear()

        for ((_, project) in projects) {
            //project.saveConfig()   ...Occurs file content loss
            project.destroy()
        }
    }
}

inline fun <reified T: ProjectEvent> ProjectManager.fireEvent(vararg args: Any, noinline onFailure: (e: Throwable) -> Unit = {}): Boolean {
    val event = T::class.createInstance().also { event ->
        for ((index, arg) in event.argTypes.withIndex()) {
            Logger.v("${arg.jvmName}, ${args[index]::class.jvmName}")
            if (arg != args[index]::class)
                error("Passed argument types [${args.joinToString { clazz -> clazz::class.simpleName.toString() }}] do not match the required argument types: [${event.argTypes.joinToString { clazz -> clazz.simpleName.toString() }}]")
        }
    }
    this.fireEvent(event.id, event.functionName, args, onFailure)
    return true
}