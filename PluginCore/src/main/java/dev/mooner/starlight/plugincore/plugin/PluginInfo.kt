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
    val id                  : String,
    val name                : String,
    @SerialName("main_class")
    val mainClass           : String,
    @SerialName("custom_classloader")
    val customClassLoader   : String? = null,
    val version             : Version,
    val assetRevision       : Int = 0,
    @SerialName("api_version")
    val apiVersion          : Version,
    val dependency          : List<PluginDependency> = listOf(),
    @SerialName("uses_native_lib")
    val usesNativeLibrary   : Boolean = false,
    val authors             : List<String>,
    val description         : String
) {

    val fullName = "$name v$version"

    val softDependency
        get() = dependency.filter(PluginDependency::isOptional)

    fun encode(): String = json.encodeToString(this)

    companion object {
        fun decodeFromString(str: String): PluginInfo {
            if (str.isBlank() || !str.startsWith("{") || !str.endsWith("}")) throw IllegalArgumentException("Illegal plugin info string: $str")
            return json.decodeFromString(str)
        }
    }

}
