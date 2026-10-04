package dev.mooner.starlight.plugincore.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileUtilsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun copyAsReadOnlyCreatesReadOnlyCopy() {
        val source = folder.newFile("plugin.slp").apply { writeText("dex") }
        val target = source.copyAsReadOnly(folder.root.resolve("cache/plugin"))

        assertEquals("dex", target.readText())
        assertFalse(target.canWrite())
        assertEquals(target, source.copyAsReadOnly(folder.root.resolve("cache/plugin")))
    }

    @Test
    fun copyAsReadOnlyReplacesStaleCopy() {
        val directory = folder.root.resolve("cache/plugin")
        val source = folder.newFile("plugin.slp").apply { writeText("dex") }
        val old = source.copyAsReadOnly(directory)

        source.writeText("updated dex")
        val updated = source.copyAsReadOnly(directory)

        assertNotEquals(old, updated)
        assertFalse(old.exists())
        assertEquals("updated dex", updated.readText())
    }
}
