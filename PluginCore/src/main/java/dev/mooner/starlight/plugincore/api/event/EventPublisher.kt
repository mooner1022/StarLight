/*
 * EventPublisher.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.api.event

import dev.mooner.starlight.plugincore.project.lifecycle.ProjectLifecycle

interface CallbackTest {

    fun call(vararg args: Any)
}

abstract class EventPublisher(
    lifecycle: ProjectLifecycle
) {

    private val listeners: Set<CallbackTest> = hashSetOf()

    protected fun fireEvent(name: String, vararg args: Any) {

    }

    init {
        lifecycle.lifecycleScope
        listeners.forEach { it.call(0, 1, 2 ,3) }
    }
}