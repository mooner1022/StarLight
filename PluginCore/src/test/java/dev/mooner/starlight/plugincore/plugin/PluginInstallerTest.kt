package dev.mooner.starlight.plugincore.plugin

import dev.mooner.starlight.plugincore.plugin.PluginInstaller.Companion.sha256
import dev.mooner.starlight.plugincore.version.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pluginsDir by lazy { temp.newFolder("plugins") }
    private val internalDir by lazy { temp.newFolder("files") }
    private val installer by lazy { PluginInstaller(pluginsDir, internalDir) }

    private fun pluginFile(name: String, manifest: String?): File {
        val file = temp.root.resolve(name)
        ZipOutputStream(file.outputStream()).use { zip ->
            if (manifest != null) {
                zip.putNextEntry(ZipEntry("assets/starlight.json"))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(name.toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private fun manifest(id: String, version: String = "1.0.0") = """
        {
          "id": "$id",
          "name": "Name-$id",
          "main_class": "$id.Main",
          "version": "$version",
          "api_version": "0.3.2",
          "authors": ["mooner"],
          "description": "desc",
          "listeners": []
        }
    """.trimIndent()

    @Test
    fun readsManifestIgnoringUnknownKeys() {
        val info = installer.inspect(pluginFile("a.slp", manifest("sample", "1.2.3")))
        assertEquals("sample", info.id)
        assertEquals(Version(1, 2, 3), info.version)
    }

    @Test
    fun rejectsFilesWithoutManifest() {
        val file = pluginFile("empty.slp", null)
        assertThrows(InvalidPluginException::class.java) { installer.inspect(file) }
    }

    @Test
    fun rejectsNonZipFiles() {
        val file = temp.newFile("broken.slp")
        assertThrows(InvalidPluginException::class.java) { installer.inspect(file) }
    }

    @Test
    fun rejectsPreservedAndIllegalIds() {
        assertThrows(InvalidPluginException::class.java) { installer.inspect(pluginFile("a.slp", manifest("System"))) }
        assertThrows(InvalidPluginException::class.java) { installer.inspect(pluginFile("b.slp", manifest("../evil"))) }
    }

    @Test
    fun installsUnderIdAndReplacesOtherFilesWithTheSameId() {
        val old = pluginFile("custom-name.slp", manifest("sample", "1.0.0")).copyTo(pluginsDir.resolve("custom-name.slp"))
        val other = pluginFile("other.slp", manifest("other")).copyTo(pluginsDir.resolve("other.slp"))

        val info = installer.install(pluginFile("download.slp", manifest("sample", "2.0.0")))

        val target = pluginsDir.resolve("sample.slp")
        assertEquals(Version(2, 0, 0), info.version)
        assertTrue(target.isFile)
        assertEquals(Version(2, 0, 0), installer.inspect(target).version)
        assertFalse(old.exists())
        assertTrue(other.exists())
        assertTrue(pluginsDir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun verifiesChecksumBeforeInstalling() {
        val source = pluginFile("a.slp", manifest("sample"))
        assertThrows(InvalidPluginException::class.java) { installer.install(source, sha256 = "00") }
        assertFalse(pluginsDir.resolve("sample.slp").exists())

        installer.install(source, sha256 = source.sha256().uppercase())
        assertTrue(pluginsDir.resolve("sample.slp").exists())
    }

    @Test
    fun uninstallKeepsDataOnlyWhenAsked() {
        val info = installer.install(pluginFile("a.slp", manifest("sample")))
        val internal = internalDir.resolve("plugin_data/sample").apply { resolve("assets").mkdirs(); resolve("db").mkdirs() }
        val external = pluginsDir.resolve(PluginInstaller.dataDirNameOf(info)).apply { mkdirs() }

        installer.uninstall(installer.fileOf("sample"), info, keepData = true)
        assertFalse(installer.fileOf("sample").exists())
        assertFalse(internal.resolve("assets").exists())
        assertTrue(internal.resolve("db").exists())
        assertTrue(external.exists())

        installer.uninstall(installer.fileOf("sample"), info, keepData = false)
        assertFalse(internal.exists())
        assertFalse(external.exists())
    }
}
