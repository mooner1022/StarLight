/*
 * StoredTasks.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.schedule

import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * One project's stored tasks (`Schedule.at`), kept in `data/schedule/stored.json` in its folder, so that they outlive
 * compiles and restarts. Each change is written at once. A project's compiles share it. The file is read again when it
 * changed since, as when the project was imported again or the file was edited.
 */
internal class StoredTasks(home: Home) {

    /** Where the file lives and where problems are told: the project. */
    interface Home {
        /** The project's folder; the file is in `data/schedule` there. */
        val directory: File
        fun warn(message: String)
    }

    /** A task as kept: [dataText] is its data as JSON. */
    class Entry(val id: String, val name: String, val at: Long, val dataText: String)

    private data class Stamp(val modified: Long, val length: Long)

    @Volatile
    var home: Home = home

    private val lock = Any()

    /** The tasks as the file holds them; null until it is read. */
    private var entries: List<Entry>? = null

    /** The file as it was last read or written; null when there was none. */
    private var stamp: Stamp? = null

    val file: File
        get() = home.directory.resolve(FOLDER).resolve(FILE_NAME)

    /**
     * Keeps a task, under an ID of its own.
     *
     * @throws IllegalStateException When the project keeps [MAX_STORED] tasks already.
     */
    fun add(name: String, at: Long, dataText: String): Entry = synchronized(lock) {
        val current = loaded()
        check(current.size < MAX_STORED) {
            "A project can keep $MAX_STORED tasks of Schedule.at: cancel some first"
        }
        val taken = current.mapTo(HashSet(), Entry::id)
        var id: String
        do {
            id = Integer.toHexString(Random.nextInt()).padStart(8, '0')
        } while (id in taken)
        val entry = Entry(id, name, at, dataText)
        save(current + entry)
        entry
    }

    /** Removes the task [id]; whether it was kept. */
    fun remove(id: String): Boolean = synchronized(lock) {
        val current = loaded()
        val left = current.filterNot { it.id == id }
        if (left.size == current.size)
            return false
        save(left)
        true
    }

    /** Removes every task named [name]; those removed. */
    fun removeByName(name: String): List<Entry> = synchronized(lock) {
        val current = loaded()
        val (removed, left) = current.partition { it.name == name }
        if (removed.isNotEmpty())
            save(left)
        removed
    }

    fun list(): List<Entry> = synchronized(lock) {
        loaded()
    }

    private fun loaded(): List<Entry> {
        val known = entries
        if (known != null && stampOf(file) == stamp)
            return known
        return read().also {
            entries = it
            stamp = stampOf(file)
        }
    }

    private fun stampOf(file: File): Stamp? =
        if (file.isFile) Stamp(file.lastModified(), file.length()) else null

    /** The file's tasks; none when it doesn't exist, or can't be read, in which case it is kept aside. */
    private fun read(): List<Entry> {
        val file = file
        if (!file.isFile)
            return emptyList()
        return try {
            parse(file.readText())
        } catch (e: Exception) {
            val broken = File(file.parentFile, BROKEN_FILE_NAME)
            broken.delete()
            file.renameTo(broken)
            home.warn(translate {
                Locale.ENGLISH { "The tasks kept by Schedule.at can't be read, so the file was kept as " +
                    "$BROKEN_FILE_NAME and they start empty: ${e.message}" }
                Locale.KOREAN  { "Schedule.at으로 저장한 예약을 읽을 수 없어 $BROKEN_FILE_NAME(으)로 남기고 " +
                    "빈 상태로 시작해요: ${e.message}" }
            })
            emptyList()
        }
    }

    /** Writes [tasks], then takes them as the ones kept. Nothing is written for a project whose folder is gone. */
    private fun save(tasks: List<Entry>) {
        val file = file
        if (home.directory.isDirectory) {
            file.parentFile?.mkdirs()
            write(file, encode(tasks))
        }
        entries = tasks
        stamp = stampOf(file)
    }

    private fun parse(text: String): List<Entry> {
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalStateException("it holds no JSON object")
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        check(version == VERSION) { "its version is ${root["version"]}, not $VERSION" }
        val tasks = root["tasks"] as? JsonArray
            ?: throw IllegalStateException("it has no tasks array")
        return tasks.map { element ->
            val task = element as? JsonObject
                ?: throw IllegalStateException("a task isn't an object: $element")
            fun text(key: String) = (task[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
                ?: throw IllegalStateException("a task has no $key: $task")
            Entry(
                id = text("id"),
                name = text("name"),
                at = (task["at"] as? JsonPrimitive)?.longOrNull
                    ?: throw IllegalStateException("a task has no time: $task"),
                dataText = (task["data"] ?: JsonNull).toString(),
            )
        }
    }

    private fun encode(tasks: List<Entry>): String =
        JSON.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
            "version" to JsonPrimitive(VERSION),
            "tasks" to JsonArray(tasks.map { entry ->
                JsonObject(mapOf<String, JsonElement>(
                    "id" to JsonPrimitive(entry.id),
                    "name" to JsonPrimitive(entry.name),
                    "at" to JsonPrimitive(entry.at),
                    "data" to Json.parseToJsonElement(entry.dataText),
                ))
            }),
        )))

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
        const val FOLDER = "data/schedule"
        const val FILE_NAME = "stored.json"
        const val BROKEN_FILE_NAME = "stored.broken.json"

        /** Tasks one project may keep at once. */
        const val MAX_STORED = 100

        private const val VERSION = 1

        private val JSON = Json { prettyPrint = true }

        private val all: MutableMap<ProjectID, StoredTasks> = ConcurrentHashMap()

        /** The stored tasks of [project], whichever compile asks. */
        fun of(project: Project): StoredTasks {
            val home = ProjectHome(project)
            return all.computeIfAbsent(project.info.id) { StoredTasks(home) }
                // The project's object may have been made again since, with the same ID.
                .also { it.home = home }
        }
    }

    private class ProjectHome(private val project: Project) : Home {
        override val directory: File
            get() = project.directory

        override fun warn(message: String) =
            project.logger.w(message)
    }
}
