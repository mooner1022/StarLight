package dev.mooner.starlight.plugincore.library

import java.io.File

/**
 * A library StarLight loaded from `StarLight/libs`, as `LibraryManager.getLibraries()` lists it.
 */
data class Library(
    /** The class loader of the library's classes. */
    val classLoader: ClassLoader,
    /** The library's file in `StarLight/libs`. */
    val file: File
) {

    /**
     * The class named [name], from the library or from the app, which its class loader asks first.
     *
     * @param name The class's binary name, such as `com.example.Parser`.
     * @return The class, as a `java.lang.Class`.
     * @throws ClassNotFoundException If there is no such class.
     */
    fun loadClass(name: String): Class<*> = classLoader.loadClass(name)
}
