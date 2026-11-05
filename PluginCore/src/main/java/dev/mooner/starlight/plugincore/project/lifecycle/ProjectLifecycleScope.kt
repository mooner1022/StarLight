/*
 * ProjectLifecycleScope.kt created by Minki Moon(mooner1022) on 4/21/23, 2:23 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

abstract class ProjectLifecycleScope internal constructor() : CoroutineScope {

    internal abstract val lifecycle: ProjectLifecycle

    fun launchWhenCompiled(block: suspend CoroutineScope.() -> Unit) {

    }
}

/** Cancelled by the registry when the project is destroyed. */
internal class ProjectLifecycleScopeImpl(
    override val lifecycle: ProjectLifecycle,
    override val coroutineContext: CoroutineContext
): ProjectLifecycleScope()