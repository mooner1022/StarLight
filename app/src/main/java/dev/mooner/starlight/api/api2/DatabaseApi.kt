/*
 * DatabaseApi.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.api2

import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DatabaseApi: Api<DatabaseApi.Database>() {

    /**
     * MessengerBot R's API2 `Database`: reads and writes files in the project's own `Database` folder; a global in
     * API2 projects.
     *
     * The folder is `Database` inside the project's folder, created when it is first used. Every project has its own;
     * it isn't the folder of the legacy `DataBase`, which is the project's `data` folder. A file name may name a
     * subfolder (`"rooms/a.json"`), which a write creates. A name that leaves the folder, such as `"../x"` or an
     * absolute path, throws a `SecurityException`. Names are used as given: no extension is added.
     *
     * Text is always UTF-8. Objects are kept as JSON, as the engine's `JSON.stringify` writes them on one line, and
     * come back as the engine's own objects and arrays.
     *
     * ```js
     * Database.writeObject("scores.json", { a: 1, b: [2, 3] });
     * const scores = Database.readObject("scores.json") ?? {};
     * ```
     *
     * Every name argument must be a string; a number or boolean is turned into its text, and `null` or `undefined`
     * throws an `IllegalArgumentException` saying `fileName must be a string`.
     *
     * @see guide:projects#api-levels
     */
    class Database internal constructor(
        private val project: Project,
        private val json: ScriptJson,
    ) {

        private val base: Path by lazy {
            project.directory.toPath().resolve("Database").toAbsolutePath().normalize()
        }

        /**
         * Whether a file or folder named [fileName] exists in the database folder.
         *
         * A blank name gives `false`.
         *
         * @param fileName The file's name, such as `"scores.json"`.
         * @throws SecurityException When the name leaves the database folder.
         */
        fun exists(fileName: Any?): Boolean {
            val path = resolve(ScriptArgs.string(fileName, "fileName"), forWrite = false)
            return path != null && Files.exists(path)
        }

        /**
         * Reads the JSON in the file [fileName] and returns it as an object, array or value.
         *
         * It returns `null` when the file doesn't exist or holds only whitespace, or when the name is blank. A byte
         * order mark at the start of the file isn't skipped, so such a file fails to parse.
         *
         * @param fileName The file's name, such as `"scores.json"`.
         * @throws RuntimeException With the message `Failed to read object from file <name>: <reason>` when the file
         * can't be read or isn't valid JSON, and when the name leaves the database folder.
         */
        fun readObject(fileName: Any?): Any? {
            val name = ScriptArgs.string(fileName, "fileName")
            try {
                val path = resolve(name, forWrite = false) ?: return null
                if (!Files.exists(path))
                    return null
                val text = Files.readAllLines(path, StandardCharsets.UTF_8).joinToString("\n").trim()
                if (text.isBlank())
                    return null
                return json.parse(text)
            } catch (e: Exception) {
                throw RuntimeException("Failed to read object from file $name: ${e.message}", e)
            }
        }

        /**
         * Reads the file [fileName] as UTF-8 text, as it is.
         *
         * It returns `""`, never `null`, when the file doesn't exist or the name is blank.
         *
         * @param fileName The file's name, such as `"notes.txt"`.
         * @throws SecurityException When the name leaves the database folder.
         * @throws java.io.IOException When the file can't be read, as when [fileName] names a folder.
         */
        fun readString(fileName: Any?): String {
            val path = resolve(ScriptArgs.string(fileName, "fileName"), forWrite = false)
            if (path == null || !Files.exists(path))
                return ""
            return String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        }

        /**
         * Writes [value] to the file [fileName] as JSON, replacing the file.
         *
         * The JSON is what `JSON.stringify(value)` gives, on one line. `null` is written as `null`. A value with no
         * JSON form, such as `undefined` or a function, fails. The file is written whole or not at all: a failed write
         * leaves the old file.
         *
         * @param fileName The file's name, such as `"scores.json"`; subfolders are created.
         * @param value What to write.
         * @throws RuntimeException With the message `Failed to write object to file <name>: <reason>` when the value
         * can't be turned into JSON, the name is blank or leaves the database folder, or the file can't be written.
         */
        fun writeObject(fileName: Any?, value: Any?) {
            val name = ScriptArgs.string(fileName, "fileName")
            try {
                val text = json.stringify(value)
                    ?: throw IllegalStateException("JSON.stringify did not return a string")
                val path = resolve(name, forWrite = true)
                    ?: throw IllegalArgumentException("fileName must not be blank")
                DatabaseFiles.writeAtomically(path, text)
            } catch (e: Exception) {
                throw RuntimeException("Failed to write object to file $name: ${e.message}", e)
            }
        }

        /**
         * Writes [value] to the file [fileName] as UTF-8 text, replacing the file.
         *
         * [value] needn't be a string: a number or boolean is written as its text, an array as `[a, b]` with each item
         * written the same way, `null` as `null` (and `undefined` as `undefined` in Rhino, but `null` in NeonJS, which can't tell them
         * apart), and any other object as the text
         * `String(value)` gives, such as `[object Object]`. Use `writeObject` to keep an object. The file is written
         * whole or not at all.
         *
         * @param fileName The file's name, such as `"notes.txt"`; subfolders are created.
         * @param value What to write.
         * @throws IllegalArgumentException When the name is blank.
         * @throws SecurityException When the name leaves the database folder.
         * @throws java.io.IOException When the file can't be written.
         */
        fun writeString(fileName: Any?, value: Any?) {
            val path = resolve(ScriptArgs.string(fileName, "fileName"), forWrite = true)
                ?: throw IllegalArgumentException("fileName must not be blank")
            DatabaseFiles.writeAtomically(path, stringOf(value))
        }

        // Rhino's undefined is written as "undefined"; ScriptArgs.stringify makes it "null".
        private fun stringOf(value: Any?): String =
            if (value === org.mozilla.javascript.Undefined.instance) "undefined" else ScriptArgs.stringify(value)

        private fun resolve(fileName: String, forWrite: Boolean): Path? =
            DatabaseFiles.resolve(base, fileName, forWrite)

        override fun toString(): String =
            "Database($base)"
    }

    override val name: String = "Database"

    override val objects: List<ApiObject> =
        getApiObjects<Database>()

    override val instanceClass: Class<Database> =
        Database::class.java

    override val instanceType: InstanceType =
        InstanceType.OBJECT

    override fun getInstance(project: Project): Any =
        Database(project, ScriptJson.current())
}

