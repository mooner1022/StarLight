/*
 * ProjectLoader.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.logger.internal.Logger
import kotlinx.serialization.decodeFromString
import java.io.File

class ProjectLoader(
    private val projectDir: File
) {
    private val projects = projectManager.projects

    fun loadProjects() {
        loadProjects(false)
    }

    fun loadProjects(force: Boolean, dir: File = projectDir): List<Project> {
        //projectDir.deleteRecursively()
        if (force || projects.isEmpty()) {
            projects.clear()
            val precompiledNames: MutableSet<String> = hashSetOf()
            for (folder in dir.listFiles()?.filter { it.isDirectory } ?: return emptyList()) {
                val info: ProjectInfo
                try {
                    info = getProjectInfo(folder)
                    val project: Project
                    try {
                        project = ProjectImpl.loadFrom(folder, info)
                        if (info.isEnabled)
                            precompiledNames += info.name
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        Logger.e(T, e)
                        continue
                    }
                    projects[info.name] = project
                } catch (e: IllegalStateException) {
                    Logger.e(T, "Failed to load project '${folder.name}': $e")
                } catch (e: IllegalArgumentException) {
                    Logger.e(T, "Failed to load project '${folder.name}': $e")
                }
            }
            for (name in precompiledNames) {
                val project = projects[name] ?: continue
                val info    = project.info
                if (info.isEnabled) {
                    try {
                        project.compile(true)
                    } catch (e: Exception) {
                        Logger.w(T, "Failed to pre-compile project ${info.name}: $e")
                        info.isEnabled = false
                        project.saveInfo()
                    }
                }
            }
        }
        return projects.values.toList()
    }

    private fun getProjectInfo(dir: File): ProjectInfo {
        val result = dir.listFiles()?.find { it.isFile && it.name == "project.json" }
            ?: throw IllegalStateException("Could not find project.json from ${dir.name}")
        try {
            return json.decodeFromString(result.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse project.json from ${dir.name}")
        }
    }

    init {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            projectDir.mkdirs()
        }
    }

    companion object {
        private val T = ProjectLoader::class.simpleName!!
    }
}