package dev.mooner.starlight.utils

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Unpacks the zip read from [input] into [dir], refusing any entry that would land outside it. */
internal fun unzip(input: InputStream, dir: File) {
    val rootPath = dir.canonicalPath + File.separator
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val target = dir.resolve(entry.name).canonicalFile
            require(target.path.startsWith(rootPath)) { "Invalid entry: ${entry.name}" }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
        }
    }
}
