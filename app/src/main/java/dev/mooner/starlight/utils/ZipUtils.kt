package dev.mooner.starlight.utils

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Writes every file under [dir] to [output] as a zip, with paths relative to [dir]. */
internal fun zip(dir: File, output: OutputStream) {
    ZipOutputStream(output.buffered()).use { zip ->
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            zip.putNextEntry(ZipEntry(file.relativeTo(dir).invariantSeparatorsPath))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

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
