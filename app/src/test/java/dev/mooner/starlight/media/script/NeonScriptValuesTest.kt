package dev.mooner.starlight.media.script

import dev.mooner.neonjs.HostAccess
import dev.mooner.neonjs.NeonContext
import dev.mooner.neonjs.NeonEngine
import dev.mooner.neonjs.SandboxPolicy
import dev.mooner.starlight.media.media.MediaSource
import dev.mooner.starlight.media.media.MediaSources
import dev.mooner.starlight.media.send.LeaveTo
import dev.mooner.starlight.plugincore.chat.MediaSendResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture

/**
 * Values passed from NeonJS, with the engine set up the way StarLight sets it up. The host object's methods take
 * `Any?` like `MediaSender`'s, and do their reading on the script's thread as it does.
 */
class NeonScriptValuesTest {

    class Host {
        var sources: List<MediaSource> = emptyList()
        var options: SendOptions? = null
        var error: String? = null

        fun send(source: Any?): Boolean = send(source, null)

        fun send(source: Any?, options: Any?): Boolean =
            try {
                sources = MediaSources.parseAll(source, null)
                this.options = SendOptions.read(options)
                true
            } catch (e: Exception) {
                error = e.message
                false
            }

        /** As MediaSharer.send resolves: a future of a result object. */
        fun result(ok: Boolean): CompletableFuture<MediaSendResult> =
            CompletableFuture.supplyAsync {
                Thread.sleep(50)
                MediaSendResult(ok, "Room (1)", if (ok) 2 else 0, 1, if (ok) null else "nope", listOf("a.jpg: missing"), null)
            }

        fun bytes(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2)
    }

    private lateinit var neon: NeonContext
    private val host = Host()

    @Before
    fun setUp() {
        neon = NeonEngine.builder()
            .sandbox(SandboxPolicy.builder().exposeJavaGlobal(true).build())
            .hostAccess(
                HostAccess.builder(HostAccess.Level.ALL)
                    .defaultDenyList(false)
                    .allowLookup { true }
                    .build()
            )
            .webGlobals(true)
            .build()
            .newContext()
        neon["host"] = host
    }

    @After
    fun tearDown() {
        neon.close()
    }

    private fun eval(code: String) {
        neon.eval(code, "test.js")
    }

    @Test
    fun stringsAndArrays() {
        eval("""host.send(["https://example.com/a.jpg", host.bytes(), "https://example.com/b.png"])""")
        assertNull(host.error)
        assertEquals(3, host.sources.size)
        assertEquals(MediaSource.Remote("https://example.com/a.jpg"), host.sources[0])
        assertArrayEquals(host.bytes(), (host.sources[1] as MediaSource.Bytes).bytes)
    }

    @Test
    fun typedArrays() {
        eval("""host.send(new Uint8Array([0x89, 0x50, 0x4E, 0x47]))""")
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), (host.sources.single() as MediaSource.Bytes).bytes)
        eval("""host.send(new Uint8Array([1, 2, 3]).buffer)""")
        assertArrayEquals(byteArrayOf(1, 2, 3), (host.sources.single() as MediaSource.Bytes).bytes)
        eval("""host.send(new DataView(new Uint8Array([9, 8, 7, 6]).buffer, 1, 2))""")
        assertArrayEquals(byteArrayOf(8, 7), (host.sources.single() as MediaSource.Bytes).bytes)
        eval("""host.send([new Uint8Array([5]), "https://example.com/c.gif"])""")
        assertArrayEquals(byteArrayOf(5), (host.sources[0] as MediaSource.Bytes).bytes)
    }

    @Test
    fun optionObjects() {
        eval("""host.send("https://example.com/a.jpg", { fileName: "고양이", timeout: 5000, cache: true, headers: { Referer: "https://example.com", "X-Num": 1 } })""")
        assertNull(host.error)
        val options = host.options!!
        assertEquals("고양이", options.fileName)
        assertEquals(5000, options.timeoutMillis)
        assertEquals(true, options.cache)
        assertEquals(mapOf("Referer" to "https://example.com", "X-Num" to "1"), options.headers)
    }

    @Test
    fun leaveOption() {
        eval("""host.send("https://example.com/a.jpg", { leave: "starlight" })""")
        assertEquals(LeaveTo.StarLight, host.options!!.leaveTo)
        eval("""host.send("https://example.com/a.jpg", { leave: false })""")
        assertEquals(LeaveTo.Stay, host.options!!.leaveTo)
    }

    @Test
    fun resultsArePromisesOfObjects() {
        eval(
            """
            globalThis.out = "pending";
            (async () => {
                const r = await host.result(true);
                const f = await host.result(false);
                out = [r.ok, r.sent, r.failed, r.room, r.error, r.errors.size(), f.ok, f.error].join("|");
            })();
            """.trimIndent()
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (neon["out"].asString() == "pending" && System.currentTimeMillis() < deadline)
            neon.runEventLoop(100)
        assertEquals("true|2|1|Room (1)||1|false|nope", neon["out"].asString())
    }

    @Test
    fun badValuesAreReported() {
        eval("""host.send("https://example.com/a.jpg", { fileName: "a/b" })""")
        assertTrue(host.error!!.contains("Invalid file name"))
        eval("""host.send([])""")
        assertTrue(host.error!!.contains("empty"))
        eval("""host.send(undefined)""")
        assertTrue(host.error!!.contains("No file"))
        eval("""host.send("https://example.com/a.jpg", "fast")""")
        assertTrue(host.error!!.contains("Options must be an object"))
        eval("""host.send("https://example.com/a.jpg", { filename: "a" })""")
        assertTrue(host.error!!.contains("Unknown option 'filename'"))
    }
}
