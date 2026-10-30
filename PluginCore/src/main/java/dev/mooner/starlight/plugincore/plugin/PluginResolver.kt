package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.plugin.PluginDependency.Companion.VERSION_ANY
import dev.mooner.starlight.plugincore.version.Version
import java.util.PriorityQueue

internal object PluginResolver {

    data class Resolution(
        val order: List<String>,
        val excluded: Map<String, PluginExclusion>
    )

    fun resolve(
        infos: Map<String, PluginInfo>,
        coreVersion: Version,
        disabled: Set<String> = emptySet(),
        minimumApiVersion: Version = Info.MIN_PLUGIN_API_VERSION,
    ): Resolution {
        val excluded: MutableMap<String, PluginExclusion> = linkedMapOf()

        for ((id, info) in infos) {
            if (id in disabled) {
                excluded[id] = PluginExclusion.Disabled
                continue
            }
            val compatibility = ApiCompatibility.of(info.apiVersion, coreVersion, minimumApiVersion)
            if (!compatibility.isCompatible)
                excluded[id] = PluginExclusion.IncompatibleApi(info.apiVersion, compatibility)
        }

        var updated = true
        while (updated) {
            updated = false
            for ((id, info) in infos) {
                if (id in excluded)
                    continue
                val exclusion = info.dependency
                    .filterNot(PluginDependency::isOptional)
                    .firstNotNullOfOrNull { checkDependency(it, infos, excluded) }
                    ?: continue
                excluded[id] = exclusion
                updated = true
            }
        }

        val candidates = infos.keys - excluded.keys
        val dependents: Map<String, MutableList<String>> = candidates.associateWith { arrayListOf() }
        val inDegrees: MutableMap<String, Int> = candidates.associateWithTo(hashMapOf()) { 0 }
        for (id in candidates) {
            for (dependency in infos[id]!!.dependency) {
                if (dependency.pluginId !in candidates)
                    continue
                dependents[dependency.pluginId]!! += id
                inDegrees[id] = inDegrees[id]!! + 1
            }
        }

        val queue = PriorityQueue(inDegrees.filterValues { it == 0 }.keys)
        val order: MutableList<String> = arrayListOf()
        while (queue.isNotEmpty()) {
            val id = queue.poll()!!
            order += id
            for (dependent in dependents[id]!!) {
                val inDegree = inDegrees[dependent]!! - 1
                inDegrees[dependent] = inDegree
                if (inDegree == 0)
                    queue += dependent
            }
        }

        for (id in candidates.sorted()) {
            if (id !in order)
                excluded[id] = PluginExclusion.CircularDependency
        }
        return Resolution(order, excluded)
    }

    private fun checkDependency(
        dependency: PluginDependency,
        infos: Map<String, PluginInfo>,
        excluded: Map<String, PluginExclusion>
    ): PluginExclusion? {
        val info = infos[dependency.pluginId]
            ?: return PluginExclusion.MissingDependency(dependency)
        if (dependency.pluginId in excluded)
            return PluginExclusion.ExcludedDependency(dependency)
        if (dependency.supportedVersion != VERSION_ANY &&
            Version.fromString(dependency.supportedVersion) incompatibleWith info.version)
            return PluginExclusion.IncompatibleDependency(dependency, info.version)
        return null
    }
}
