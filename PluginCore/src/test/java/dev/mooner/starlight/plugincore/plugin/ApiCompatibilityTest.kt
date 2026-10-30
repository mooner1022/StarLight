package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.version.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCompatibilityTest {

    private val current = Version(1, 0, 0)
    private val minimum = Version(0, 3, 0)

    private fun of(apiVersion: Version) =
        ApiCompatibility.of(apiVersion, current, minimum)

    @Test
    fun acceptsEveryVersionInsideTheRange() {
        assertTrue(of(minimum).isCompatible)
        assertTrue(of(Version(0, 3, 2)).isCompatible)
        assertTrue(of(Version(0, 9, 0)).isCompatible)
        assertTrue(of(current).isCompatible)
    }

    @Test
    fun rejectsPluginsBuiltForANewerCore() {
        assertEquals(ApiCompatibility.RequiresNewerApp(Version(1, 0, 1), current), of(Version(1, 0, 1)))
        assertFalse(of(Version(2, 0, 0)).isCompatible)
    }

    @Test
    fun rejectsPluginsOlderThanTheMinimum() {
        assertEquals(ApiCompatibility.Outdated(Version(0, 2, 9), minimum), of(Version(0, 2, 9)))
        assertFalse(of(Version(0, 1, 0)).isCompatible)
    }
}
