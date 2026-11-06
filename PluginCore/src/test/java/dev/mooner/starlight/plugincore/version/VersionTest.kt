package dev.mooner.starlight.plugincore.version

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun newerThanComparesInOrder() {
        assertTrue(Version(1, 2, 0) newerThan Version(1, 1, 9))
        assertTrue(Version(2, 0, 0) newerThan Version(1, 9, 9))
        assertTrue(Version(1, 0, 1) newerThan "1.0.0")
        assertFalse(Version(1, 1, 9) newerThan Version(1, 2, 0))
        assertFalse(Version(1, 0, 0) newerThan Version(1, 0, 0))
    }

    @Test
    fun compatibilityChecksMajorAndMinor() {
        assertTrue(Version(1, 2, 0) compatibleWith Version(1, 2, 5))
        assertFalse(Version(1, 2, 0) compatibleWith Version(1, 3, 0))
    }
}
