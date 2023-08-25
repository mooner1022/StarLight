package dev.mooner.starlight.api.legacy

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import java.io.File

@Suppress("unused")
class FileStreamApi: Api<FileStreamApi.FileStream>() {

    class FileStream {

        companion object {

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

            @JvmStatic
            fun read(path: String, nullIfNotExists: Boolean): String? =
                if (nullIfNotExists) read(path) else File(path).readText()

            @JvmStatic
            fun write(path: String, data: String): String =
                data.also { _ -> File(path).let { file ->
                    if (file.parentFile?.exists() != true)
                        file.parentFile?.mkdirs()
                    file.writeText(data)
                } }

            @JvmStatic
            fun append(path: String, data: String): String =
                with(File(path)) {
                    (readText() + data)
                        .also(::writeText)
                }

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