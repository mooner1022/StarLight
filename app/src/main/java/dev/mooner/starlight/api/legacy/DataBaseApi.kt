package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import java.io.File

@Suppress("unused")
class DataBaseApi: Api<DataBaseApi.DataBase>() {

    /**
     * The `DataBase` global of legacy projects: text files in the project's own `data` folder.
     *
     * A file name is resolved in the `data` folder inside the project's folder, which is created when needed; each
     * project has its own. A name may contain subfolders, but they aren't created. Files are read and written as UTF-8.
     *
     * Of MessengerBot's `DataBase`, only these four methods exist; `exists`, `listFiles`, `clearDataBase`,
     * `getDataBaseDir` and the others are missing.
     *
     * @see guide:projects#files
     * @see guide:play-build#data-folder
     */
    class DataBase(
        private val project: Project
    ) {

        private fun getFile(fileName: String): File = project.getDataDirectory().resolve(fileName)

        /**
         * The text of the file [fileName] in the project's `data` folder.
         *
         * Unlike `FileStream.read`, a missing file throws rather than giving `null`.
         *
         * @param fileName The file's name in the `data` folder.
         * @throws java.io.IOException When the file doesn't exist or can't be read.
         */
        fun getDataBase(fileName: String): String = getFile(fileName).readText()

        /**
         * Writes [data] to the file [fileName] in the project's `data` folder, replacing what it held.
         *
         * The file is created if needed, but not the subfolders in [fileName].
         *
         * @param fileName The file's name in the `data` folder.
         * @param data The text to write.
         * @return [data].
         * @throws java.io.IOException When the file can't be written.
         */
        fun setDataBase(fileName: String, data: String): String {
            getFile(fileName).writeText(data)
            return data
        }

        /**
         * Adds [data] to the end of the file [fileName] in the project's `data` folder and returns its new text.
         *
         * Unlike [setDataBase], it doesn't create the file: it throws when the file doesn't exist.
         *
         * @param fileName The file's name in the `data` folder.
         * @param data The text to add.
         * @return The file's text after the change.
         * @throws java.io.IOException When the file doesn't exist or can't be read or written.
         */
        fun appendDataBase(fileName: String, data: String) = with(getFile(fileName)) {
            val org = readText()
            val appended = org + data
            writeText(appended)
            appended
        }

        /**
         * Deletes the file [fileName] in the project's `data` folder and says whether it was deleted.
         *
         * No backup is kept, unlike MessengerBot's optional `.bak` copy.
         *
         * @param fileName The file's name in the `data` folder.
         * @return `false` also when the file doesn't exist.
         */
        fun removeDataBase(fileName: String): Boolean = getFile(fileName).delete()
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
            args = arrayOf(String::class.java, String::class.java)
            returns = String::class.java
        },
        function {
            name = "appendDataBase"
            args = arrayOf(String::class.java, String::class.java)
            returns = String::class.java
        },
        function {
            name = "removeDataBase"
            args = arrayOf(String::class.java, String::class.java)
            returns = Boolean::class.java
        }
    )

    override val instanceClass: Class<DataBase> = DataBase::class.java

    override val instanceType: InstanceType = InstanceType.OBJECT

    override fun getInstance(project: Project): Any = DataBase(project)
}