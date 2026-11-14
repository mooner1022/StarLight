/*
 * ScriptVersion.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import java.util.concurrent.ConcurrentHashMap

/**
 * Which compile of a project a deferred callback belongs to, so one queued by code that a compile has since replaced,
 * or that was unloaded, is dropped rather than run.
 *
 * The project's scope can't tell: a script's top-level code runs while it compiles, before the new scope is the
 * project's, so a callback queued from there would look stale as soon as the compile finished. A marker that changes
 * when a compile starts, and when the project is destroyed, is the same for the top-level code and for the events that
 * follow, and differs for everything after the next compile.
 */
internal object ScriptVersion {

    private val markers: MutableMap<ProjectID, Any> = ConcurrentHashMap()

    /** The marker of the code [project] is running, or compiling now. */
    fun of(project: Project): Any {
        markers[project.info.id]?.let { return it }
        synchronized(this) {
            return markers.getOrPut(project.info.id) {
                project.getLifecycle().registerObserver(Observer(project.info.id))
                Any()
            }
        }
    }

    /** Whether [marker], from [of], is still the marker of [project]'s code. */
    fun isCurrent(project: Project, marker: Any): Boolean =
        markers[project.info.id] === marker

    /** Stays for the project's life, as one per project. */
    private class Observer(private val id: ProjectID) : ProjectLifecycleObserver.ExplicitObserver {

        override fun onCompileStart(project: Project) {
            markers[id] = Any()
        }

        override fun onDestroy(project: Project) {
            markers[id] = Any()
        }
    }
}
