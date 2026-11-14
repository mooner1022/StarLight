package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.api2.DatabaseFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class DatabaseApiTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val base get() = folder.root.toPath().resolve("Database").toAbsolutePath().normalize()

    @Test
    fun blankNameResolvesToNothingAndCreatesNothing() {
        assertNull(DatabaseFiles.resolve(base, "  ", forWrite = false))
        assertTrue(!Files.exists(base))
    }

    @Test
    fun subfoldersAreAllowedAndCreatedForWrites() {
        val path = DatabaseFiles.resolve(base, "a/b.json", forWrite = true)!!
        assertEquals(base.resolve("a/b.json"), path)
        assertTrue(Files.isDirectory(base.resolve("a")))
    }

    @Test
    fun traversalAndAbsolutePathsAreRejected() {
        assertThrows(SecurityException::class.java) { DatabaseFiles.resolve(base, "../x", forWrite = false) }
        assertThrows(SecurityException::class.java) { DatabaseFiles.resolve(base, "/etc/passwd", forWrite = false) }
        assertThrows(SecurityException::class.java) { DatabaseFiles.resolve(base, "a/../../x", forWrite = true) }
    }

    @Test
    fun atomicWriteReplacesTheFileAndLeavesNoTemporaryFile() {
        val path = DatabaseFiles.resolve(base, "t.txt", forWrite = true)!!
        DatabaseFiles.writeAtomically(path, "first")
        DatabaseFiles.writeAtomically(path, "second é")
        assertEquals("second é", String(Files.readAllBytes(path), Charsets.UTF_8))
        Files.list(base).use { files -> assertEquals(1, files.count()) }
    }
}
