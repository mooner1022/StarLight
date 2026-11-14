package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.legacy.FileStreamApi
import dev.mooner.starlight.languages.ScriptJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** `FileStream` as MessengerBot R behaves, with plain Java values standing in for the engine's. */
class FileStreamApiTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fs = FileStreamApi.FileStream(ScriptJson.Plain)

    private fun path(name: String): String = File(tmp.root, name).path

    @Test
    fun `write makes parents and returns the text, stringifying non-strings`() {
        val p = path("a/b/c.txt")
        assertEquals("123", fs.write(p, 123.0))
        assertEquals("123", fs.read(p))
        assertEquals("null", fs.write(p, null))
        assertEquals("[1, x]", fs.write(p, listOf(1.0, "x")))
    }

    @Test
    fun `read gives null for a missing file and throws for a folder`() {
        assertNull(fs.read(path("none.txt")))
        try {
            fs.read(tmp.root.path)
            fail()
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.startsWith("Failed to read file '"))
        }
    }

    @Test
    fun `append creates the file and returns the old text and the new`() {
        val p = path("x/log.txt")
        assertEquals("a", fs.append(p, "a"))
        assertEquals("ab", fs.append(p, "b"))
        assertEquals("ab", fs.read(p))
    }

    @Test
    fun `save appends only when asked`() {
        val p = path("s.txt")
        fs.save(p, "1")
        fs.save(p, "2", true)
        assertEquals("12", fs.read(p))
        fs.save(p, "3", "false")
        assertEquals("3", fs.read(p))
    }

    @Test
    fun `json round trips and an empty file throws`() {
        val p = path("j.json")
        assertTrue(fs.writeJson(p, mapOf("a" to 1L)))
        assertEquals(mapOf("a" to 1L), fs.readJson(p))
        assertNull(fs.readJson(path("missing.json")))
        fs.write(p, "")
        try {
            fs.readJson(p)
            fail()
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.startsWith("Failed to read JSON from file '"))
        }
    }

    @Test
    fun `extension follows the last dot rule`() {
        assertEquals("gz", fs.getExtension("a.tar.gz"))
        assertNull(fs.getExtension(".bashrc"))
        assertNull(fs.getExtension("a."))
        assertNull(fs.getExtension("noext"))
        assertNull(fs.getExtension(null))
    }

    @Test
    fun `file questions never throw`() {
        assertFalse(fs.exists(null))
        assertFalse(fs.isFile(null))
        assertEquals(-1L, fs.getFileSize(path("missing")))
        assertNull(fs.getFileName(null))
        assertNull(fs.getParentPath("a.txt"))
    }

    @Test
    fun `copy and move`() {
        val a = path("a.txt")
        fs.write(a, "hi")
        assertTrue(fs.copyFile(a, path("d/b.txt")))
        assertEquals("hi", fs.read(path("d/b.txt")))
        val dest = path("e/c.txt")
        assertEquals(dest, fs.moveFile(a, dest))
        assertFalse(fs.exists(a))
        try {
            fs.copyFile(a, dest)
            fail()
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("Source file does not exist"))
        }
    }

    @Test
    fun `create, createDir, listFiles and remove`() {
        assertTrue(fs.create(path("n/f.txt")))
        assertFalse(fs.create(path("n/f.txt")))
        assertTrue(fs.createDir(path("n/sub")))
        assertEquals(listOf("f.txt", "sub"), (fs.listFiles(path("n")) as List<*>).map { it.toString() }.sorted())
        assertNull(fs.listFiles(path("n/f.txt")))
        assertFalse(fs.remove(path("n")))
        assertTrue(fs.remove(path("n/f.txt")))
    }
}
