/*
 * ProjectLifecycleScope.kt created by Minki Moon(mooner1022) on 4/21/23, 2:23 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

abstract class ProjectLifecycleScope internal constructor() : CoroutineScope {

    internal abstract val lifecycle: ProjectLifecycle

    fun launchWhenCompiled(block: suspend CoroutineScope.() -> Unit) {

    }
}

internal class ProjectLifecycleScopeImpl(
    override val lifecycle: ProjectLifecycle,
    override val coroutineContext: CoroutineContext
): ProjectLifecycleScope(), ProjectLifecycleObserver.StateObserver {

    override fun onUpdate(project: Project, state: ProjectLifecycle.State) {
        println("Project LifeCycle state update: $state")
        if (state == ProjectLifecycle.State.DESTROYED) {
            lifecycle.unregisterObserver(this)
            coroutineContext.cancel()
        }
    }
}