package com.mooner.starlight.plugincore.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PluginConfig(
    val name: String,
    @SerialName("main_class")
    val mainClass: String,
    val version: String,
    @SerialName("api_version")
    val apiVersion: String,
    val depend: List<String> = listOf(),
    @SerialName("soft_depend")
    val softDepend: List<String> = listOf(),
    val authors: List<String>,
    val description: String
) {
    companion object {
        fun decode(str: String): PluginConfig {
            if (str.isBlank() || !str.startsWith("{") || !str.endsWith("}")) throw IllegalArgumentException("Illegal String argument: $str")
            return Json.decodeFromString(str)
        }
    }

    fun encode(): String = Json.encodeToString(this)

    val fullName = "$name v$version"
}
