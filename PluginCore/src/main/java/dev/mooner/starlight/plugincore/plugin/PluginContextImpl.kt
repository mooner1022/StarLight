/*
 * PluginContextImpl.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.utils.currentThread
import java.io.File

internal class PluginContextImpl(
    override val id: String,
    override val name: String,
    override val resourceDir: String,
) : PluginContext {

    init {
        if (!getInitializer().startsWith(StarlightPlugin::class.qualifiedName ?: "null") &&
            !getInitializer().startsWith(Session::class.qualifiedName ?: "null"))
            throw ExceptionInInitializerError("PluginContext should be initialized inside a qualified StarLight package.")
    }

    override fun getAsset(path: String): File =
        File(resourceDir, "assets").resolve(path)

    override fun getPlugin(): StarlightPlugin? =
        Session.pluginManager.getPluginById(id)

    private fun getInitializer(): String {
        val trace = currentThread.stackTrace
        return trace[4].className
    }
}