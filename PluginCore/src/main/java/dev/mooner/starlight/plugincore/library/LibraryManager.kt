package dev.mooner.starlight.plugincore.library

class LibraryManager(
    private val libs: MutableSet<Library>
) {

    fun getLibraries(): List<Library> = libs.toList()
}