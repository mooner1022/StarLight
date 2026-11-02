package dev.mooner.starlight.plugincore.library

import java.io.File

/** Google Play builds may not load dex from outside the app, so there are no external libraries. */
class LibraryLoader {

    @Suppress("UNUSED_PARAMETER")
    fun loadLibraries(baseDirectory: File, codeCacheDirectory: File): Set<Library> =
        emptySet()
}
