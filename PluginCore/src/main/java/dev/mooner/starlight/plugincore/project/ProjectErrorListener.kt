/*
 * ProjectErrorListener.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

/**
 * Told when code of a project fails on its worker with an error nothing caught, after the error is logged and before
 * the project's setting for errors is applied. The app's hook for a script's `onUncaughtError`; not for plugins.
 *
 * @since 1.2.0
 * @suppress
 */
fun interface ProjectErrorListener {

    /** [scope] is the compiled version the code ran in; [functionName] and [args] are the called function's, null for code run with `runEvent`. */
    fun onEventError(project: Project, scope: Any, error: Throwable, functionName: String?, args: Array<out Any>?)

    companion object {

        @Volatile
        var current: ProjectErrorListener? = null
    }
}