/** The file handling of [DatabaseApi.Database], apart from the project and the engine. */
internal object DatabaseFiles {

    /**
     * The path of [fileName] inside [base], or `null` for a blank name. Makes the folder (and, for a write, the file's
     * parent folder) on the way.
     *
     * @throws SecurityException When the name leaves [base].
     * @throws IllegalArgumentException When [base] exists and isn't a folder.
     */
    fun resolve(base: Path, fileName: String, forWrite: Boolean): Path? {
        if (fileName.isBlank())
            return null
        if (Files.exists(base)) {
            if (!Files.isDirectory(base))
                throw IllegalArgumentException("Database directory is not a directory: $base")
        } else {
            Files.createDirectories(base)
        }
        val target = base.resolve(fileName).normalize()
        if (!target.startsWith(base))
            throw SecurityException("Invalid file path (path traversal detected): $fileName")
        if (forWrite)
            target.parent?.let { Files.createDirectories(it) }
        return target
    }

    /** Writes [text] as UTF-8 to a temporary file beside [target], then moves it over [target]. */
    fun writeAtomically(target: Path, text: String) {
        val parent = target.parent ?: throw IllegalArgumentException("No parent folder for $target")
        Files.createDirectories(parent)
        val temp = Files.createTempFile(parent, "db", ".tmp")
        try {
            Files.write(temp, text.toByteArray(StandardCharsets.UTF_8))
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
