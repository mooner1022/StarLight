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

/**
 * A plugin's information, from its package: `plugin.info`.
 */
@Serializable
data class PluginInfo(
    /**
     * The plugin's ID, such as `discord`: the prefix of its event IDs and what `Plugins.ofId` takes.
     */
    val id                  : String,
    /**
     * The plugin's name, as the app shows it.
     */
    val name                : String,
    /** @suppress */
    @SerialName("main_class")
    val mainClass           : String,
    /** @suppress */
    @SerialName("custom_classloader")
    val customClassLoader   : String? = null,
    /**
     * The plugin's version; `String(info.version)` gives it as text, such as `1.2.0`.
     */
    val version             : Version,
    /** @suppress */
    val assetRevision       : Int = 0,
    /** @suppress */
    @SerialName("api_version")
    val apiVersion          : Version,
    /** @suppress */
    val dependency          : List<PluginDependency> = listOf(),
    /** @suppress */
    @SerialName("uses_native_lib")
    val usesNativeLibrary   : Boolean = false,
    /**
     * The plugin's authors.
     */
    val authors             : List<String>,
    /**
     * What the plugin does, as its author wrote it.
     */
    val description         : String,
    /** @suppress */
    val category            : PluginCategory = PluginCategory.DEFAULT,
) {

    /**
     * The name and version together, such as `Discord v1.2.0`.
     */
    val fullName = "$name v$version"

    /** @suppress */
    val softDependency
        get() = dependency.filter(PluginDependency::isOptional)

    /** @suppress */
    fun encode(): String = json.encodeToString(this)

    companion object {
        fun decodeFromString(str: String): PluginInfo {
            if (str.isBlank() || !str.startsWith("{") || !str.endsWith("}")) throw IllegalArgumentException("Illegal plugin info string: $str")
            return json.decodeFromString(str)
        }
    }
}
