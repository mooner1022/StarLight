package com.mooner.starlight.plugincore.project

import android.os.Environment
import com.mooner.starlight.plugincore.Session
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectLoader {
    private val projectDir = File(Environment.getExternalStorageDirectory(), "StarLight/projects/")
    private val t = javaClass.simpleName
    private val listeners: MutableList<(projects: List<Project>) -> Unit> = mutableListOf()
    private var projects: MutableList<Project> = mutableListOf()

    init {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            projectDir.mkdirs()
        }
    }

    fun bind(listener: (projects: List<Project>) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun unbind(listener: (projects: List<Project>) -> Unit) {
        if (listeners.contains(listener)) listeners.remove(listener)
    }

    fun newProject(dir: File = projectDir, config: ProjectConfig) {
        projects.add(Project.create(dir, config))
        for (listener in listeners) {
            listener(projects)
        }
    }

    fun loadProjects(dir: File = projectDir): List<Project> {
        projects.clear()
        for (folder in dir.listFiles()?.filter { it.isDirectory }?:return emptyList()) {
            val config: ProjectConfig
            try {
                config = getProjectConfig(folder)
                projects.add(Project(folder, config))
            } catch (e: IllegalStateException) {
                Session.logger.e(t, e.toString())
            }
        }
        return projects.toList()
    }

    fun getProjectConfig(dir: File): ProjectConfig {
        val result = dir.listFiles()?.find { it.isFile && it.name == "project.json" }
                ?: throw IllegalStateException("Could not find project.json from ${dir.name}")
        try {
            return Json.decodeFromString(result.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not parse project.json from ${dir.name}")
        }
    }
}