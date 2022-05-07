/*
 * PluginInfo.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.version.Version
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    @SerialName("main_class")
    val mainClass: String,
    val version: Version,
    @SerialName("api_version")
    val apiVersion: Version,
    val dependency: List<PluginDependency> = listOf(),
    @SerialName("soft_dependency")
    val softDependency: List<PluginDependency> = listOf(),
    val authors: List<String>,
    val description: String
) {
    companion object {
        fun decodeFromString(str: String): PluginInfo {
            if (str.isBlank() || !str.startsWith("{") || !str.endsWith("}")) throw IllegalArgumentException("Illegal plugin info string: $str")
            return json.decodeFromString(str)
        }
    }

    fun encode(): String = json.encodeToString(this)

    val fullName = "$name v$version"
}
