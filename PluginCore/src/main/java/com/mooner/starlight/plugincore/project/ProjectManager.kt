package com.mooner.starlight.plugincore.project

import java.io.File

class ProjectManager(
    private val projectDir: File
) {

    internal val listeners: MutableMap<String, (projects: List<Project>) -> Unit> = hashMapOf()
    internal var projects: MutableMap<String, Project> = hashMapOf()

    fun getEnabledProjects(): List<Project> {
        return projects.values.filter { it.info.isEnabled }
    }

    fun bindListener(key: String, listener: (projects: List<Project>) -> Unit) {
        if (!listeners.containsKey(key)) {
            listeners[key] = listener
        }
    }

    fun unbindListener(key: String) {
        if (listeners.containsKey(key)) {
            listeners.remove(key)
        }
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
        project.saveConfig()
        if (callListener) {
            callListeners()
        }
    }

    fun newProject(config: ProjectInfo, dir: File = projectDir) {
        projects[config.name] = Project.create(dir, config)
        callListeners()
    }

    fun newProject(dir: File = projectDir, block: ProjectInfoBuilder.() -> Unit) {
        val config = ProjectInfoBuilder().apply(block).build()
        projects[config.name] = Project.create(dir, config)
        callListeners()
    }

    fun callEvent(pluginId: String, eventName: String, args: Array<Any>) {
        val projects = this.projects.values.filter { pluginId in it.info.listeners }
        if (projects.isEmpty()) return
        for (project in projects) {
            project.callEvent(eventName, args)
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
        callListeners()
    }

    private fun callListeners() {
        for ((_, listener) in listeners) {
            listener(projects.values.toList())
        }
    }

    internal fun purge() {
        for ((_, project) in projects) {
            project.saveConfig()
            project.destroy()
        }
    }
}