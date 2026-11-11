package dev.mooner.starlight.plugincore.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var clock = 1_000_000L

    /** Writes a plugin file with [entries], newer than any written before. */
    private fun plugin(vararg entries: Pair<String, String>): File {
        val file = File(tmp.root, "plugin.slp")
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        clock += 10_000
        file.setLastModified(clock)
        return file
    }

    @Test
    fun anUpdatedPluginGetsItsNewLibraries() {
        val libs = File(tmp.root, "libs")
        PluginFiles.unpackNativeLibs(plugin("lib/x86_64/libbot.so" to "v1", "lib/x86_64/libold.so" to "old"), libs, "x86_64")
        assertEquals("v1", File(libs, "libbot.so").readText())

        // The same names, new contents; one library is gone from the new version.
        val changed = PluginFiles.unpackNativeLibs(plugin("lib/x86_64/libbot.so" to "v2"), libs, "x86_64")

        assertTrue(changed)
        assertEquals("v2", File(libs, "libbot.so").readText())
        assertFalse(File(libs, "libold.so").exists())
    }

    @Test
    fun theSamePluginFileIsNotUnpackedTwice() {
        val libs = File(tmp.root, "libs")
        val file = plugin("lib/x86_64/libbot.so" to "v1")
        assertTrue(PluginFiles.unpackNativeLibs(file, libs, "x86_64"))
        File(libs, "libbot.so").writeText("touched")

        assertFalse(PluginFiles.unpackNativeLibs(file, libs, "x86_64"))
        assertEquals("touched", File(libs, "libbot.so").readText())
    }

    @Test
    fun whatThePluginPutThereItselfStays() {
        val libs = File(tmp.root, "libs")
        PluginFiles.unpackNativeLibs(plugin("lib/x86_64/libbot.so" to "v1"), libs, "x86_64")
        File(libs, "downloaded.so").writeText("the plugin's own")

        PluginFiles.unpackNativeLibs(plugin("lib/x86_64/libbot.so" to "v2"), libs, "x86_64")

        assertEquals("the plugin's own", File(libs, "downloaded.so").readText())
    }

    @Test
    fun aFolderUnpackedBeforeThereWasAStampIsBroughtUpToDate() {
        // As an installed app has it: the library of an older version, and no record of where it came from.
        val libs = File(tmp.root, "libs").apply { mkdirs() }
        File(libs, "libbot.so").writeText("from the version before")

        PluginFiles.unpackNativeLibs(plugin("lib/x86_64/libbot.so" to "current"), libs, "x86_64")

        assertEquals("current", File(libs, "libbot.so").readText())
    }

    @Test
    fun onlyTheFolderOfTheDevicesArchitectureIsTaken() {
        val file = plugin(
            "lib/x86_64/libbot.so" to "64",
            "lib/x86/libbot.so" to "32",
            "lib/armeabi-v7a/libbot.so" to "v7",
            "lib/armeabi/libbot.so" to "v5",
        )

        val x86 = File(tmp.root, "x86").also { PluginFiles.unpackNativeLibs(file, it, "x86") }
        val arm = File(tmp.root, "arm").also { PluginFiles.unpackNativeLibs(file, it, "armeabi") }

        // "x86" used to match "x86_64" too, by its first letters.
        assertEquals("32", File(x86, "libbot.so").readText())
        assertEquals("v7", File(arm, "libbot.so").readText())
    }

    @Test
    fun assetsFollowThePluginFileAndItsRevision() {
        val assets = File(tmp.root, "assets")
        val file = plugin("assets/icon.png" to "one", "assets/web/index.html" to "page", "classes.dex" to "code")
        assertTrue(PluginFiles.unpackAssets(file, assets, revision = 1))
        assertEquals("page", File(assets, "web/index.html").readText())
        assertFalse(File(assets, "classes.dex").exists())

        assertFalse(PluginFiles.unpackAssets(file, assets, revision = 1))
        assertTrue(PluginFiles.unpackAssets(file, assets, revision = 2))
        // A new file with the revision left as it was: the author forgot to raise it.
        assertTrue(PluginFiles.unpackAssets(plugin("assets/icon.png" to "two"), assets, revision = 2))
        assertEquals("two", File(assets, "icon.png").readText())
        assertFalse(File(assets, "web/index.html").exists())
    }

    @Test
    fun anEntryThatLeadsOutOfTheFolderIsRefused() {
        val assets = File(tmp.root, "inner/assets")
        try {
            PluginFiles.unpackAssets(plugin("assets/../../escaped.txt" to "x"), assets, revision = 1)
            fail("should have refused")
        } catch (_: IllegalStateException) {}
        assertFalse(File(tmp.root, "escaped.txt").exists())
        // Not marked as done: the next start tries again.
        assertFalse(File(assets, PluginFiles.STAMP).exists())
    }
}
