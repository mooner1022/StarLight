package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import java.io.File

@Suppress("unused")
class FileStreamApi: Api<FileStreamApi.FileStream>() {

    /**
     * The `FileStream` global: reads and writes text files by path; a global in legacy and API2 projects.
     *
     * Its members are static: use them on `FileStream` itself. Paths are used as given and aren't relative to the
     * project's folder, so pass absolute paths. On the Google Play build StarLight has no storage permission, so only
     * paths in its own app folders work. Files are read and written as UTF-8.
     *
     * Of MessengerBot's `FileStream`, only `read`, `write`, `append` and `remove` exist; `exists`, `listFiles`,
     * `readJson`, `writeJson`, `getSdcardPath` and the others are missing.
     *
     * @see guide:play-build#data-folder
     */
    class FileStream {

        companion object {

            /**
             * The text of the file at [path], or `null` if it doesn't exist or can't be read.
             *
             * @param path The file's absolute path.
             */
            @JvmStatic
            fun read(path: String): String? =
                File(path).let {
                    try {
                        if (it.exists())
                            it.readText()
                        else null
                    } catch (e: Exception) {
                        null
                    }
                }

            /**
             * The text of the file at [path], with a choice of what happens when it can't be read.
             *
             * This overload is StarLight's own; MessengerBot has only `read(path)`.
             *
             * @param path The file's absolute path.
             * @param nullIfNotExists `true` to get `null`, as `read(path)` does; `false` to throw instead.
             * @throws java.io.IOException When [nullIfNotExists] is `false` and the file doesn't exist or can't be
             * read.
             */
            @JvmStatic
            fun read(path: String, nullIfNotExists: Boolean): String? =
                if (nullIfNotExists) read(path) else File(path).readText()

            /**
             * Writes [data] to the file at [path], replacing what it held.
             *
             * The file and any missing parent folders are created.
             *
             * @param path The file's absolute path.
             * @param data The text to write.
             * @return [data].
             * @throws java.io.IOException When the file can't be written, such as outside the app's folders on the
             * Google Play build.
             */
            @JvmStatic
            fun write(path: String, data: String): String =
                data.also { _ -> File(path).let { file ->
                    if (file.parentFile?.exists() != true)
                        file.parentFile?.mkdirs()
                    file.writeText(data)
                } }

            /**
             * Adds [data] to the end of the file at [path] and returns the file's new text.
             *
             * Unlike [write], it doesn't create the file: it throws when the file doesn't exist. It reads the whole
             * file and writes it back.
             *
             * @param path The file's absolute path.
             * @param data The text to add.
             * @return The file's text after the change.
             * @throws java.io.IOException When the file doesn't exist or can't be read or written.
             */
            @JvmStatic
            fun append(path: String, data: String): String =
                with(File(path)) {
                    (readText() + data)
                        .also(::writeText)
                }

            /**
             * Deletes the file at [path] and says whether it was deleted.
             *
             * A folder is deleted only if it is empty.
             *
             * @param path The file's absolute path.
             * @return `false` also when nothing is at [path].
             */
            @JvmStatic
            fun remove(path: String): Boolean =
                File(path).delete()
        }
    }

    override val name: String = "FileStream"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "read"
            args = arrayOf(String::class.java)
            returns = String::class.java
        },
        function {
            name = "write"
            args = arrayOf(String::class.java, String::class.java)
            returns = String::class.java
        },
        function {
            name = "append"
            args = arrayOf(String::class.java, String::class.java)
            returns = String::class.java
        },
        function {
            name = "remove"
            args = arrayOf(String::class.java, String::class.java)
            returns = Boolean::class.java
        }
    )

    override val instanceClass: Class<FileStream> = FileStream::class.java

    override val instanceType: InstanceType = InstanceType.CLASS

    override fun getInstance(project: Project): Any = FileStream::class.java
}