/*
 * ProjectLifecycleRegistry.kt created by Minki Moon(mooner1022) on 1/6/24, 7:46 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.lifecycle

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

private val LOG = LoggerFactory.logger {  }

/**
 * Tells observers about each change of a project's state, on the thread that makes it and before the change goes on.
 * So the code of a compile runs only after the observers of the previous one heard [ProjectLifecycle.State.COMPILING],
 * an observer registered while compiling isn't told about that compile, and none is skipped when changes come quickly.
 */
class ProjectLifecycleRegistry(
    private val project: Project
): ProjectLifecycle {

    private val flow: StateFlow<ProjectLifecycle.State> =
        MutableStateFlow(ProjectLifecycle.State.DESTROYED)

    /** Each observer and the scope it was registered for. */
    private val observers: ConcurrentHashMap<ProjectLifecycleObserver, CoroutineScope> = ConcurrentHashMap()

    private var scope: ProjectLifecycleScopeImpl? = null

    /** Lives until the project is destroyed; after that, a new one is made when it is next used. */
    override val lifecycleScope: ProjectLifecycleScope
        get() = synchronized(this) {
            scope?.takeIf(CoroutineScope::isActive)
                ?: ProjectLifecycleScopeImpl(this, Dispatchers.Default + SupervisorJob()).also { scope = it }
        }

    override val state get() = flow.value

    /**
     * [observer] is called from now on, until [scope] ends or it is unregistered. A [ProjectLifecycleObserver.StateObserver]
     * is first told the current state.
     */
    override fun registerObserver(observer: ProjectLifecycleObserver, scope: CoroutineScope) {
        val job = scope.coroutineContext[Job]
        if (job?.isActive == false)
            return
        observers[observer] = scope
        job?.invokeOnCompletion { observers.remove(observer, scope) }
        if (observer is ProjectLifecycleObserver.StateObserver)
            notify(observer) { onUpdate(project, state) }
    }

    override fun unregisterObserver(observer: ProjectLifecycleObserver) {
        observers -= observer
    }

    override suspend fun whileInState(state: ProjectLifecycle.State, block: LifecycleCallback) =
        flow.whileIn(state) { block(project) }

    fun setCurrentState(state: ProjectLifecycle.State) {
        val prev = synchronized(this) {
            val prev = flow.value
            if (prev == state)
                return
            (flow as MutableStateFlow).value = state
            prev
        }
        for ((observer, scope) in observers) {
            if (!scope.isActive) {
                observers.remove(observer, scope)
                continue
            }
            when (observer) {
                is ProjectLifecycleObserver.StateObserver ->
                    notify(observer) { onUpdate(project, state) }
                is ProjectLifecycleObserver.ExplicitObserver ->
                    notifyExplicit(observer, prev, state)
            }
        }
        // Only after the observers heard of it, so the ones in this scope get onDestroy.
        if (state == ProjectLifecycle.State.DESTROYED)
            synchronized(this) {
                scope?.cancel()
                scope = null
            }
    }

    private fun notifyExplicit(
        observer: ProjectLifecycleObserver.ExplicitObserver,
        prev: ProjectLifecycle.State,
        next: ProjectLifecycle.State,
    ) {
        if (prev == ProjectLifecycle.State.COMPILING)
            notify(observer) { onCompileEnd(project) }
        when (next) {
            ProjectLifecycle.State.COMPILING -> notify(observer) { onCompileStart(project) }
            ProjectLifecycle.State.ENABLED   -> notify(observer) { onEnable(project) }
            ProjectLifecycle.State.DISABLED  -> notify(observer) { onDisable(project) }
            ProjectLifecycle.State.DESTROYED -> notify(observer) { onDestroy(project) }
        }
    }

    /** One observer that throws doesn't keep the others from hearing of the change. */
    private inline fun <T: ProjectLifecycleObserver> notify(observer: T, block: T.() -> Unit) {
        try {
            observer.block()
        } catch (e: Exception) {
            LOG.error { "Lifecycle observer of project '${project.info.name}' failed: $e" }
        }
    }
}

internal suspend fun <T> StateFlow<T>.whileIn(state: T, block: suspend () -> Unit) {
    first { it == state }
    coroutineScope {
        val work = launch { block() }
        val watcher = launch {
            first { it != state }
            work.cancel()
        }
        work.join()
        watcher.cancel()
    }
}
