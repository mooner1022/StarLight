package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.api.msgbot.ScriptArgs
import dev.mooner.starlight.languages.ScriptJson
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

@Suppress("unused")
class DataBaseApi: Api<DataBaseApi.DataBase>() {

    /**
     * The `DataBase` global of legacy projects: text files in a folder, by default the project's own `data` folder.
     *
     * A file name without a dot gets `.txt`: `"scores"` is `scores.txt`, while `"scores.json"` stays as it is. A name
     * that has no `.txt` file but matches a file without extension, as written by older StarLight versions, uses that
     * one. A name may contain subfolders, which writing creates. Files are read and written as UTF-8.
     *
     * Each project has its own `data` folder inside the project's folder, created when needed; MessengerBot shares one
     * folder between all scripts. `setDatabaseDir` points this project to another folder, also one outside the
     * project, until the project compiles again.
     *
     * @see guide:projects#files
     * @see guide:play-build#data-folder
     */
    class DataBase internal constructor(
        private val project: Project,
        private val json: ScriptJson,
    ) {

        private val folder = DatabaseFolder(project.getDataDirectory())

        /**
         * The text of the file [fileName].
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @return The whole text, or `null` when the file doesn't exist or is a folder.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         * @throws RuntimeException `Failed to read database file '<name>': <message>` when it can't be read.
         */
        fun getDataBase(fileName: Any?): String? =
            folder.read(nameOf(fileName))

        /**
         * Writes [data] to the file [fileName], replacing what it held.
         *
         * The file and the folders in its name are created when needed. `null` and `undefined` write nothing, leaving
         * an empty file; other values are written as their text (`5` as `5`).
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @param data The text to write.
         * @return The text now in the file.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         * @throws RuntimeException `Failed to set database file '<name>': <message>` when it can't be written.
         */
        fun setDataBase(fileName: Any?, data: Any?): String =
            folder.write(nameOf(fileName), dataOf(data))

        /**
         * Adds [data] to the end of the file [fileName], creating the file and its folders when needed.
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @param data The text to add; `null` and `undefined` add nothing.
         * @return The text now in the file.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         * @throws RuntimeException `Failed to append to database file '<name>': <message>` when it can't be written.
         */
        fun appendDataBase(fileName: Any?, data: Any?): String =
            folder.append(nameOf(fileName), dataOf(data))

        /**
         * Empties the file [fileName], creating it when it doesn't exist.
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @return The text now in the file, an empty string.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         */
        fun clearDataBase(fileName: Any?): String =
            setDataBase(fileName, "")

        /**
         * Whether the file or folder [fileName] exists.
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         */
        fun exists(fileName: Any?): Boolean =
            folder.exists(nameOf(fileName))

        /**
         * The size of the file [fileName] in bytes.
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @return The size, or `-1` when it doesn't exist.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         */
        fun getFileSize(fileName: Any?): Long =
            folder.size(nameOf(fileName))

        /**
         * The names of the files and folders in the database folder, backups (`.bak`) included.
         *
         * @return An array of names, without paths and sorted; empty when the folder doesn't exist.
         */
        fun listFiles(): Any? =
            json.parse(JsonArray(folder.list().map(::JsonPrimitive)).toString())

        /**
         * Deletes the file [fileName] and says whether it was deleted.
         *
         * A copy is kept first, next to the file with `.bak` added to its name (`scores.txt.bak`), replacing an older
         * copy. A copy that can't be made doesn't stop the deletion.
         *
         * @param fileName The file's name; `.txt` is added when it has no dot.
         * @return `false` when the file doesn't exist or can't be deleted.
         * @throws IllegalArgumentException When [fileName] isn't a string.
         */
        fun removeDataBase(fileName: Any?): Boolean =
            folder.remove(nameOf(fileName), backup = true)

        /**
         * The path of the folder the files are in.
         *
         * By default it is the project's `data` folder.
         */
        fun getDatabaseDir(): String =
            folder.dir.absolutePath

        /**
         * Same as [getDatabaseDir].
         */
        fun getDataBaseDir(): String =
            getDatabaseDir()

        /**
         * Points the database to the folder [newDir] for this project, until it compiles again.
         *
         * The folder isn't checked or created now; writing creates it. A path that isn't absolute is taken inside the
         * project's `data` folder.
         *
         * @param newDir The folder's path.
         * @throws IllegalArgumentException When [newDir] isn't a string.
         */
        fun setDatabaseDir(newDir: Any?) {
            val dir = File(ScriptArgs.string(newDir, "newDir"))
            folder.dir = if (dir.isAbsolute) dir else project.getDataDirectory().resolve(dir)
        }

        /**
         * Same as [setDatabaseDir].
         *
         * @param newDir The folder's path.
         */
        fun setDataBaseDir(newDir: Any?) {
            setDatabaseDir(newDir)
        }

        private fun nameOf(value: Any?): String =
            ScriptArgs.string(value, "fileName")

        private fun dataOf(value: Any?): String =
            if (ScriptArgs.isMissing(value)) "" else ScriptArgs.stringify(value)
    }

    override val name: String = "DataBase"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "getDataBase"
            args = arrayOf(String::class.java)
            returns = String::class.java
        },
        function {
            name = "setDataBase"
            args = arrayOf(String::class.java, Any::class.java)
            returns = String::class.java
        },
        function {
            name = "appendDataBase"
            args = arrayOf(String::class.java, Any::class.java)
            returns = String::class.java
        },
        function {
            name = "clearDataBase"
            args = arrayOf(String::class.java)
            returns = String::class.java
        },
        function {
            name = "exists"
            args = arrayOf(String::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "getFileSize"
            args = arrayOf(String::class.java)
            returns = Long::class.java
        },
        function {
            name = "listFiles"
            returns = Any::class.java
        },
        function {
            name = "removeDataBase"
            args = arrayOf(String::class.java)
            returns = Boolean::class.java
        },
        function {
            name = "getDatabaseDir"
            returns = String::class.java
        },
        function {
            name = "getDataBaseDir"
            returns = String::class.java
        },
        function {
            name = "setDatabaseDir"
            args = arrayOf(String::class.java)
        },
        function {
            name = "setDataBaseDir"
            args = arrayOf(String::class.java)
        }
    )

    override val instanceClass: Class<DataBase> = DataBase::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = DataBase(project, ScriptJson.current())
}
