package dev.mooner.starlight.media.media

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

/** Downloads from a local HTTP server, local files and data, as prepared before sending. */
class MediaPreparerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: TestHttpServer
    private lateinit var base: String
    private val hits = AtomicInteger()

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(200) { 7 }
    private val pdf = "%PDF-1.7\n".toByteArray() + ByteArray(200) { 1 }

    private lateinit var cache: MediaCache
    private lateinit var preparer: MediaPreparer
    private val options = MediaPreparer.Options(useCache = false, timeoutMillis = 5_000, maxBytes = 1L shl 20)

    @Before
    fun setUp() {
        server = TestHttpServer()
        fun respond(status: Int, body: ByteArray, headers: Map<String, String> = emptyMap()): TestHttpServer.Response {
            hits.incrementAndGet()
            return TestHttpServer.Response(status, body, headers)
        }
        // No extension in the URL: the type comes from the data.
        server.route("/image") { respond(200, png, mapOf("Content-Type" to "application/octet-stream")) }
        server.route("/report") {
            respond(200, pdf, mapOf("Content-Disposition" to "attachment; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C.pdf"))
        }
        server.route("/moved") { respond(302, ByteArray(0), mapOf("Location" to "/image")) }
        server.route("/missing") { respond(404, "nope".toByteArray()) }
        server.route("/ua") { request ->
            val ua = request.headers["user-agent"].orEmpty()
            val token = request.headers["x-token"].orEmpty()
            respond(200, "$ua|$token".toByteArray(), mapOf("Content-Type" to "text/plain"))
        }
        server.route("/big") { respond(200, ByteArray(4096)) }
        server.start()
        base = server.baseUrl

        cache = MediaCache(tmp.root.resolve("cache"), { MediaCache.Limits(100, 1L shl 30, 0) })
        preparer = MediaPreparer(cache, Downloader(), clock = { 1234L })
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun work() = MediaPreparer.WorkDir(tmp.root.resolve("work-${System.nanoTime()}"))

    @Test
    fun downloadsAndTypesByContent() {
        val prepared = preparer.prepare(MediaSource.Remote("$base/image"), null, work(), options, 1)
        assertEquals("image/png", prepared.mime)
        assertEquals("1234_1.png", prepared.file.name)
        assertArrayEquals(png, prepared.file.readBytes())
    }

    @Test
    fun followsRedirects() {
        val prepared = preparer.prepare(MediaSource.Remote("$base/moved"), null, work(), options, 1)
        assertArrayEquals(png, prepared.file.readBytes())
    }

    @Test
    fun namesFromContentDispositionOrTheScript() {
        assertEquals("보고서.pdf", preparer.prepare(MediaSource.Remote("$base/report"), null, work(), options, 1).file.name)
        val named = preparer.prepare(MediaSource.Remote("$base/report"), "월간 보고서", work(), options, 1)
        assertEquals("월간 보고서.pdf", named.file.name)
        assertEquals("application/pdf", named.mime)
    }

    @Test
    fun sendsHeaders() {
        val prepared = preparer.prepare(
            MediaSource.Remote("$base/ua"), null, work(), options.copy(headers = mapOf("X-Token" to "abc")), 1,
        )
        assertEquals("${Downloader.DEFAULT_USER_AGENT}|abc", prepared.file.readText())
        assertEquals("text/plain", prepared.mime)
    }

    @Test(expected = Downloader.DownloadException::class)
    fun httpErrorsFail() {
        preparer.prepare(MediaSource.Remote("$base/missing"), null, work(), options, 1)
    }

    @Test(expected = Downloader.DownloadException::class)
    fun sizeLimit() {
        preparer.prepare(MediaSource.Remote("$base/big"), null, work(), options.copy(maxBytes = 1024), 1)
    }

    @Test
    fun cachedDownloadsAreReused() {
        val cached = options.copy(useCache = true)
        val first = preparer.prepare(MediaSource.Remote("$base/image"), null, work(), cached, 1)
        val before = hits.get()
        val second = preparer.prepare(MediaSource.Remote("$base/image"), null, work(), cached, 1)
        assertEquals("no second download", before, hits.get())
        assertEquals(first.file, second.file)
        assertEquals("png", second.file.extension)
        // A name makes a copy, leaving the cached file as it is.
        val named = preparer.prepare(MediaSource.Remote("$base/image"), "cat", work(), cached, 1)
        assertEquals("cat.png", named.file.name)
        assertNotEquals(first.file, named.file)
        assertTrue(first.file.isFile)
    }

    @Test
    fun localFilesAreSharedInPlaceUnlessRenamed() {
        val file = tmp.newFile("photo.jpg").apply { writeBytes(png) }
        val inPlace = preparer.prepare(MediaSource.Local(file), null, work(), options, 1)
        assertEquals(file, inPlace.file)
        assertEquals("image/png", inPlace.mime)
        val renamed = preparer.prepare(MediaSource.Local(file), "cat", work(), options, 1)
        assertEquals("cat.png", renamed.file.name)
        assertArrayEquals(png, renamed.file.readBytes())
    }

    @Test(expected = MediaPreparer.PrepareException::class)
    fun missingFilesFail() {
        preparer.prepare(MediaSource.Local(tmp.root.resolve("nope.jpg")), null, work(), options, 1)
    }

    @Test
    fun bytesAndCachedBytes() {
        val work = work()
        val first = preparer.prepare(MediaSource.Bytes(pdf), null, work, options, 1)
        val second = preparer.prepare(MediaSource.Bytes(pdf), null, work, options, 2)
        assertEquals("application/pdf", first.mime)
        assertNotEquals(first.file, second.file)

        val cached = options.copy(useCache = true)
        val a = preparer.prepare(MediaSource.Bytes(pdf), null, work, cached, 1)
        val b = preparer.prepare(MediaSource.Bytes(pdf.copyOf()), null, work, cached, 1)
        assertEquals("same contents, same cached file", a.file, b.file)
    }

    @Test
    fun namesInOneSendDontCollide() {
        val work = work()
        val a = preparer.prepare(MediaSource.Bytes(png), "same", work, options, 1)
        val b = preparer.prepare(MediaSource.Bytes(png), "same", work, options, 2)
        assertEquals("same.png", a.file.name)
        assertEquals("same (1).png", b.file.name)
    }
}
