package dev.mooner.starlight.plugincore.library

import dalvik.system.PathClassLoader
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.logger.internal.Logger
import java.io.File

class LibraryLoader {

    private val logger = LoggerFactory.logger {  }

    fun loadLibraries(baseDirectory: File): Set<Library> {
        val folder = baseDirectory.resolve(LIBS_DIR)
        if (!folder.exists() || !folder.isDirectory) {
            folder.mkdirs()
            Logger.v(T, "Empty libs directory, skipping load")
            return emptySet()
        }

        val files = folder.listFiles { file -> file.extension in loadableExtensions }
        if (files.isNullOrEmpty()) {
            Logger.v(T, "Empty libs directory, skipping load")
            return emptySet()
        }

        val optDir = folder.resolve(OPT_DIR)
        if (!optDir.exists() || !optDir.isDirectory) optDir.mkdirs()

        val libs: MutableSet<Library> = hashSetOf()
        for (dexFile in files) {
            try {
                libs += loadLibrary(dexFile)
            } catch (e: Exception) {
                logger.error(e)
            }
        }
        return libs
    }

    private fun loadLibrary(file: File): Library {
        val classLoader = PathClassLoader(file.path, javaClass.classLoader!!)
        return Library(classLoader, file)
    }

    companion object {
        private const val T = "LibraryLoader"

        private const val LIBS_DIR = "libs"
        private const val OPT_DIR = "dex"

        private val loadableExtensions = arrayOf("dex", "apk", "jar")
    }
}