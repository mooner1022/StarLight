package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.legacy.DatabaseFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The files behind legacy `DataBase`. */
class DatabaseFolderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun folder() = DatabaseFolder(tmp.root)

    @Test
    fun `a name without a dot is a txt file`() {
        val db = folder()
        assertEquals("hi", db.write("data", "hi"))
        assertTrue(tmp.root.resolve("data.txt").isFile)
        assertEquals("hi", db.read("data"))
    }

    @Test
    fun `a name with a dot stays as it is`() {
        val db = folder()
        db.write("a.json", "{}")
        assertTrue(tmp.root.resolve("a.json").isFile)
    }

    @Test
    fun `a file without extension from before is still used`() {
        tmp.root.resolve("old").writeText("x")
        val db = folder()
        assertEquals("x", db.read("old"))
        assertEquals("xy", db.append("old", "y"))
        assertFalse(tmp.root.resolve("old.txt").exists())
    }

    @Test
    fun `reading a missing file or a folder gives null`() {
        val db = folder()
        assertNull(db.read("none"))
        tmp.root.resolve("dir.d").mkdirs()
        assertNull(db.read("dir.d"))
    }

    @Test
    fun `append creates the file and subfolders`() {
        val db = folder()
        assertEquals("a", db.append("sub/x", "a"))
        assertEquals("ab", db.append("sub/x", "b"))
    }

    @Test
    fun `size is minus one when missing`() {
        val db = folder()
        assertEquals(-1L, db.size("none"))
        db.write("f", "abc")
        assertEquals(3L, db.size("f"))
    }

    @Test
    fun `list gives names of files and folders`() {
        val db = folder()
        db.write("b", "")
        db.write("a", "")
        tmp.root.resolve("d").mkdirs()
        assertEquals(listOf("a.txt", "b.txt", "d"), db.list())
    }

    @Test
    fun `remove keeps a backup`() {
        val db = folder()
        db.write("f", "keep")
        assertTrue(db.remove("f", backup = true))
        assertFalse(db.exists("f"))
        assertEquals("keep", tmp.root.resolve("f.txt.bak").readText())
        assertFalse(db.remove("f", backup = true))
    }

    @Test
    fun `remove without backup leaves none`() {
        val db = folder()
        db.write("f", "x")
        assertTrue(db.remove("f", backup = false))
        assertFalse(tmp.root.resolve("f.txt.bak").exists())
    }
}
