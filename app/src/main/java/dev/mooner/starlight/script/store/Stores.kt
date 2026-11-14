/*
 * Stores.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.store

import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycleObserver
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Every project's stores, by project ID and name: one [StoreData] for each, whichever compile opened it. */
internal object Stores {

    private val stores: MutableMap<String, StoreData> = ConcurrentHashMap()

    /** Letters and digits of any script, `_`, `-` and `.`, not starting with a dot: a file name on every device. */
    private val NAME = Regex("""^[\p{L}\p{N}_\-][\p{L}\p{N}_.\-]{0,63}$""")

    fun checkName(name: String) =
        require(NAME.matches(name)) {
            "Store name '$name' can't be used: use up to 64 letters, digits, '_', '-' or '.', not starting with '.'"
        }

    fun of(project: Project, name: String): StoreData {
        val home = ProjectHome(project)
        return stores.computeIfAbsent(key(project, name)) { StoreData(name, home) }
            // The project's object may have been made again since, with the same ID.
            .also { it.home = home }
    }

    /** Saves what the project's stores changed, now; failures go to the project's log. */
    fun saveAll(project: Project) {
        val prefix = project.info.id.toString() + "/"
        for ((key, store) in stores)
            if (key.startsWith(prefix))
                store.saveQuietly()
    }

    /**
     * Saves [project]'s stores when the compile that is running ends its version: when the project compiles again, is
     * turned off or is unloaded. One for each compile, as each makes the API objects again.
     */
    fun watch(project: Project) {
        val saver = object : ProjectLifecycleObserver.ExplicitObserver {
            // Before the new code reads them, and while the old version can no longer change them.
            override fun onCompileStart(project: Project) {
                saveAll(project)
                project.getLifecycle().unregisterObserver(this)
            }

            override fun onDisable(project: Project) =
                saveAll(project)

            override fun onDestroy(project: Project) {
                saveAll(project)
                project.getLifecycle().unregisterObserver(this)
            }
        }
        project.getLifecycle().registerObserver(saver)
    }

    private fun key(project: Project, name: String): String =
        "${project.info.id}/$name"

    private class ProjectHome(private val project: Project) : StoreData.StoreHome {
        override val directory: File
            get() = project.directory

        override fun warn(message: String) =
            project.logger.w(message)

        override fun error(message: String) =
            project.logger.e(message)
    }
}
