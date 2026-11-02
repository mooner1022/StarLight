package dev.mooner.starlight.media.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

/** Sources as Rhino hands them over: strings, Java lists and byte arrays. */
class MediaSourcesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(100) { it.toByte() }

    @Test
    fun urls() {
        assertEquals(MediaSource.Remote("https://example.com/a.jpg"), MediaSources.parse("  https://example.com/a.jpg ", null))
        assertEquals(MediaSource.Remote("HTTP://example.com/a"), MediaSources.parse("HTTP://example.com/a", null))
    }

    @Test
    fun relativePathsResolveAgainstTheProject() {
        val project = tmp.newFolder("project")
        project.resolve("images").mkdirs()
        project.resolve("images/cat.png").writeBytes(byteArrayOf(1))
        val source = MediaSources.parse("images/cat.png", project) as MediaSource.Local
        assertEquals(project.resolve("images/cat.png"), source.file)
        // Missing files are still local: reported when prepared.
        assertEquals(project.resolve("nope.png"), (MediaSources.parse("nope.png", project) as MediaSource.Local).file)
    }

    @Test
    fun withoutAProjectRelativePathsAreRefused() {
        // room.sendMedia doesn't know the project.
        try {
            MediaSources.parse("images/cat.png", null)
            throw AssertionError("A relative path was taken without a project")
        } catch (e: InvalidSourceException) {
            assertTrue(e.message!!.contains("MediaSharer.send"))
        }
        // What doesn't need the project still works.
        assertEquals(File("/sdcard/a.jpg"), (MediaSources.parse("/sdcard/a.jpg", null) as MediaSource.Local).file)
        assertArrayEquals(jpeg, (MediaSources.parse("base64:" + Base64.getEncoder().encodeToString(jpeg), null) as MediaSource.Bytes).bytes)
    }

    @Test
    fun sharedStoragePaths() {
        assertEquals(File("/sdcard/Download/a.jpg"), (MediaSources.parse("sdcard/Download/a.jpg", null) as MediaSource.Local).file)
        assertEquals(File("/storage/emulated/0/a.jpg"), (MediaSources.parse("/storage/emulated/0/a.jpg", null) as MediaSource.Local).file)
        assertEquals(File("/sdcard/a b.jpg"), (MediaSources.parse("file:///sdcard/a%20b.jpg", null) as MediaSource.Local).file)
    }

    @Test
    fun rawBase64OfAJpegIsNotAPath() {
        // JPEG data in Base64 starts with "/9j/", which looks like an absolute path.
        val text = Base64.getEncoder().encodeToString(jpeg)
        assertTrue(text.startsWith("/9j/"))
        val source = MediaSources.parse(text, tmp.root) as MediaSource.Bytes
        assertArrayEquals(jpeg, source.bytes)
    }

    @Test
    fun base64Forms() {
        val standard = Base64.getEncoder().encodeToString(jpeg)
        val urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(jpeg)
        assertArrayEquals(jpeg, (MediaSources.parse("base64:$standard", null) as MediaSource.Bytes).bytes)
        assertArrayEquals(jpeg, (MediaSources.parse("base64,$urlSafe", null) as MediaSource.Bytes).bytes)
        assertArrayEquals(jpeg, (MediaSources.parse(standard.chunked(20).joinToString("\n"), null) as MediaSource.Bytes).bytes)
    }

    @Test
    fun dataUrls() {
        val base64 = MediaSources.parse("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg), null) as MediaSource.Bytes
        assertArrayEquals(jpeg, base64.bytes)
        assertEquals("image/jpeg", base64.mimeHint)
        val text = MediaSources.parse("data:text/plain,hello%20world", null) as MediaSource.Bytes
        assertEquals("hello world", String(text.bytes))
        assertEquals("text/plain", text.mimeHint)
    }

    @Test
    fun byteArraysAndLists() {
        assertArrayEquals(jpeg, (MediaSources.parseAll(jpeg, null).single() as MediaSource.Bytes).bytes)
        val sources = MediaSources.parseAll(listOf("https://example.com/a.jpg", jpeg, File("/sdcard/b.png")), null)
        assertEquals(3, sources.size)
        assertTrue(sources[0] is MediaSource.Remote)
        assertTrue(sources[1] is MediaSource.Bytes)
        assertTrue(sources[2] is MediaSource.Local)
    }

    @Test(expected = InvalidSourceException::class)
    fun emptyListsAreRejected() {
        MediaSources.parseAll(emptyList<Any>(), null)
    }

    @Test
    fun badItemsNameTheirPosition() {
        try {
            MediaSources.parseAll(listOf("https://example.com/a.jpg", null), null)
        } catch (e: InvalidSourceException) {
            assertTrue(e.message!!.startsWith("Item 2:"))
            return
        }
        throw AssertionError("Expected an exception")
    }
}
