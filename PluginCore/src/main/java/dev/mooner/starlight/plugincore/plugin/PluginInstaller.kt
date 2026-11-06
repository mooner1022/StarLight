package dev.mooner.starlight.plugincore.plugin

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.jar.JarFile

class PluginInstaller(
    private val pluginsDir: File,
    private val internalDir: File,
) {

    fun inspect(file: File): PluginInfo =
        readPluginInfo(file)

    fun install(source: File, sha256: String? = null, repository: PluginRepository? = null): PluginInfo {
        val checksum = source.sha256()
        if (sha256 != null && !checksum.equals(sha256, ignoreCase = true))
            throw InvalidPluginException("Checksum mismatch: ${source.name}")
        val info = readPluginInfo(source)

        pluginsDir.mkdirs()
        val target = fileOf(info.id)
        val replaced = installedFiles(info.id)
        val temp = pluginsDir.resolve(".${target.name}.tmp")
        try {
            source.copyTo(temp, overwrite = true)
            if (target.exists() && !target.delete())
                throw IOException("Failed to replace $target")
            if (!temp.renameTo(target))
                throw IOException("Failed to move $temp to $target")
        } finally {
            temp.delete()
        }
        for (file in replaced)
            if (file != target)
                file.delete()
        writeOrigin(info.id, PluginOrigin(repository, checksum, System.currentTimeMillis()))
        return info
    }

    fun originOf(id: String): PluginOrigin? {
        val origin = runCatching { json.decodeFromString<PluginOrigin>(originFileOf(id).readText()) }.getOrNull()
            ?: return null
        val file = fileOf(id).takeIf(File::isFile) ?: return null
        return origin.takeIf { file.sha256().equals(it.sha256, ignoreCase = true) }
    }

    private fun writeOrigin(id: String, origin: PluginOrigin) {
        val file = originFileOf(id)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(PluginOrigin.serializer(), origin))
    }

    private fun originFileOf(id: String): File =
        internalDir.resolve("plugin_origins").resolve("$id.json")

    fun fileOf(id: String): File =
        pluginsDir.resolve(fileNameOf(id))

    fun uninstall(file: File, info: PluginInfo?, keepData: Boolean) {
        for (target in listOfNotNull(file, info?.id?.let(::fileOf)))
            if (target.exists() && !target.delete())
                throw IOException("Failed to delete $target")
        if (info == null)
            return
        originFileOf(info.id).delete()
        val internal = internalDir.resolve("plugin_data").resolve(info.id)
        if (keepData) {
            internal.resolve("assets").deleteRecursively()
            internal.resolve("libs").deleteRecursively()
        } else {
            internal.deleteRecursively()
            pluginsDir.resolve(dataDirNameOf(info)).deleteRecursively()
        }
    }

    private fun installedFiles(id: String): List<File> =
        pluginsDir.listFiles { file -> file.isFile && file.extension in PluginLoader.SUPPORTED_EXT }
            ?.filter { file -> runCatching { readPluginInfo(file).id == id }.getOrDefault(false) }
            .orEmpty()

    companion object {

        private val ID_REGEX = "[A-Za-z0-9._-]+".toRegex()

        private val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        fun fileNameOf(id: String): String =
            "$id.${PluginLoader.SUPPORTED_EXT.first()}"

        fun dataDirNameOf(info: PluginInfo): String =
            "${info.name}(${info.id})"

        fun readPluginInfo(file: File): PluginInfo {
            val text = try {
                JarFile(file).use { jar ->
                    val entry = jar.getJarEntry("assets/starlight.json")
                        ?: jar.getJarEntry("res/raw/starlight.json")
                        ?: throw InvalidPluginException("Cannot find starlight.json")
                    jar.getInputStream(entry).use { it.readBytes().decodeToString() }
                }
            } catch (e: IOException) {
                throw InvalidPluginException("Cannot open plugin file: $e")
            }
            val info = try {
                json.decodeFromString<PluginInfo>(text)
            } catch (e: IllegalArgumentException) {
                throw InvalidPluginException("Malformed starlight.json: ${e.message}")
            }
            if (!info.id.matches(ID_REGEX))
                throw InvalidPluginException("Illegal plugin id: ${info.id}")
            if (info.id.lowercase() in PluginLoader.PRESERVED_IDS)
                throw InvalidPluginException("Preserved or unusable plugin id: ${info.id}")
            return info
        }

        fun File.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0)
                        break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
