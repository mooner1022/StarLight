package com.mooner.starlight.plugincore.project

import android.os.Environment
import com.mooner.starlight.plugincore.core.Session.Companion.json
import com.mooner.starlight.plugincore.logger.Logger
import kotlinx.serialization.decodeFromString
import java.io.File

class ProjectLoader {
    companion object {
        private val T = ProjectLoader::class.simpleName!!
    }

    @Suppress("DEPRECATION")
    private val projectDir = File(Environment.getExternalStorageDirectory(), "StarLight/projects/")
    private val listeners: MutableMap<String, (projects: List<Project>) -> Unit> = hashMapOf()
    private var projects: MutableList<Project> = mutableListOf()

    init {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            projectDir.mkdirs()
        }
    }

    fun loadProjects() {
        loadProjects(false)
    }

    fun getEnabledProjects(): List<Project> {
        return projects.filter { it.config.isEnabled }
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
        return this.projects
    }

    fun getProject(name: String): Project? {
        return projects.find { it.config.name == name }
    }

    fun updateProjectConfig(name: String, callListener: Boolean = false, block: ProjectConfig.() -> Unit) {
        val project = projects.find { it.config.name == name }?: throw IllegalArgumentException("Cannot find project [$name]")
        project.config.block()
        project.saveConfig()
        if (callListener) {
            callListeners()
        }
    }

    fun newProject(config: ProjectConfig, dir: File = projectDir) {
        projects.add(Project.create(dir, config))
        callListeners()
    }

    fun newProject(dir: File = projectDir, block: ProjectConfigBuilder.() -> Unit) {
        val config = ProjectConfigBuilder().apply(block).build()
        projects.add(Project.create(dir, config))
        callListeners()
    }

    fun loadProjects(force: Boolean, dir: File = projectDir): List<Project> {
        //projectDir.deleteRecursively()
        if (force || projects.isEmpty()) {
            projects.clear()
            for (folder in dir.listFiles()?.filter { it.isDirectory }?:return emptyList()) {
                val config: ProjectConfig
                try {
                    config = getProjectConfig(folder)
                    val project: Project
                    try {
                        project = Project(folder, config)
                        if (config.isEnabled) {
                            try {
                                project.compile(true)
                            } catch (e: Exception) {
                                Logger.d(T, "Failed to pre-compile project ${config.name}: $e")
                                continue
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        Logger.e(T, e.toString())
                        continue
                    }
                    projects.add(project)
                } catch (e: IllegalStateException) {
                    Logger.e(T, "Failed to load project: $e")
                } catch (e: IllegalArgumentException) {
                    Logger.e(T, "Failed to load project: $e")
                }
            }
        }
        return projects.toList()
    }

    fun callEvent(pluginId: String, eventName: String, args: Array<Any>) {
        val projects = this.projects.filter { pluginId in it.config.listeners }
        if (projects.isEmpty()) return
        for (project in projects) {
            project.callEvent(eventName, args)
        }
    }

    private fun getProjectConfig(dir: File): ProjectConfig {
        val result = dir.listFiles()?.find { it.isFile && it.name == "project.json" }
                ?: throw IllegalStateException("Could not find project.json from ${dir.name}")
        try {
            return json.decodeFromString(result.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not parse project.json from ${dir.name}")
        }
    }

    private fun callListeners() {
        for ((_, listener) in listeners) {
            listener(projects)
        }
    }
}