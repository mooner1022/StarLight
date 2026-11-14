package dev.mooner.starlight.plugincore.plugin

import java.io.File
import java.util.zip.ZipFile

/**
 * Unpacks what a plugin file carries beside its code, its native libraries and its assets, into the folder the
 * app keeps for them, and keeps that folder in step with the file.
 *
 * A plugin that was updated used to keep the libraries of the version before: a file was only written where none
 * was. New code then ran against an old library, which fails as it loads or, worse, later. Now the folder
 * remembers which plugin file it was unpacked from, by the file's size and time, and is unpacked again when that
 * changed. Only what was unpacked is removed first: whatever else a plugin put there stays.
 */
internal object PluginFiles {

    /** Kept in the folder: the plugin file it was unpacked from, then the files unpacked, one a line. */
    const val STAMP = ".unpacked"

    /**
     * The folders of a plugin file that hold libraries for the device's architecture, the best first. The names are
     * matched whole: by its first letters, `x86` also took `x86_64`'s libraries, and `armeabi` those of
     * `armeabi-v7a`, whichever came first in the file.
     */
    fun abiFolders(arch: String): List<String> = when (arch) {
        "x86"     -> listOf("x86")
        "x86_64"  -> listOf("x86_64")
        "armeabi" -> listOf("armeabi-v7a", "armeabi")
        "arm64"   -> listOf("arm64-v8a", "arm64")
        else      -> listOf(arch)
    }

    /** Unpacks the libraries for [arch] from [plugin] into [target]. Returns whether anything was written. */
    fun unpackNativeLibs(plugin: File, target: File, arch: String): Boolean =
        ZipFile(plugin).use { zip ->
            val names = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()
            val folder = abiFolders(arch).map { "lib/$it/" }.firstOrNull { prefix -> names.any { it.startsWith(prefix) } }
            unpack(zip, target, stampOf(plugin)) { name -> folder?.takeIf(name::startsWith)?.let(name::removePrefix) }
        }

    /**
     * Unpacks the assets of [plugin] into [target]. [revision] is the plugin's own count of its assets' changes,
     * which also has them unpacked again when it moves.
     */
    fun unpackAssets(plugin: File, target: File, revision: Int): Boolean =
        ZipFile(plugin).use { zip ->
            unpack(zip, target, "${stampOf(plugin)}-r$revision") { name -> name.takeIf { it.startsWith("assets/") }?.removePrefix("assets/") }
        }

    private fun stampOf(plugin: File): String =
        "${plugin.length()}-${plugin.lastModified()}"

    /**
     * Writes the entries [pathOf] gives a path for into [target], unless [target] was already unpacked from the
     * same [stamp]. The stamp is written last, so that an unpacking cut short is done again.
     */
    private fun unpack(zip: ZipFile, target: File, stamp: String, pathOf: (entryName: String) -> String?): Boolean {
        val stampFile = File(target, STAMP)
        val before = runCatching { stampFile.readLines() }.getOrDefault(emptyList())
        if (before.firstOrNull() == stamp)
            return false

        target.mkdirs()
        val root = target.canonicalFile
        stampFile.delete()
        // What the version before left there, and only that.
        for (path in before.drop(1))
            inside(root, path)?.delete()

        val written = mutableListOf<String>()
        for (entry in zip.entries()) {
            if (entry.isDirectory)
                continue
            val path = pathOf(entry.name)?.takeIf(String::isNotEmpty) ?: continue
            val file = inside(root, path)
                ?: throw IllegalStateException("The plugin file has an entry that leads outside its folder: ${entry.name}")
            if (file.isDirectory)
                file.deleteRecursively()
            file.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
            written += path
        }
        stampFile.writeText((listOf(stamp) + written).joinToString("\n"))
        return written.isNotEmpty() || before.size > 1
    }

    /** [path] under [root], or null when it leads out of it or is the stamp itself. */
    private fun inside(root: File, path: String): File? {
        val file = File(root, path).canonicalFile
        return file.takeIf { it.path.startsWith(root.path + File.separator) && it.name != STAMP }
    }
}
