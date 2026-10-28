package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.version.Version
import java.io.File

sealed interface PluginExclusion {
    data object Disabled: PluginExclusion
    data class IncompatibleApi(val apiVersion: Version): PluginExclusion
    data class MissingDependency(val dependency: PluginDependency): PluginExclusion
    data class ExcludedDependency(val dependency: PluginDependency): PluginExclusion
    data class IncompatibleDependency(val dependency: PluginDependency, val found: Version): PluginExclusion
    data class DuplicateId(val kept: File): PluginExclusion
    data object CircularDependency: PluginExclusion
}

sealed interface PluginState {
    data class Loaded(val plugin: StarlightPlugin): PluginState
    data object Disabled: PluginState
    data object SafeMode: PluginState
    data class Excluded(val exclusion: PluginExclusion): PluginState
    data class Failed(val error: Throwable): PluginState
    data class Invalid(val error: Throwable): PluginState
}

data class InstalledPlugin(
    val file: File,
    val info: PluginInfo?,
    val state: PluginState,
) {
    val isDuplicate: Boolean
        get() = (state as? PluginState.Excluded)?.exclusion is PluginExclusion.DuplicateId

    val key: String
        get() = info?.id?.takeUnless { isDuplicate } ?: file.name
}

sealed interface PluginChange {
    val info: PluginInfo?

    data class Installed(override val info: PluginInfo): PluginChange
    data class Updated(override val info: PluginInfo, val from: Version): PluginChange
    data class Removed(override val info: PluginInfo?): PluginChange
}
