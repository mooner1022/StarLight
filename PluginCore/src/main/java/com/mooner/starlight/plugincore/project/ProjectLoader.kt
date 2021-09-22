package com.mooner.starlight.plugincore.project

import com.mooner.starlight.plugincore.core.Session.Companion.json
import com.mooner.starlight.plugincore.core.Session.Companion.projectManager
import com.mooner.starlight.plugincore.logger.Logger
import kotlinx.serialization.decodeFromString
import java.io.File

class ProjectLoader(
    private val projectDir: File
) {

    companion object {
        private val T = ProjectLoader::class.simpleName!!
    }

    private val projects = projectManager.projects

    init {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            projectDir.mkdirs()
        }
    }

    fun loadProjects() {
        loadProjects(false)
    }

    fun loadProjects(force: Boolean, dir: File = projectDir): List<Project> {
        //projectDir.deleteRecursively()
        if (force || projects.isEmpty()) {
            projects.clear()
            for (folder in dir.listFiles()?.filter { it.isDirectory }?:return emptyList()) {
                val config: ProjectInfo
                try {
                    config = getProjectConfig(folder)
                    val project: Project
                    try {
                        project = Project(folder, config)
                        if (config.isEnabled) {
                            try {
                                project.compile(true)
                            } catch (e: Exception) {
                                Logger.w(T, "Failed to pre-compile project ${config.name}: $e")
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

    private fun getProjectConfig(dir: File): ProjectInfo {
        val result = dir.listFiles()?.find { it.isFile && it.name == "project.json" }
                ?: throw IllegalStateException("Could not find project.json from ${dir.name}")
        try {
            return json.decodeFromString(result.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not parse project.json from ${dir.name}")
        }
    }
}