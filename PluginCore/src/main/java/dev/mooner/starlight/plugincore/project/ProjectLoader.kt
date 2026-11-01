/*
 * ProjectLoader.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import android.util.AtomicFile
import androidx.core.util.readText
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.Session.projectManager
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.logger.internal.Logger
import java.io.File
import java.io.FileNotFoundException

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

    /**
     * Loads one project folder that isn't loaded yet, such as an imported one, and announces it the way
     * [ProjectManager.newProject] does.
     */
    fun loadProject(dir: File): Project {
        val info = getProjectInfo(dir)
        require(projectManager.getProjectByName(info.name) == null) { "A project named '${info.name}' already exists" }
        require(projectManager.getProjectById(info.id.toString()) == null) { "A project with id ${info.id} already exists" }
        val project = ProjectImpl.loadFrom(dir, info)
        projects[info.name] = project
        EventHandler.fireEventWithScope(Events.Project.Create(project))
        return project
    }

    /** Reads a project folder's project.json. */
    fun readProjectInfo(dir: File): ProjectInfo =
        getProjectInfo(dir)

    private fun getProjectInfo(dir: File): ProjectInfo {
        val raw = try {
            AtomicFile(File(dir, "project.json")).readText()
        } catch (_: FileNotFoundException) {
            throw IllegalStateException("Could not find project.json from ${dir.name}")
        }
        try {
            return json.decodeFromString(raw)
        } catch (_: Exception) {
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