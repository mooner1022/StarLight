/*
 * ProjectLifecycle.kt created by Minki Moon(mooner1022) on 4/21/23, 1:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private typealias LifecycleCallback = suspend (project: Project) -> Unit

class ProjectLifecycle(
    private val project: Project
) {

    var lifecycleScope: ProjectLifecycleScope? = null
        private set

    private val flow: StateFlow<State> =
        MutableStateFlow(State.DESTROYED)

    private val observers: ConcurrentMap<ProjectLifecycleObserver, Job> = ConcurrentHashMap()

    val state get() = flow.value

    fun registerObserver(observer: ProjectLifecycleObserver) {
        var prevState: State? = null
        flow
            .buffer(Channel.UNLIMITED)
            .onEach { state ->
                if (observer is ProjectLifecycleObserver.StateObserver)
                    observer.onUpdate(project, state)
                else {
                    observer as ProjectLifecycleObserver.ExplicitObserver
                    if (prevState == State.COMPILING)
                        observer.onCompileEnd(project)
                    getExplicitCallbackByState(observer, state)
                        .invoke(project)
                    prevState = state
                }
            }
            .launchIn(checkOrCreateScope())
            .also { observers[observer] = it }
    }

    fun unregisterObserver(observer: ProjectLifecycleObserver) {
        observers[observer]?.let { job ->
            job.cancel()
            observers -= observer
        }
    }

    fun whileInState(state: State, block: LifecycleCallback) {
        checkOrCreateScope().launch {
            if (flow.value == state)
                block(project)
            flow.collect { nState ->
                if (nState != state)
                    cancel()
            }
        }
    }

    private fun getExplicitCallbackByState(observer: ProjectLifecycleObserver.ExplicitObserver, state: State): LifecycleCallback {
        return when(state) {
            State.COMPILING ->
                observer::onCompileStart
            State.ENABLED ->
                observer::onEnable
            State.DISABLED ->
                observer::onDisable
            State.DESTROYED ->
                observer::onDestroy
        }
    }

    private fun checkOrCreateScope(): ProjectLifecycleScope {
        if (lifecycleScope == null)
            lifecycleScope = ProjectLifecycleScopeImpl(
                lifecycle = this,
                coroutineContext = Dispatchers.Default + SupervisorJob()
            )
        return lifecycleScope!!
    }

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