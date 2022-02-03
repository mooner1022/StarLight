package dev.mooner.starlight.plugincore.library

import java.io.File

data class Library(
    val classLoader: ClassLoader,
    val file: File
) {

    fun loadClass(name: String): Class<*> = classLoader.loadClass(name)
}
