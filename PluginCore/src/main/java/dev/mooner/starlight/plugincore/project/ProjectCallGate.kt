/*
 * ProjectCallGate.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

/**
 * Decides, as a project's worker takes a call made with [Project.callFunction], whether the function runs.
 *
 * StarLight sets one to hold back a chat message a script waits for as an answer (`Chat.waitFor`) from `onMessage`
 * and the other functions that would get it, whoever delivers the message: the app, its debug room, or a plugin
 * calling `callFunction("onMessage", …)`. The gate is asked on the worker, right before the function would run, so it
 * sees calls in the order they run. It has to be quick. One that throws lets the call run.
 *
 * @since 1.2.0
 * @suppress
 */
fun interface ProjectCallGate {

    /**
     * Whether [project]'s function [functionName] runs with [args]. `false` skips the call, without an error.
     */
    fun allows(project: Project, functionName: String, args: Array<out Any>): Boolean

    companion object {

        /** The gate every call goes through; null lets every call run. StarLight sets it as it starts. */
        @Volatile
        var current: ProjectCallGate? = null
    }
}
