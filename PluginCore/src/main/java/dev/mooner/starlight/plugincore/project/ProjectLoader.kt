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

    /** [precompile]: compile the enabled projects; a safe start leaves them off until the user acts. */
    fun loadProjects(force: Boolean, dir: File = projectDir, precompile: Boolean = true): List<Project> {
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
                        if (info.isEnabled && precompile)
                            precompiledNames += info.name
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        Logger.e(T, e)
                        continue
                    }
                    projects[info.name] = project
                } catch (e: Exception) {
                    // Whatever went wrong with one project, a language of a plugin that fails to make itself
                    // included, the others load; this one is left out, as one whose language is missing.
                    Logger.e(T, "Failed to load project '${folder.name}': $e")
                } catch (e: LinkageError) {
                    Logger.e(T, "Failed to load project '${folder.name}': $e")
                }
            }
            for (name in precompiledNames) {
                val project = projects[name] ?: continue
                if (project.info.isEnabled)
                    precompile(project)
            }
        }
        return projects.values.toList()
    }

    /**
     * Loads one project folder that isn't loaded yet, such as an imported one, and announces it the way
     * [ProjectManager.newProject] does. An enabled project is compiled first, as [loadProjects] does.
     */
    fun loadProject(dir: File): Project {
        val info = getProjectInfo(dir)
        require(projectManager.getProjectByName(info.name) == null) { "A project named '${info.name}' already exists" }
        require(projectManager.getProjectById(info.id.toString()) == null) { "A project with id ${info.id} already exists" }
        val project = ProjectImpl.loadFrom(dir, info)
        projects[info.name] = project
        if (info.isEnabled)
            precompile(project)
        EventHandler.fireEventWithScope(Events.Project.Create(project))
        return project
    }

    /**
     * Compiles an enabled project so it runs right away, or disables it if that fails. One that fails because its
     * language's code or native library didn't load stays on: that is the app's or a plugin's fault and not the
     * script's, and may be gone at the next start.
     */
    private fun precompile(project: Project) {
        try {
            project.compile(true)
        } catch (e: Exception) {
            Logger.w(T, "Failed to pre-compile project ${project.info.name}: $e")
            project.info.isEnabled = false
            project.saveInfo()
        } catch (e: LinkageError) {
            Logger.w(T, "Failed to pre-compile project ${project.info.name}, which stays on for the next start: $e")
        }
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