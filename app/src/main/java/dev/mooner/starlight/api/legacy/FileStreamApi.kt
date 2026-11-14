package dev.mooner.starlight.api.legacy

import android.os.Environment
import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

@Suppress("unused")
class FileStreamApi: Api<FileStreamApi.FileStream>() {

    /**
     * The `FileStream` global: reads, writes and manages files and folders by path, with text and JSON helpers; a
     * global in legacy and API2 projects.
     *
     * Paths are used as given and aren't relative to the project's folder, so pass absolute paths; `getSdcardPath()`
     * gives the storage's root. On the Google Play build StarLight has no storage permission, so only paths in its own
     * app folders work. Files are read and written as UTF-8.
     *
     * A path can be any value: a number is used as its text. `null` and `undefined` throw
     * `"path must be a string"`, except in the members that only ask about a path (`exists`, `isFile`, `isDirectory`,
     * `getExtension`, `getFileName`, `getFileSize`, `getParentPath`), which give `false`, `null` or `-1` for them. When
     * an operation fails, it throws an error saying `Failed to <what> '<path>': <reason>`.
     *
     * @see guide:play-build#data-folder
     */
    class FileStream internal constructor(private val json: ScriptJson) {

        private inline fun <T> failing(what: String, block: () -> T): T =
            try {
                block()
            } catch (e: Exception) {
                throw RuntimeException("Failed to $what: ${e.message}")
            }

        private fun prepare(file: File) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }

        private fun readOrNull(path: String): String? {
            val file = File(path)
            if (!file.exists()) return null
            return failing("read file '$path'") { file.readText() }
        }

        /**
         * The text of the file at [path], or `null` if it doesn't exist.
         *
         * @param path The file's absolute path.
         * @throws RuntimeException When the file exists but can't be read, such as a folder.
         */
        fun read(path: Any?): String? =
            readOrNull(ScriptArgs.string(path, "path"))

        /**
         * The text of the file at [path], with a choice of what happens when it doesn't exist.
         *
         * This overload is StarLight's own; MessengerBot has only `read(path)`.
         *
         * @param path The file's absolute path.
         * @param nullIfNotExists `true` to get `null`, as `read(path)` does; `false` to throw instead.
         * @throws java.io.IOException When [nullIfNotExists] is `false` and the file doesn't exist.
         * @throws RuntimeException When the file exists but can't be read.
         */
        fun read(path: Any?, nullIfNotExists: Boolean): String? {
            val text = readOrNull(ScriptArgs.string(path, "path"))
            if (text == null && !nullIfNotExists)
                throw FileNotFoundException("${ScriptArgs.stringify(path)} doesn't exist")
            return text
        }

        /**
         * The value of the JSON file at [path], as a script object, array or primitive.
         *
         * @param path The file's absolute path.
         * @return `null` if the file doesn't exist.
         * @throws RuntimeException When the file holds no valid JSON (an empty file included) or can't be read.
         */
        fun readJson(path: Any?): Any? {
            val p = ScriptArgs.string(path, "path")
            return failing("read JSON from file '$p'") {
                val text = readOrNull(p) ?: return null
                json.parse(text)
            }
        }

        /**
         * Writes [data] as text to the file at [path], replacing what it held.
         *
         * The file and any missing parent folders are created. A number is written as its text, `null` as `"null"` and an
         * array as `[a, b]`.
         *
         * @param path The file's absolute path.
         * @param data What to write.
         * @return The text that was written.
         * @throws RuntimeException When the file can't be written, such as outside the app's folders on the Google Play
         * build.
         */
        fun write(path: Any?, data: Any?): String {
            val p = ScriptArgs.string(path, "path")
            val text = ScriptArgs.stringify(data)
            failing("write file '$p'") {
                val file = File(p)
                prepare(file)
                file.writeText(text)
            }
            return text
        }

        /**
         * Writes [value] as JSON, as `JSON.stringify` writes it, to the file at [path], replacing what it held.
         *
         * The file and any missing parent folders are created.
         *
         * @param path The file's absolute path.
         * @param value What `JSON.stringify` can write.
         * @return `true`; failures throw.
         * @throws RuntimeException When [value] has no JSON form, such as `undefined` or a function, when `JSON.stringify`
         * fails on it (a cyclic structure or a BigInt; the engine's own error is the cause), or when the file can't be
         * written.
         */
        fun writeJson(path: Any?, value: Any?): Boolean {
            val p = ScriptArgs.string(path, "path")
            return failing("write JSON to file '$p'") {
                val text = json.stringify(value) ?: throw IllegalStateException("JSON.stringify did not return a string")
                write(p, text)
                true
            }
        }

