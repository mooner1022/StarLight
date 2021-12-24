package com.mooner.starlight.plugincore.library

import com.mooner.starlight.plugincore.logger.Logger
import dalvik.system.PathClassLoader
import java.io.File

class LibraryLoader {

    companion object {
        private const val T = "LibraryLoader"

        private const val LIBS_DIR = "libs"
        private const val OPT_DIR = "dex"

        private val loadableExtensions = arrayOf("dex", "apk", "jar")
    }

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
                Logger.e(T, e)
            }
        }
        return libs
    }

    private fun loadLibrary(file: File): Library {
        val classLoader = PathClassLoader(file.path, javaClass.classLoader!!)
        return Library(classLoader, file)
    }
}