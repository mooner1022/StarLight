package dev.mooner.starlight.media.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String) = text.toByteArray(Charsets.ISO_8859_1)

    private fun padded(prefix: ByteArray, size: Int = 64) = prefix.copyOf(maxOf(size, prefix.size))

    private fun ftyp(major: String, vararg compatible: String): ByteArray {
        val size = 16 + compatible.size * 4
        return bytes(0, 0, 0, size) + ascii("ftyp") + ascii(major) + bytes(0, 0, 0, 0) +
            compatible.joinToString("").let(::ascii)
    }

    @Test
    fun recognizesCommonSignatures() {
        assertEquals("jpg", FileTypes.sniff(padded(bytes(0xFF, 0xD8, 0xFF, 0xE0)))?.ext)
        assertEquals("png", FileTypes.sniff(padded(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))?.ext)
        assertEquals("gif", FileTypes.sniff(padded(ascii("GIF89a")))?.ext)
        assertEquals("webp", FileTypes.sniff(padded(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))?.ext)
        assertEquals("wav", FileTypes.sniff(padded(ascii("RIFF\u0000\u0000\u0000\u0000WAVEfmt ")))?.ext)
        assertEquals("avi", FileTypes.sniff(padded(ascii("RIFF\u0000\u0000\u0000\u0000AVI LIST")))?.ext)
        assertEquals("pdf", FileTypes.sniff(padded(ascii("%PDF-1.7")))?.ext)
        assertEquals("mp3", FileTypes.sniff(padded(ascii("ID3\u0004")))?.ext)
        assertEquals("ogg", FileTypes.sniff(padded(ascii("OggS")))?.ext)
        assertEquals("zip", FileTypes.sniff(padded(bytes(0x50, 0x4B, 0x03, 0x04)))?.ext)
        assertEquals("lzh", FileTypes.sniff(padded(ascii("\u0000\u0000-lh5-")))?.ext)
    }

    @Test
    fun tellsIsoMediaFilesApart() {
        assertEquals("mp4", FileTypes.sniff(padded(ftyp("isom", "isom", "mp41")))?.ext)
        assertEquals("mov", FileTypes.sniff(padded(ftyp("qt  ")))?.ext)
        assertEquals("m4a", FileTypes.sniff(padded(ftyp("M4A ", "M4A ", "mp42")))?.ext)
        assertEquals("heic", FileTypes.sniff(padded(ftyp("heic", "mif1", "heic")))?.ext)
        assertEquals("avif", FileTypes.sniff(padded(ftyp("mif1", "avif", "miaf")))?.ext)
    }

    @Test
    fun textIsNotMistakenForWeakSignatures() {
        // 'G' (transport stream) and "BM" (bitmap) start plenty of text.
        assertNull(FileTypes.sniff(ascii("Good morning, this is text")))
        assertNull(FileTypes.sniff(ascii("BMW is a car brand and this is text")))
        assertEquals("txt", FileTypes.resolveExtension(FileTypes.sniff(ascii("Good morning")), emptyList(), ascii("Good morning")))
        // A real transport stream repeats the sync byte every 188 bytes.
        val ts = ByteArray(376).also { it[0] = 0x47; it[188] = 0x47 }
        assertEquals("ts", FileTypes.sniff(ts)?.ext)
    }

    @Test
    fun hintsNarrowSharedSignatures() {
        val zip = FileTypes.sniff(padded(bytes(0x50, 0x4B, 0x03, 0x04)))
        assertEquals("docx", FileTypes.resolveExtension(zip, listOf("docx")))
        assertEquals("zip", FileTypes.resolveExtension(zip, listOf(null)))
        // A URL claiming .jpg for PNG data: the data wins.
        val png = FileTypes.sniff(padded(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("png", FileTypes.resolveExtension(png, listOf("jpg")))
        // No signature: the first known hint, then text, then the fallback.
        assertEquals("csv", FileTypes.resolveExtension(null, listOf("unknown", "csv"), ascii("a,b\n1,2")))
        assertEquals("txt", FileTypes.resolveExtension(null, emptyList(), ascii("plain text")))
        assertEquals(FileTypes.FALLBACK_EXT, FileTypes.resolveExtension(null, emptyList(), bytes(0x00, 0x01, 0x02)))
    }

    @Test
    fun readsTextAsText() {
        assertTrue(FileTypes.looksLikeText(ascii("hello\r\nworld\t!")))
        assertTrue(FileTypes.looksLikeText("한글 텍스트".toByteArray(charset("MS949"))))
        assertFalse(FileTypes.looksLikeText(bytes(0x48, 0x00, 0x49)))
    }

    @Test
    fun extensionsFromUrls() {
        assertEquals("png", FileTypes.extensionOfUrl("https://example.com/a/b/image.PNG?x=1"))
        assertEquals("jpg", FileTypes.extensionOfUrl("https://pbs.twimg.com/media/abc?format=jpg&name=large"))
        assertEquals("mp4", FileTypes.extensionOfUrl("https://example.com/download?file=clip.mp4"))
        assertEquals("pdf", FileTypes.extensionOfUrl("https://example.com/%EB%AC%B8%EC%84%9C.pdf"))
        assertNull(FileTypes.extensionOfUrl("https://example.com/api/image/12345"))
        assertNull(FileTypes.extensionOfUrl("https://example.com/v1.2/data"))
        assertEquals("문서.pdf", FileTypes.fileNameOfUrl("https://example.com/%EB%AC%B8%EC%84%9C.pdf"))
        assertNull(FileTypes.fileNameOfUrl("https://example.com/api/image"))
    }

    @Test
    fun extensionsFromMimeTypes() {
        assertEquals("jpg", FileTypes.extensionOfMime("image/jpeg"))
        assertEquals("jpg", FileTypes.extensionOfMime("image/jpg"))
        assertEquals("txt", FileTypes.extensionOfMime("text/plain; charset=utf-8"))
        assertEquals("mp4", FileTypes.extensionOfMime("video/mp4"))
        assertNull(FileTypes.extensionOfMime("application/x-unknown"))
        assertEquals(FileTypes.OCTET_STREAM, FileTypes.mimeOf("exe"))
        assertEquals("image/webp", FileTypes.mimeOf("WEBP"))
    }

    @Test
    fun contentDispositionNames() {
        assertEquals("report.pdf", FileTypes.fileNameOfContentDisposition("attachment; filename=\"report.pdf\""))
        assertEquals("report.pdf", FileTypes.fileNameOfContentDisposition("attachment; filename=report.pdf"))
        assertEquals("보고서.pdf", FileTypes.fileNameOfContentDisposition("attachment; filename=\"x.pdf\"; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C.pdf"))
        assertEquals("a_b.txt", FileTypes.fileNameOfContentDisposition("attachment; filename=\"a/b.txt\""))
        assertNull(FileTypes.fileNameOfContentDisposition("inline"))
    }

    @Test
    fun fileNames() {
        assertTrue(FileTypes.isValidFileName("사진 1.jpg"))
        assertFalse(FileTypes.isValidFileName("a/b"))
        assertFalse(FileTypes.isValidFileName("what?"))
        assertFalse(FileTypes.isValidFileName(".."))
        assertEquals("a_b_c.txt", FileTypes.sanitizeFileName("a:b*c.txt"))
        assertTrue(FileTypes.sanitizeFileName("x".repeat(500) + ".jpg").let { it.endsWith(".jpg") && it.toByteArray().size <= 200 })
        assertEquals("photo.jpg", FileTypes.withExtension("photo.JPG".lowercase(), "jpg"))
        assertEquals("photo.jpg", FileTypes.withExtension("photo", "jpg"))
        assertEquals("photo.png", MediaPreparer.replaceExtension("photo.jpg", "png"))
        assertEquals("v1.2.png", MediaPreparer.replaceExtension("v1.2", "png"))
        assertEquals("doc.PDF", MediaPreparer.replaceExtension("doc.PDF", "pdf"))
    }
}
