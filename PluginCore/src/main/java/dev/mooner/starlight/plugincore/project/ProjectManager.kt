/*
 * ProjectManager.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import java.io.File
import java.util.*
import kotlin.reflect.KClass

typealias ProjectFailureCallback = (project: Project, e: Throwable) -> Unit

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

    @JvmOverloads
    fun getProjectByName(name: String, ignoreCase: Boolean = false): Project? {
        return if (ignoreCase) {
            projects.keys
                .find { it.lowercase() == name.lowercase() }
                ?.let(projects::get)
        } else {
            projects[name]
        }
    }

    fun getProjectById(id: String): Project? {
        val uuid = UUID.fromString(id)
        return projects.values.find { it.info.id == uuid }
    }

    fun updateProjectInfo(name: String, callListener: Boolean = false, block: ProjectInfo.() -> Unit) {
        val project = projects[name] ?: throw IllegalArgumentException("Cannot find project [$name]")
        project.info.block()
        project.saveInfo()
        if (callListener)
            project.requestUpdate()
    }

    fun newProject(info: ProjectInfo, dir: File = projectDir, events: Map<String, ProjectEvent>) {
        ProjectImpl.create(dir, info, events).also { project ->
            projects[info.name] = project
            EventHandler.fireEventWithScope(Events.Project.Create(project))
        }
    }

    fun newProject(dir: File = projectDir, events: Map<String, ProjectEvent>, block: ProjectInfoBuilder.() -> Unit) {
        val info = ProjectInfoBuilder().apply(block).build()
        newProject(info, dir, events)
    }

    fun fireEvent(eventClass: KClass<ProjectEvent>, args: Array<out Any>, onFailure: ProjectFailureCallback) {
        if (!Session.isInitComplete)
            return

        val eventId = ProjectEventManager.validateAndGetEventID(eventClass)
            ?: error("Non-registered event: ${eventClass.qualifiedName}")

        val targets = projects.values.filter { project ->
            project.isCompiled && project.info.isEnabled && project.isEventCallAllowed(eventId)
        }
        if (targets.isEmpty())
            return

        val event = eventClass.getInstance()
        val actualTypes = event.argTypes
            .map { it.type }
        if (actualTypes.size < args.size)
            error("Argument length mismatch, required: ${actualTypes.size}, provided: ${args.size}")

        for (i in args.indices) {
            val eArg = actualTypes[i]
            if (!eArg.javaObjectType.isInstance(args[i]))
                error("Argument type mismatch on position ${i}, required: ${eArg}, provided: ${args[i]::class}")
        }

        for (project in targets)
            project.callFunction(event.functionName, args) { e -> onFailure(project, e) }
    }

    fun removeProject(project: Project, removeFiles: Boolean = true) =
        removeProject(project.info.name, removeFiles)

    fun removeProject(name: String, removeFiles: Boolean = true) {
        val project = projects[name] ?: return

        if (project.isCompiled)
            project.destroy()
        if (removeFiles)
            project.directory.deleteRecursively()
        projects -= name
        EventHandler.fireEventWithScope(Events.Project.Delete(name, project.info.id))
    }

    internal fun purge() =
        projects.forEach { (_, u) -> u.destroy(requestUpdate = false) }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T: ProjectEvent> ProjectManager.fireEvent(vararg args: Any, noinline onFailure: ProjectFailureCallback = { _, _ -> }) {
    this.fireEvent(eventClass = T::class as KClass<ProjectEvent>, args, onFailure)
}