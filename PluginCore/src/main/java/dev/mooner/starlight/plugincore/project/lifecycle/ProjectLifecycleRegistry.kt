/*
 * ProjectLifecycleRegistry.kt created by Minki Moon(mooner1022) on 1/6/24, 7:46 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.lazyMutable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ProjectLifecycleRegistry(
    private val project: Project
): ProjectLifecycle {

    override var lifecycleScope: ProjectLifecycleScope by lazyMutable(::createScope)
        private set

    private val flow: StateFlow<ProjectLifecycle.State> =
        MutableStateFlow(ProjectLifecycle.State.DESTROYED)

    private var observers: ConcurrentMap<ProjectLifecycleObserver, Job> = ConcurrentHashMap()

    override val state get() = flow.value

    override fun registerObserver(observer: ProjectLifecycleObserver, scope: CoroutineScope) {
        var prevState: ProjectLifecycle.State? = null
        flow
            .buffer(Channel.UNLIMITED)
            .onEach { state ->
                if (observer is ProjectLifecycleObserver.StateObserver)
                    observer.onUpdate(project, state)
                else {
                    observer as ProjectLifecycleObserver.ExplicitObserver
                    getExplicitCallbackByState(observer, prevState, state)
                        ?.invoke(project)
                    prevState = state
                }
            }
            .launchIn(scope)
            .also { observers[observer] = it }
    }

    override fun unregisterObserver(observer: ProjectLifecycleObserver) {
        observers[observer]?.let { job ->
            job.cancel()
            observers -= observer
        }
    }

    override suspend fun whileInState(state: ProjectLifecycle.State, block: LifecycleCallback) = coroutineScope {
        if (flow.value == state) {
            block(project)
            flow.collect { nState ->
                if (nState != state)
                    cancel()
            }
        } else {
            flow.collect { nState ->
                if (nState == state)
                    block(project)
                else
                    cancel()
            }
        }
    }

    fun setCurrentState(state: ProjectLifecycle.State) {
        if (this.state == state)
            return
        (flow as MutableStateFlow).tryEmit(state)
        println("setCurrentState $state")
        if (state == ProjectLifecycle.State.DESTROYED) {
            observers = ConcurrentHashMap()
            lifecycleScope = createScope()
        }
    }

    private fun getExplicitCallbackByState(
        observer: ProjectLifecycleObserver.ExplicitObserver,
        prev: ProjectLifecycle.State?,
        next: ProjectLifecycle.State
    ): LifecycleCallback? {
        return when {
            next == ProjectLifecycle.State.COMPILING ->
                observer::onCompileStart
            next == ProjectLifecycle.State.ENABLED ->
                observer::onEnable
            next == ProjectLifecycle.State.DISABLED ->
                observer::onDisable
            next == ProjectLifecycle.State.DESTROYED ->
                observer::onDestroy
            prev == ProjectLifecycle.State.COMPILING && next != ProjectLifecycle.State.COMPILING ->
                observer::onCompileEnd
            else -> null
        }
    }

    private fun createScope(): ProjectLifecycleScope {
        return ProjectLifecycleScopeImpl(
            lifecycle = this,
            coroutineContext = Dispatchers.Default + SupervisorJob()
        ).also {
            registerObserver(it, it)
        }
    }
}