/*
 * DatabaseFolder.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.legacy

import java.io.File

/**
 * The files behind legacy `DataBase`: text files in one folder, named the way MessengerBot names them.
 *
 * A name without a dot gets `.txt`, unless only the plain name exists, which is how files written by older StarLight
 * versions are kept reachable. A name may hold subfolders.
 */
internal class DatabaseFolder(var dir: File) {

    /** The file [name] stands for. */
    fun fileOf(name: String): File {
        val normalized = normalize(name)
        val file = File(dir, normalized)
        if (normalized != name && !file.exists()) {
            val plain = File(dir, name)
            if (plain.isFile)
                return plain
        }
        return file
    }

    /** The text of [name]; `null` when it doesn't exist or is a folder. */
    fun read(name: String): String? {
        val file = fileOf(name)
        if (!file.isFile)
            return null
        try {
            return file.readText()
        } catch (e: Exception) {
            throw RuntimeException("Failed to read database file '${normalize(name)}': ${e.message}", e)
        }
    }

    /** Replaces the text of [name] with [data], creating the file and its folders; the text now in the file. */
    fun write(name: String, data: String): String {
        val file = fileOf(name)
        try {
            file.parentFile?.mkdirs()
            file.writeText(data)
        } catch (e: Exception) {
            throw RuntimeException("Failed to set database file '${normalize(name)}': ${e.message}", e)
        }
        return read(name) ?: data
    }

    /** Adds [data] to the end of [name], creating the file and its folders; the text now in the file. */
    fun append(name: String, data: String): String {
        val file = fileOf(name)
        try {
            file.parentFile?.mkdirs()
            file.appendText(data)
        } catch (e: Exception) {
            throw RuntimeException("Failed to append to database file '${normalize(name)}': ${e.message}", e)
        }
        return read(name) ?: data
    }

    fun exists(name: String): Boolean =
        fileOf(name).exists()

    /** The size in bytes, `-1` when [name] doesn't exist. */
    fun size(name: String): Long =
        fileOf(name).let { if (it.exists()) it.length() else -1L }

    /** The names of everything directly in the folder. */
    fun list(): List<String> {
        if (!dir.isDirectory)
            return emptyList()
        return dir.list()?.sorted() ?: emptyList()
    }

    /**
     * Deletes [name], keeping a copy as `<file>.bak` next to it first when [backup] is set.
     *
     * @return `false` when it doesn't exist or can't be deleted.
     */
    fun remove(name: String, backup: Boolean): Boolean {
        val file = fileOf(name)
        if (!file.exists())
            return false
        if (backup) {
            try {
                File(file.path + ".bak").writeText(if (file.isFile) file.readText() else "")
            } catch (_: Exception) {
                // A backup that can't be made doesn't stop the removal.
            }
        }
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    companion object {

        /** [name] with `.txt` added when it has no dot. */
        fun normalize(name: String): String =
            if ('.' in name) name else "$name.txt"
    }
}