        /**
         * [writeJson] without a result.
         *
         * @param path The file's absolute path.
         * @param value What `JSON.stringify` can write.
         * @throws RuntimeException When [value] has no JSON form or the file can't be written.
         */
        fun saveJson(path: Any?, value: Any?) {
            writeJson(path, value)
        }

        /**
         * Writes [data] as text to the file at [path], replacing what it held.
         *
         * The file and any missing parent folders are created.
         *
         * @param path The file's absolute path.
         * @param data What to write, as in [write].
         * @throws RuntimeException When the file can't be written.
         */
        fun save(path: Any?, data: Any?) {
            save(path, data, false)
        }

        /**
         * Writes [data] as text to the file at [path], at its end or replacing what it held.
         *
         * The file and any missing parent folders are created.
         *
         * @param path The file's absolute path.
         * @param data What to write, as in [write].
         * @param append `true` to add to the end of the file; `false` (the default) to replace its text. The text
         * `"true"` and `"false"` count, and a number is `true` unless it is `0`.
         * @throws RuntimeException When the file can't be written, or [append] is an object.
         */
        fun save(path: Any?, data: Any?, append: Any?) {
            val p = ScriptArgs.string(path, "path")
            failing("save file '$p'") {
                val text = ScriptArgs.stringify(data)
                val add = ScriptArgs.boolean(append, "append", false)
                val file = File(p)
                prepare(file)
                if (add) file.appendText(text) else file.writeText(text)
            }
        }

        /**
         * Adds [data] as text to the end of the file at [path] and returns the file's new text.
         *
         * The file and any missing parent folders are created when missing. It reads the whole file and writes it back.
         *
         * @param path The file's absolute path.
         * @param data What to add, as in [write].
         * @return The file's text after the change.
         * @throws RuntimeException When the file can't be read or written.
         */
        fun append(path: Any?, data: Any?): String {
            val p = ScriptArgs.string(path, "path")
            val text = ScriptArgs.stringify(data)
            return failing("append to file '$p'") {
                val file = File(p)
                prepare(file)
                val result = file.readText() + text
                file.appendText(text)
                result
            }
        }

        /**
         * Deletes the file at [path] and says whether it was deleted.
         *
         * A folder is deleted only if it is empty.
         *
         * @param path The file's absolute path.
         * @return `false` also when nothing is at [path].
         */
        fun remove(path: Any?): Boolean {
            val p = ScriptArgs.string(path, "path")
            return failing("remove file '$p'") { File(p).delete() }
        }

