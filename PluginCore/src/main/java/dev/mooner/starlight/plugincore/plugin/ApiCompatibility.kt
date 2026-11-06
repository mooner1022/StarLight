package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.version.Version

sealed interface ApiCompatibility {

    data object Compatible: ApiCompatibility

    data class RequiresNewerApp(val required: Version, val current: Version): ApiCompatibility

    data class Outdated(val apiVersion: Version, val minimum: Version): ApiCompatibility

    val isCompatible: Boolean
        get() = this == Compatible

    companion object {

        @JvmStatic
        @JvmOverloads
        fun of(
            apiVersion : Version,
            current    : Version = Info.PLUGINCORE_VERSION,
            minimum    : Version = Info.MIN_PLUGIN_API_VERSION,
        ): ApiCompatibility =
            when {
                apiVersion > current -> RequiresNewerApp(apiVersion, current)
                apiVersion < minimum -> Outdated(apiVersion, minimum)
                else                 -> Compatible
            }
    }
}
