/*
 * NeonWaits.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.neonjs

import dev.mooner.neonjs.NeonContext
import dev.mooner.starlight.plugincore.project.Project

/**
 * Runs [block], which waits for another thread, letting a NeonJS script's context go meanwhile. A context is used by
 * one thread at a time and a script holds its own while it calls into the host: if what it waits for needs that
 * context too, both would wait forever. Outside a NeonJS script, as in Rhino, which has no such lock, [block] just
 * runs.
 */
internal fun <R> whileWaitingForAnotherThread(block: () -> R): R {
    val context = NeonContext.current() ?: return block()
    return context.releasing(block)
}

/**
 * [Project.compile] for a script that asks for it. The compile runs on another thread and calls the running code's
 * `onStartCompile`, in the context the calling script may be holding: the script's own project, or one it shares
 * nothing with but the wait.
 */
internal fun Project.compileForScript(throwException: Boolean = false): Boolean =
    whileWaitingForAnotherThread { compile(throwException) }
