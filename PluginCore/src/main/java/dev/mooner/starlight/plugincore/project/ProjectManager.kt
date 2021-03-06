/*
 * ProjectManager.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session.eventManager
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import java.io.File
import kotlin.reflect.full.createInstance

class ProjectManager(
    private val projectDir: File
) {
    internal var projects: MutableMap<String, Project> = hashMapOf()

    fun getEnabledProjects(): List<Project> {
        return projects.values.filter { it.info.isEnabled }
    }

    fun getProjects(): List<Project> {
        return projects.values.toList()
    }

    fun getProject(name: String): Project? {
        return projects[name]
    }

    fun updateProjectInfo(name: String, callListener: Boolean = false, block: ProjectInfo.() -> Unit) {
        val project = projects[name]?: throw IllegalArgumentException("Cannot find project [$name]")
        project.info.block()
        project.saveInfo()
        if (callListener) {
            eventManager.fireEventWithContext(Events.Project.ProjectInfoUpdateEvent(project))
        }
    }

    fun newProject(info: ProjectInfo, dir: File = projectDir) {
        Project.create(dir, info).also { project ->
            projects[info.name] = project
            eventManager.fireEventWithContext(Events.Project.ProjectCreateEvent(project))
        }
    }

    fun newProject(dir: File = projectDir, block: ProjectInfoBuilder.() -> Unit) {
        val info = ProjectInfoBuilder().apply(block).build()
        newProject(info, dir)
    }

    fun fireEvent(eventId: String, functionName: String, args: Array<out Any>, onFailure: (project: Project, e: Throwable) -> Unit) {
        //if (!Session.eventManager.hasEvent(eventId)) {
        //    Logger.e(ProjectManager::class.simpleName, "Rejecting event call from '$eventId' which is not registered on EventManager")
        //    return
        //}
        projects.values
            .filter { it.isCompiled && it.info.isEnabled && it.isEventCallAllowed(eventId) }
            .let { availableProjects ->
                if (availableProjects.isEmpty()) return
                for (project in availableProjects) {
                    project.callFunction(functionName, args) { e -> onFailure(project, e) }
                }
            }
    }

    fun removeProject(project: Project, removeFiles: Boolean = true) {
        removeProject(project.info.name, removeFiles)
    }

    fun removeProject(name: String, removeFiles: Boolean = true) {
        projects[name]?.let {
            if (removeFiles)
                it.directory.deleteRecursively()
            projects -= name
        }
        eventManager.fireEventWithContext(Events.Project.ProjectDeleteEvent(name))
    }

    internal fun purge() = projects.forEach { (_, u) -> u.destroy(requestUpdate = true) }
}

inline fun <reified T: ProjectEvent> ProjectManager.fireEvent(vararg args: Any, noinline onFailure: (project: Project, e: Throwable) -> Unit = { _, _ -> }) {
    val event = T::class.createInstance().also { event ->
        for ((index, arg) in event.argTypes.withIndex()) {
            //Logger.v("${arg.jvmName}, ${args[index]::class.jvmName}")
            if (arg != args[index]::class)
                error("Passed argument types [${args.joinToString { clazz -> clazz::class.simpleName.toString() }}] do not match the required argument types: [${event.argTypes.joinToString { clazz -> clazz.simpleName.toString() }}]")
        }
    }
    this.fireEvent(event.id, event.functionName, args, onFailure)
}