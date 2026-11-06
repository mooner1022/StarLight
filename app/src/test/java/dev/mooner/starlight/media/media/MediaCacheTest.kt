package dev.mooner.starlight.media.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private var limits = MediaCache.Limits(maxItems = 3, maxBytes = 1000, protectMillis = 0)

    private fun cache(dir: File = tmp.root.resolve("cache")) =
        MediaCache(dir, { limits }, { now })

    private fun MediaCache.put(key: String, size: Int, source: String? = null): MediaCache.Hit {
        val temp = newTempFile()
        temp.writeBytes(ByteArray(size))
        now += 10
        return store(key, temp, "jpg", "image/jpeg", source)
    }

    @Test
    fun storesAndFinds() {
        val cache = cache()
        val stored = cache.put("k1", 10)
        assertEquals("k1.jpg", stored.file.name)
        assertTrue(stored.file.isFile)
        val hit = cache.lookup("k1")
        assertNotNull(hit)
        assertEquals("image/jpeg", hit!!.entry.mime)
        assertNull(cache.lookup("k2"))
    }

    @Test
    fun evictsLeastRecentlyUsedOverTheItemLimit() {
        val cache = cache()
        cache.put("a", 10)
        cache.put("b", 10)
        cache.put("c", 10)
        now += 10
        cache.lookup("a")
        cache.put("d", 10)
        cache.flush()
        assertNull("b was used least recently", cache.lookup("b"))
        assertNotNull(cache.lookup("a"))
        assertNotNull(cache.lookup("d"))
        assertEquals(3, cache.usage().items)
    }

    @Test
    fun evictsOverTheSizeLimit() {
        val cache = cache()
        cache.put("a", 600)
        cache.put("b", 600)
        cache.flush()
        assertNull(cache.lookup("a"))
        assertNotNull(cache.lookup("b"))
    }

    @Test
    fun keepsFilesInUse() {
        limits = limits.copy(protectMillis = 60_000)
        val cache = cache()
        cache.put("a", 600)
        cache.put("b", 600)
        cache.flush()
        assertNotNull("a may still be read by KakaoTalk", cache.lookup("a"))
        now += 120_000
        cache.flush()
        assertEquals(1, cache.usage().items)
    }

    @Test
    fun clearsByKeyFileNameOrUrl() {
        val cache = cache()
        val url = "https://example.com/a.jpg"
        cache.put(MediaCache.keyForUrl(url), 10, url)
        cache.put("k2", 10)
        cache.put("k3", 10)
        assertEquals(1, cache.clear(url))
        assertEquals(1, cache.clear("k2.jpg"))
        assertEquals(1, cache.clear("k3"))
        assertEquals(0, cache.clear("missing"))
        assertEquals(0, cache.usage().items)
    }

    @Test
    fun clearsEverything() {
        val cache = cache()
        cache.put("a", 10)
        cache.put("b", 10)
        assertEquals(2, cache.clear())
        assertEquals(0, cache.usage().items)
        assertFalse(tmp.root.resolve("cache/a.jpg").exists())
    }

    @Test
    fun persistsAndSweepsUnknownFiles() {
        val dir = tmp.root.resolve("cache")
        cache(dir).apply {
            put("a", 10)
            flush()
        }
        dir.resolve("orphan.bin").writeBytes(ByteArray(5))
        dir.resolve("b.jpg").writeBytes(ByteArray(5))
        val reloaded = cache(dir)
        assertNotNull(reloaded.lookup("a"))
        assertEquals(1, reloaded.usage().items)
        assertFalse(dir.resolve("orphan.bin").exists())
    }

    @Test
    fun entriesWithoutFilesAreDropped() {
        val cache = cache()
        val hit = cache.put("a", 10)
        hit.file.delete()
        assertNull(cache.lookup("a"))
    }
}
