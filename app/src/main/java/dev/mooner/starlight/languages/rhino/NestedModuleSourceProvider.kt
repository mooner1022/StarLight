/*
 * NestedModuleSourceProvider.kt created by Minki Moon(mooner1022) on 8/8/23, 2:28 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages.rhino

import org.mozilla.javascript.commonjs.module.provider.ModuleSource
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider
import java.io.File
import java.net.URI

class NestedModuleSourceProvider(
    privilegedUris: Iterable<URI>,
    fallbackUris: Iterable<URI>?,
): UrlModuleSourceProvider(privilegedUris, fallbackUris) {

    override fun loadFromUri(uri: URI?, base: URI?, validator: Any?): ModuleSource? {
        val targetFile = uri?.let(::File)
        if (targetFile == null || !targetFile.exists())
            return super.loadFromUri(uri, base, validator)
        if (targetFile.isDirectory) {
            val entry = targetFile.resolve(ENTRY_FILE)
            if (!entry.exists())
                return super.loadFromUri(uri, base, validator)
            return loadFromActualUri(entry.toURI(), base, validator)
        }

        return super.loadFromUri(uri, base, validator)
    }

    companion object {
        private const val ENTRY_FILE = "index.js"
    }
}