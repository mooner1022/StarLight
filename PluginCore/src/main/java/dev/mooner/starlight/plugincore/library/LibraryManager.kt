package dev.mooner.starlight.plugincore.library

/**
 * The `.dex`, `.jar` and `.apk` libraries StarLight loaded from `StarLight/libs`; a global only while the developer
 * setting **외부 dex 라이브러리 로드** is on.
 *
 * Then every project gets it, whatever its API levels; the Google Play build never does. The libraries are loaded
 * when StarLight starts. A library's classes are reached through `Library.loadClass`.
 */
class LibraryManager(
    private val libs: MutableSet<Library>
) {

    /** The loaded libraries, in no particular order; one that failed to load is left out, and logged. */
    fun getLibraries(): List<Library> = libs.toList()
}