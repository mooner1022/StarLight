package com.mooner.starlight.plugincore.project

import android.os.Environment
import com.mooner.starlight.plugincore.logger.Logger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectLoader {
    companion object {
        private val T = ProjectLoader::class.simpleName!!
    }

    @Suppress("DEPRECATION")
    private val projectDir = File(Environment.getExternalStorageDirectory(), "StarLight/projects/")
    private val listeners: MutableList<(projects: List<Project>) -> Unit> = mutableListOf()
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

    fun bind(listener: (projects: List<Project>) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun unbind(listener: (projects: List<Project>) -> Unit) {
        if (listeners.contains(listener)) listeners.remove(listener)
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
        project.flush()
        if (callListener) {
            callListeners()
        }
    }

    fun newProject(config: ProjectConfig, dir: File = projectDir) {
        projects.add(Project.create(dir, config))
        callListeners()
    }

    fun newProject(dir: File = projectDir, block: MutableProjectConfig.() -> Unit) {
        val config = MutableProjectConfig().apply(block).toProjectConfig()
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
                    Logger.e(T, e.toString())
                }
            }
        }
        return projects.toList()
    }

    private fun getProjectConfig(dir: File): ProjectConfig {
        val result = dir.listFiles()?.find { it.isFile && it.name == "project.json" }
                ?: throw IllegalStateException("Could not find project.json from ${dir.name}")
        try {
            return Json.decodeFromString(result.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not parse project.json from ${dir.name}")
        }
    }

    private fun callListeners() {
        for (listener in listeners) {
            listener(projects)
        }
    }
}