/*
 * PluginContext.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import java.io.File

sealed interface PluginContext {

    val id: String

    val name: String

    val resourceDir: String

    fun getPlugin(): StarlightPlugin?

    fun getAsset(path: String): File
}