        /**
         * Creates an empty file at [path], and the missing folders above it.
         *
         * @param path The file's absolute path.
         * @return `true` if the file was created, `false` if it existed already.
         * @throws RuntimeException When it can't be created.
         */
        fun create(path: Any?): Boolean {
            val p = ScriptArgs.string(path, "path")
            return failing("create file '$p'") {
                val file = File(p)
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
        }

        /**
         * Creates the folder [path], and the missing folders above it.
         *
         * @param path The folder's absolute path.
         * @return `true` if a folder was created, `false` if it existed already or couldn't be created.
         */
        fun createDir(path: Any?): Boolean {
            val p = ScriptArgs.string(path, "path")
            return failing("create directory '$p'") { File(p).mkdirs() }
        }

        /**
         * Copies the file [path1] to [path2], replacing what is there, and creates the missing folders above it.
         *
         * Copying a folder makes an empty folder: its contents aren't copied.
         *
         * @param path1 The source's absolute path.
         * @param path2 The destination's absolute path.
         * @return `true`; failures throw.
         * @throws RuntimeException When the source doesn't exist or the copy fails.
         */
        fun copyFile(path1: Any?, path2: Any?): Boolean {
            val p1 = ScriptArgs.string(path1, "path1")
            val p2 = ScriptArgs.string(path2, "path2")
            return failing("copy file from '$p1' to '$p2'") {
                val source = File(p1)
                if (!source.exists())
                    throw FileNotFoundException("Source file does not exist: $p1")
                val target = File(p2)
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                true
            }
        }

        /**
         * Moves the file [path1] to [path2], and creates the missing folders above it.
         *
         * It renames the file, so it can fail between different storages, such as the app's own folders and an SD card.
         *
         * @param path1 The source's absolute path.
         * @param path2 The destination's absolute path.
         * @return [path2] as text; failures throw.
         * @throws RuntimeException When the source doesn't exist or can't be moved.
         */
        fun moveFile(path1: Any?, path2: Any?): String {
            val p1 = ScriptArgs.string(path1, "path1")
            val p2 = ScriptArgs.string(path2, "path2")
            return failing("move file from '$p1' to '$p2'") {
                val source = File(p1)
                if (!source.exists())
                    throw FileNotFoundException("Source file does not exist: $p1")
                val target = File(p2)
                target.parentFile?.mkdirs()
                if (!source.renameTo(target))
                    throw IOException("Failed to move file")
                p2
            }
        }

        /**
         * Whether something exists at [path]. It is `false` for a path that is no value.
         *
         * @param path The absolute path.
         */
        fun exists(path: Any?): Boolean =
            try { File(ScriptArgs.string(path, "path")).exists() } catch (_: Exception) { false }

        /**
         * Whether [path] is a file. It is `false` for a path that is no value.
         *
         * @param path The absolute path.
         */
        fun isFile(path: Any?): Boolean =
            try { File(ScriptArgs.string(path, "path")).isFile } catch (_: Exception) { false }

        /**
         * Whether [path] is a folder. It is `false` for a path that is no value.
         *
         * @param path The absolute path.
         */
        fun isDirectory(path: Any?): Boolean =
            try { File(ScriptArgs.string(path, "path")).isDirectory } catch (_: Exception) { false }

        /**
         * The extension of the name at the end of [path], without the dot and as written: `"gz"` for `a.tar.gz`.
         *
         * Nothing is looked up, so a path needn't exist.
         *
         * @param path The path.
         * @return `null` when the name has no extension: no dot, a dot only at its start (`.bashrc`) or only at its
         * end.
         */
        fun getExtension(path: Any?): String? =
            try {
                val name = File(ScriptArgs.string(path, "path")).name
                val dot = name.lastIndexOf('.')
                if (dot <= 0 || dot >= name.length - 1) null else name.substring(dot + 1)
            } catch (_: Exception) {
                null
            }

        /**
         * The name at the end of [path]: `"a.txt"` for `/data/a.txt`.
         *
         * @param path The path.
         * @return `""` for `/`; `null` for a path that is no value.
         */
        fun getFileName(path: Any?): String? =
            try { File(ScriptArgs.string(path, "path")).name } catch (_: Exception) { null }

        /**
         * The size in bytes of the file at [path].
         *
         * @param path The file's absolute path.
         * @return `-1` if there is no such file, or [path] is a folder.
         */
        fun getFileSize(path: Any?): Long =
            try {
                val file = File(ScriptArgs.string(path, "path"))
                if (file.exists() && file.isFile) file.length() else -1L
            } catch (_: Exception) {
                -1L
            }

        /**
         * The absolute path of the folder that holds [path].
         *
         * Nothing is looked up, so a path needn't exist.
         *
         * @param path The path.
         * @return `null` when [path] has no parent: `/`, or a relative path of one name.
         */
        fun getParentPath(path: Any?): String? =
            try { File(ScriptArgs.string(path, "path")).parentFile?.absolutePath } catch (_: Exception) { null }

        /**
         * The root of the device's shared storage, usually `/storage/emulated/0`.
         *
         * @throws RuntimeException When it can't be told.
         */
        @Suppress("DEPRECATION")
        fun getSdcardPath(): String =
            try {
                Environment.getExternalStorageDirectory().absolutePath
            } catch (e: Exception) {
                throw RuntimeException("Failed to get SD card path: ${e.message}")
            }

        /**
         * The names of what the folder [path] holds, files and folders mixed, in no particular order, as an array of
         * text.
         *
         * Names only, not paths. Unlike MessengerBot's, the array is a script array, so it has `map` and the like too.
         *
         * @param path The folder's absolute path.
         * @return `null` if [path] doesn't exist or isn't a folder, or on any failure; an empty array if the folder
         * can't be listed.
         */
        fun listFiles(path: Any?): Any? =
            try {
                val dir = File(ScriptArgs.string(path, "path"))
                if (!dir.exists() || !dir.isDirectory) {
                    null
                } else {
                    val names = dir.listFiles()?.map { JsonPrimitive(it.name) } ?: emptyList()
                    json.parse(JsonArray(names).toString())
                }
            } catch (_: Exception) {
                null
            }
    }

    override val name: String = "FileStream"

    override val objects: List<ApiObject> = getApiObjects<FileStream>()

    override val instanceClass: Class<FileStream> = FileStream::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = FileStream(ScriptJson.current())
}
