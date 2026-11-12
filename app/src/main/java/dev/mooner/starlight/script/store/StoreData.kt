/*
 * StoreData.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.store

import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.ScriptTimers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One store of a project: the values in `data/store/<name>.json`, kept in memory as JSON, and written back a moment
 * after they change. A project's compiles share it, so an old version saving late and a new one reading early see the
 * same values.
 *
 * The file may be changed by others too: by hand, or by StarLight AI. [open] reads it again when it changed and
 * nothing here waits to be saved, and a save puts this store's changes on top of what the file holds by then, so
 * neither side's changes are lost unless both changed the same key.
 */
internal class StoreData(val name: String, home: StoreHome) {

    /** Where the store lives and where it reports problems: its project. */
    interface StoreHome {
        /** The project's folder; the store's file is in `data/store` there. */
        val directory: File
        fun warn(message: String)
        fun error(message: String)
    }

    private data class Stamp(val modified: Long, val length: Long)

    @Volatile
    var home: StoreHome = home

    private val lock = Any()

    /** The values as the script sees them. */
    private var entries = LinkedHashMap<String, JsonElement>()

    /** What changed since the file was last read or written: a value, or null for one deleted. */
    private val pending = LinkedHashMap<String, JsonElement?>()

    /** Whether everything was deleted since the file was last read or written; [pending] holds what came after. */
    private var cleared = false

    /** The file as it was last read or written; null when there was none. */
    private var stamp: Stamp? = null

    private var loaded = false

    private var saveScheduled = false

    private val hasChanges: Boolean
        get() = pending.isNotEmpty() || cleared

    val file: File
        get() = home.directory.resolve(FOLDER).resolve("$name.json")

    /**
     * Reads the file the first time, and again if it changed since and nothing here waits to be saved.
     *
     * @throws IllegalStateException When the file isn't a store's JSON. It is left as it is.
     */
    fun open() = synchronized(lock) {
        val file = file
        val disk = stampOf(file)
        if (loaded && (hasChanges || disk == stamp))
            return
        entries = read(file)
        stamp = disk
        loaded = true
    }

    fun get(key: String): JsonElement? = synchronized(lock) { entries[key] }

    fun has(key: String): Boolean = synchronized(lock) { key in entries }

    fun keys(): List<String> = synchronized(lock) { entries.keys.toList() }

    fun size(): Int = synchronized(lock) { entries.size }

    fun all(): JsonObject = synchronized(lock) { JsonObject(LinkedHashMap(entries)) }

    fun set(key: String, value: JsonElement) = synchronized(lock) {
        entries[key] = value
        pending[key] = value
        scheduleSave()
    }

    /**
     * Replaces the value of [key] with what [transform] makes of it, with no other change of this store in between.
     * [transform] runs while the store is held: other events using it wait for it.
     */
    fun update(key: String, transform: (JsonElement?) -> JsonElement): JsonElement = synchronized(lock) {
        transform(entries[key]).also { set(key, it) }
    }

    fun delete(key: String): Boolean = synchronized(lock) {
        if (entries.remove(key) == null)
            return false
        pending[key] = null
        scheduleSave()
        true
    }

    fun clear() = synchronized(lock) {
        if (entries.isEmpty() && !hasChanges)
            return
        entries.clear()
        pending.clear()
        cleared = true
        scheduleSave()
    }

    /**
     * Writes what changed, now. A file changed meanwhile by someone else is read first, and this store's changes go on
     * top of it; one that can't be read is kept beside it as `<name>.broken.json`.
     *
     * Nothing is written for a project whose folder is gone, as after it was deleted.
     */
    fun save() = synchronized(lock) {
        if (!hasChanges)
            return
        if (!home.directory.isDirectory) {
            pending.clear()
            cleared = false
            return
        }
        val file = file
        val disk = stampOf(file)
        if (disk != stamp) {
            val base = try {
                read(file)
            } catch (e: IllegalStateException) {
                val broken = File(file.parentFile, "$name.broken.json")
                file.renameTo(broken)
                home.warn(translate {
                    Locale.ENGLISH { "Store '$name' was changed outside the script and can't be read, so it was kept as " +
                        "${broken.name} and written again from the script's values: ${e.message}" }
                    Locale.KOREAN  { "스크립트 밖에서 바뀐 저장소 '$name'을(를) 읽을 수 없어 ${broken.name}(으)로 남기고, " +
                        "스크립트의 값으로 다시 저장했어요: ${e.message}" }
                })
                null
            }
            if (base != null) {
                if (!cleared)
                    entries = base.also { merged -> pending.forEach { (key, value) -> if (value == null) merged.remove(key) else merged[key] = value } }
                else
                    entries = LinkedHashMap<String, JsonElement>().also { merged -> pending.forEach { (key, value) -> if (value != null) merged[key] = value } }
            }
        }
        file.parentFile?.mkdirs()
        write(file, JSON.encodeToString(JsonObject.serializer(), JsonObject(entries)))
        stamp = stampOf(file)
        pending.clear()
        cleared = false
    }

    /** [save], with a failure told to the project's log rather than thrown. */
    fun saveQuietly() {
        try {
            save()
        } catch (e: Exception) {
            home.error(translate {
                Locale.ENGLISH { "Store '$name' couldn't be saved: $e" }
                Locale.KOREAN  { "저장소 '$name'을(를) 저장하지 못했어요: $e" }
            })
        }
    }

    private fun scheduleSave() {
        if (saveScheduled)
            return
        saveScheduled = true
        ScriptTimers.schedule(SAVE_DELAY_MILLIS) {
            synchronized(lock) { saveScheduled = false }
            saveQuietly()
        }
    }

    private fun stampOf(file: File): Stamp? =
        if (file.isFile) Stamp(file.lastModified(), file.length()) else null

    /** The values [file] holds; none when it doesn't exist. */
    private fun read(file: File): LinkedHashMap<String, JsonElement> {
        if (!file.isFile)
            return LinkedHashMap()
        val element = try {
            Json.parseToJsonElement(file.readText())
        } catch (e: Exception) {
            throw IllegalStateException("Store '$name' can't be read from ${file.path}: ${e.message}. Fix the file or " +
                "delete it.", e)
        }
        if (element !is JsonObject)
            throw IllegalStateException("Store '$name' can't be read from ${file.path}: it holds no JSON object. Fix " +
                "the file or delete it.")
        return LinkedHashMap(element)
    }

    /** Writes [text] to a file beside [file] and puts it in its place, so the file is whole whenever it is read. */
    private fun write(file: File, text: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** In the project's folder, apart from what scripts write themselves to `data`. */
        const val FOLDER = "data/store"
        const val SAVE_DELAY_MILLIS = 1_000L

        private val JSON = Json { prettyPrint = true }
    }
}
