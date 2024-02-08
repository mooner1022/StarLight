/*
 * ProjectLifecycle.kt created by Minki Moon(mooner1022) on 4/21/23, 1:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.CoroutineScope

typealias LifecycleCallback = suspend (project: Project) -> Unit

interface ProjectLifecycle {

    val lifecycleScope: ProjectLifecycleScope

    val state: State

    fun registerObserver(observer: ProjectLifecycleObserver, scope: CoroutineScope = lifecycleScope)

    fun unregisterObserver(observer: ProjectLifecycleObserver)

    suspend fun whileInState(state: State, block: LifecycleCallback)

    enum class State {
        COMPILING,
        ENABLED,
        DISABLED,
        DESTROYED
    }

    enum class Event {
        ON_COMPILE_START,
        ON_COMPILE_END,
        ON_ENABLE,
        ON_DISABLE,
        ON_DESTROY;

        fun getTargetState(): State {
            return when(this) {
                ON_COMPILE_START ->
                    State.COMPILING
                ON_COMPILE_END, ON_DISABLE ->
                    State.DISABLED
                ON_ENABLE ->
                    State.ENABLED
                ON_DESTROY ->
                    State.DESTROYED
            }
        }
    }
}