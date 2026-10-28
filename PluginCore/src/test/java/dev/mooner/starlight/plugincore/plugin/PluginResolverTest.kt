package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.plugin.PluginDependency.Companion.VERSION_ANY
import dev.mooner.starlight.plugincore.version.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginResolverTest {

    private val coreVersion = Version(1, 0, 0)

    private fun plugin(
        id: String,
        version: String = "1.0.0",
        apiVersion: Version = coreVersion,
        vararg dependency: PluginDependency
    ) = PluginInfo(
        id = id,
        name = id,
        mainClass = "$id.Main",
        version = Version.fromString(version),
        apiVersion = apiVersion,
        dependency = dependency.toList(),
        authors = listOf(),
        description = ""
    )

    private fun dependency(id: String, version: String = VERSION_ANY, optional: Boolean = false) =
        PluginDependency(id, version, isOptional = optional)

    private fun resolve(vararg plugins: PluginInfo, disabled: Set<String> = emptySet()) =
        PluginResolver.resolve(plugins.associateBy(PluginInfo::id), coreVersion, disabled)

    @Test
    fun ordersDependenciesFirst() {
        val result = resolve(
            plugin("a", dependency = arrayOf(dependency("b"))),
            plugin("b", dependency = arrayOf(dependency("c"))),
            plugin("c"),
        )
        assertEquals(listOf("c", "b", "a"), result.order)
        assertTrue(result.excluded.isEmpty())
    }

    @Test
    fun ordersIndependentPluginsById() {
        val result = resolve(plugin("z"), plugin("a"), plugin("m"))
        assertEquals(listOf("a", "m", "z"), result.order)
    }

    @Test
    fun excludesIncompatibleApiVersion() {
        val result = resolve(plugin("a", apiVersion = Version(2, 0, 0)), plugin("b"))
        assertEquals(listOf("b"), result.order)
        assertEquals(PluginExclusion.IncompatibleApi(Version(2, 0, 0)), result.excluded["a"])
    }

    @Test
    fun excludesMissingRequiredDependency() {
        val required = dependency("x")
        val result = resolve(plugin("a", dependency = arrayOf(required)))
        assertTrue(result.order.isEmpty())
        assertEquals(PluginExclusion.MissingDependency(required), result.excluded["a"])
    }

    @Test
    fun ignoresMissingOptionalDependency() {
        val result = resolve(plugin("a", dependency = arrayOf(dependency("x", optional = true))))
        assertEquals(listOf("a"), result.order)
    }

    @Test
    fun ordersPresentOptionalDependencyFirst() {
        val result = resolve(
            plugin("a", dependency = arrayOf(dependency("b", optional = true))),
            plugin("b"),
        )
        assertEquals(listOf("b", "a"), result.order)
    }

    @Test
    fun excludesDependentsTransitively() {
        val result = resolve(
            plugin("a", dependency = arrayOf(dependency("b"))),
            plugin("b", dependency = arrayOf(dependency("c"))),
            plugin("c", apiVersion = Version(0, 9, 0)),
        )
        assertTrue(result.order.isEmpty())
        assertTrue(result.excluded["a"] is PluginExclusion.ExcludedDependency)
        assertTrue(result.excluded["b"] is PluginExclusion.ExcludedDependency)
        assertTrue(result.excluded["c"] is PluginExclusion.IncompatibleApi)
    }

    @Test
    fun comparesRequiredVersionWithDependencyVersion() {
        val result = resolve(
            plugin("a", version = "2.0.0", dependency = arrayOf(dependency("b", "1.0.0"))),
            plugin("b", version = "1.0.3"),
        )
        assertEquals(listOf("b", "a"), result.order)
    }

    @Test
    fun excludesIncompatibleDependencyVersion() {
        val required = dependency("b", "2.0.0")
        val result = resolve(
            plugin("a", dependency = arrayOf(required)),
            plugin("b", version = "1.0.0"),
        )
        assertEquals(listOf("b"), result.order)
        assertEquals(PluginExclusion.IncompatibleDependency(required, Version(1, 0, 0)), result.excluded["a"])
    }

    @Test
    fun excludesCircularDependency() {
        val result = resolve(
            plugin("a", dependency = arrayOf(dependency("b"))),
            plugin("b", dependency = arrayOf(dependency("a"))),
            plugin("c"),
        )
        assertEquals(listOf("c"), result.order)
        assertEquals(PluginExclusion.CircularDependency, result.excluded["a"])
        assertEquals(PluginExclusion.CircularDependency, result.excluded["b"])
    }

    @Test
    fun excludesDisabledPluginsAndTheirDependents() {
        val result = resolve(
            plugin("a", dependency = arrayOf(dependency("b"))),
            plugin("b"),
            plugin("c", dependency = arrayOf(dependency("b", optional = true))),
            disabled = setOf("b"),
        )
        assertEquals(listOf("c"), result.order)
        assertEquals(PluginExclusion.Disabled, result.excluded["b"])
        assertTrue(result.excluded["a"] is PluginExclusion.ExcludedDependency)
    }
}
