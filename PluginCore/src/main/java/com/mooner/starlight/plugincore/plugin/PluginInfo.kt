package com.mooner.starlight.plugincore.plugin

import com.mooner.starlight.plugincore.Session.json
import com.mooner.starlight.plugincore.version.Version
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
        fun decode(str: String): PluginInfo {
            if (str.isBlank() || !str.startsWith("{") || !str.endsWith("}")) throw IllegalArgumentException("Illegal plugin info string: $str")
            return json.decodeFromString(str)
        }
    }

    fun encode(): String = json.encodeToString(this)

    val fullName = "$name v$version"
}
