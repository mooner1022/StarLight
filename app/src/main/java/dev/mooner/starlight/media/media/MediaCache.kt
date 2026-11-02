/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.media

import dev.mooner.starlight.media.utils.sha256Hex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Downloaded and decoded files kept for reuse, named by a hash of their URL or contents.
 *
 * The index is loaded once and kept in memory; [flush] writes it back and evicts the least recently used files once
 * there are more than [Limits.maxItems] files or [Limits.maxBytes] bytes. Files used within [Limits.protectMillis]
 * are never evicted, since KakaoTalk may still be reading them.
 */
class MediaCache(
    private val dir: File,
    private val limits: () -> Limits,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Limits(val maxItems: Int, val maxBytes: Long, val protectMillis: Long)

    @Serializable
    data class Entry(
        val file: String,
        val ext: String,
        val mime: String,
        val size: Long,
        val lastUsed: Long,
        /** The URL, for URL entries. */
        val source: String? = null,
    )

    @Serializable
    private data class Index(val version: Int = 1, val entries: Map<String, Entry> = emptyMap())

    class Hit(val file: File, val entry: Entry)

    data class Usage(val items: Int, val bytes: Long)

    private val lock = Any()
    private var entries: MutableMap<String, Entry>? = null
    private var isDirty = false

    private val indexFile: File
        get() = dir.resolve(INDEX_FILE)

    fun lookup(key: String): Hit? =
        synchronized(lock) {
            val entries = entries()
            val entry = entries[key] ?: return null
            val file = dir.resolve(entry.file)
            if (!file.isFile) {
                entries -= key
                isDirty = true
                return null
            }
            val touched = entry.copy(lastUsed = clock())
            entries[key] = touched
            isDirty = true
            Hit(file, touched)
        }

    /** A new file in the cache directory to write into before [store]. */
    fun newTempFile(): File {
        // Loading the index sweeps unknown files, so it must not happen while this one is being written.
        synchronized(lock) { entries() }
        dir.mkdirs()
        return File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, dir)
    }

    /** Moves [temp] into the cache as the file of [key]. */
    fun store(key: String, temp: File, ext: String, mime: String, source: String? = null): Hit =
        synchronized(lock) {
            val name = "$key.$ext"
            val target = dir.resolve(name)
            if (target.exists())
                target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            val entries = entries()
            entries[key]?.takeIf { it.file != name }?.let { dir.resolve(it.file).delete() }
            val entry = Entry(name, ext, mime, target.length(), clock(), source)
            entries[key] = entry
            isDirty = true
            Hit(target, entry)
        }

    /**
     * Removes cached files: every file when [target] is null, else the file of a key, a cache file name, or a URL.
     * Returns how many were removed.
     */
    fun clear(target: String? = null): Int =
        synchronized(lock) {
            val entries = entries()
            if (target == null) {
                val count = entries.size
                dir.listFiles()?.forEach { if (it.name != INDEX_FILE) it.deleteRecursively() }
                entries.clear()
                isDirty = true
                saveLocked()
                return count
            }
            val name = target.trim().substringAfterLast('/')
            val keys = listOf(target.trim(), name, name.substringBefore('.'), keyForUrl(target.trim()))
            val key = keys.firstOrNull { it in entries }
            val removed = if (key != null) {
                entries.remove(key)?.let { dir.resolve(it.file).delete() }
                isDirty = true
                1
            } else {
                // A file the index doesn't know, given by name.
                if (name.isNotEmpty() && name != INDEX_FILE && dir.resolve(name).let { it.isFile && it.delete() }) 1 else 0
            }
            saveLocked()
            removed
        }

    fun usage(): Usage =
        synchronized(lock) {
            val entries = entries()
            Usage(entries.size, entries.values.sumOf { it.size })
        }

    /** Evicts what is over the limits and saves the index if it changed. */
    fun flush() {
        synchronized(lock) {
            evictLocked()
            saveLocked()
        }
    }

    private fun evictLocked() {
        val entries = entries()
        val limits = limits()
        var total = entries.values.sumOf { it.size }
        if (entries.size <= limits.maxItems && total <= limits.maxBytes)
            return
        val protectedSince = clock() - limits.protectMillis
        for ((key, entry) in entries.entries.sortedBy { it.value.lastUsed }) {
            if (entries.size <= limits.maxItems && total <= limits.maxBytes)
                break
            if (entry.lastUsed >= protectedSince)
                break
            dir.resolve(entry.file).delete()
            entries -= key
            total -= entry.size
            isDirty = true
        }
    }

    private fun saveLocked() {
        if (!isDirty)
            return
        dir.mkdirs()
        val temp = dir.resolve("$INDEX_FILE.tmp")
        temp.writeText(json.encodeToString(Index.serializer(), Index(entries = entries().toMap())))
        if (!temp.renameTo(indexFile)) {
            temp.copyTo(indexFile, overwrite = true)
            temp.delete()
        }
        isDirty = false
    }

    /** The index, loaded on first use; entries without files are dropped and files without entries deleted. */
    private fun entries(): MutableMap<String, Entry> {
        entries?.let { return it }
        val loaded = runCatching { json.decodeFromString(Index.serializer(), indexFile.readText()).entries }
            .getOrDefault(emptyMap())
            .filterTo(linkedMapOf()) { (_, entry) -> dir.resolve(entry.file).isFile }
        val known = loaded.values.mapTo(hashSetOf()) { it.file }
        dir.listFiles()
            ?.filter { it.name != INDEX_FILE && it.name !in known }
            ?.forEach { it.deleteRecursively() }
        entries = loaded
        return loaded
    }

    companion object {
        const val INDEX_FILE = "index.json"
        private const val TEMP_PREFIX = "download-"
        private const val TEMP_SUFFIX = ".part"

        private val BYTES_PREFIX = "BYTES|".toByteArray()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun keyForUrl(url: String): String =
            sha256Hex("URL|$url".toByteArray())

        fun keyForBytes(bytes: ByteArray): String =
            sha256Hex(BYTES_PREFIX, bytes)
    }
}
