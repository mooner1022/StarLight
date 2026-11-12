/*
 * ScriptCallbacks.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script

import dev.mooner.starlight.languages.ScriptErrors
import dev.mooner.starlight.plugincore.RuntimeClassLoader
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.script.errors.UncaughtErrors
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a script's callback the way its timers run: on the project's workers, as a task named [name] in a warning about
 * one that runs long, with plugins' classes reachable to `Java.type`, and an error logged as [failure] says rather than
 * thrown, so it doesn't unload the project.
 */
internal fun Project.runCallback(name: String, failure: (description: String) -> String, block: () -> Unit): Job {
    // The version the callback is of: an error after a compile replaced it isn't the new version's.
    val scope = getScope()
    return launch(CoroutineName(name)) {
        val thread = Thread.currentThread()
        val original = thread.contextClassLoader
        thread.contextClassLoader = RuntimeClassLoader(original)
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(failure(ScriptErrors.describe(e, directory)))
            if (getScope() === scope)
                UncaughtErrors.report(info.id, e, UncaughtErrors.Origin.EXCEPTION, name)
        } finally {
            thread.contextClassLoader = original
        }
    }
}
