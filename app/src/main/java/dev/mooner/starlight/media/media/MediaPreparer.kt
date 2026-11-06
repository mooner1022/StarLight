/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.media

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Turns sources into files that can be shared: downloads, decodes, copies or reuses cached files. */
class MediaPreparer(
    private val cache: MediaCache,
    private val downloader: Downloader,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Options(
        val useCache: Boolean,
        val timeoutMillis: Int,
        val maxBytes: Long,
        val headers: Map<String, String> = emptyMap(),
    )

    /** A file to share. Files in a send's [WorkDir] are deleted after it; others (cached, local) are kept. */
    data class Prepared(val file: File, val mime: String)

    class PrepareException(message: String) : IOException(message)

    /** The directory of one send, which hands out names that don't collide. */
    class WorkDir(val dir: File) {

        private val taken: MutableSet<String> = hashSetOf()

        @Synchronized
        fun reserve(name: String): File {
            dir.mkdirs()
            val ext = name.substringAfterLast('.', "")
            val base = if (ext.isEmpty()) name else name.dropLast(ext.length + 1)
            var candidate = name
            var n = 1
            while (!taken.add(candidate.lowercase()) || dir.resolve(candidate).exists()) {
                candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
                n++
            }
            return dir.resolve(candidate)
        }

        fun newTempFile(): File {
            dir.mkdirs()
            return File.createTempFile("download-", ".part", dir)
        }
    }

    /**
     * @param name the name to send the file under (an extension is added), or null to keep the source's own.
     * @param ordinal numbers files of one send in generated names.
     */
    fun prepare(source: MediaSource, name: String?, work: WorkDir, options: Options, ordinal: Int): Prepared =
        when (source) {
            is MediaSource.Local  -> prepareLocal(source.file, name, work)
            is MediaSource.Remote -> prepareRemote(source.url, name, work, options, ordinal)
            is MediaSource.Bytes  -> prepareBytes(source.bytes, source.mimeHint, name, work, options, ordinal)
            is MediaSource.Image  -> prepareBytes(compress(source.bitmap), "image/png", name, work, options, ordinal)
        }

    private fun prepareLocal(file: File, name: String?, work: WorkDir): Prepared {
        if (!file.isFile)
            throw PrepareException(if (file.isDirectory) "Not a file: ${file.path}" else "File not found: ${file.path}")
        if (!file.canRead())
            throw PrepareException("File is not readable (storage permission?): ${file.path}")
        val header = FileTypes.readHeader(file)
        val ext = FileTypes.resolveExtension(FileTypes.sniff(header), listOf(FileTypes.extensionOfName(file.name)), header)
        val mime = FileTypes.mimeOf(ext)
        // Shared in place; the sender copies it if StarLight's FileProvider can't serve its directory.
        if (name == null)
            return Prepared(file, mime)
        // Keep an extension this plugin doesn't know (such as .apk) rather than the guessed one.
        val ownExt = file.extension.lowercase().takeIf { it.isNotEmpty() && it.length <= 10 && it !in FileTypes.MIME_BY_EXT }
        val target = work.reserve(replaceExtension(name, ownExt ?: ext))
        linkOrCopy(file, target)
        return Prepared(target, mime)
    }

    private fun prepareRemote(url: String, name: String?, work: WorkDir, options: Options, ordinal: Int): Prepared {
        if (options.useCache) {
            val key = MediaCache.keyForUrl(url)
            val hit = cache.lookup(key) ?: run {
                val temp = cache.newTempFile()
                val result = try {
                    downloader.download(url, temp, options.timeoutMillis, options.headers, options.maxBytes)
                } catch (e: Throwable) {
                    temp.delete()
                    throw e
                }
                val ext = remoteExtension(url, result)
                cache.store(key, temp, ext, FileTypes.mimeOf(ext), source = url)
            }
            return fromCache(hit, name, work)
        }

        val temp = work.newTempFile()
        val result = downloader.download(url, temp, options.timeoutMillis, options.headers, options.maxBytes)
        val ext = remoteExtension(url, result)
        val fileName = name
            ?: FileTypes.fileNameOfContentDisposition(result.contentDisposition)
            ?: FileTypes.fileNameOfUrl(result.url)
            ?: FileTypes.fileNameOfUrl(url)
            ?: generatedName(ordinal)
        val target = work.reserve(replaceExtension(fileName, ext))
        moveTo(temp, target)
        return Prepared(target, FileTypes.mimeOf(ext))
    }

    private fun prepareBytes(bytes: ByteArray, mimeHint: String?, name: String?, work: WorkDir, options: Options, ordinal: Int): Prepared {
        if (bytes.isEmpty())
            throw PrepareException("No data to send (0 bytes)")
        if (bytes.size > options.maxBytes)
            throw PrepareException("Data is larger than the limit (${bytes.size} bytes, limit ${options.maxBytes})")
        val header = bytes.copyOf(minOf(bytes.size, FileTypes.HEADER_BYTES))
        val ext = FileTypes.resolveExtension(FileTypes.sniff(header), listOf(FileTypes.extensionOfMime(mimeHint)), header)
        if (options.useCache) {
            val key = MediaCache.keyForBytes(bytes)
            val hit = cache.lookup(key) ?: run {
                val temp = cache.newTempFile()
                try {
                    temp.writeBytes(bytes)
                } catch (e: Throwable) {
                    temp.delete()
                    throw e
                }
                cache.store(key, temp, ext, FileTypes.mimeOf(ext))
            }
            return fromCache(hit, name, work)
        }
        val target = work.reserve(replaceExtension(name ?: generatedName(ordinal), ext))
        target.writeBytes(bytes)
        return Prepared(target, FileTypes.mimeOf(ext))
    }

    /** The cached file itself, or a copy under [name]. */
    private fun fromCache(hit: MediaCache.Hit, name: String?, work: WorkDir): Prepared {
        if (name == null)
            return Prepared(hit.file, hit.entry.mime)
        val target = work.reserve(replaceExtension(name, hit.entry.ext))
        linkOrCopy(hit.file, target)
        return Prepared(target, hit.entry.mime)
    }

    private fun remoteExtension(url: String, result: Downloader.Result): String {
        val header = FileTypes.readHeader(result.file)
        val hints = listOf(
            FileTypes.extensionOfUrl(url),
            FileTypes.extensionOfUrl(result.url),
            FileTypes.extensionOfName(FileTypes.fileNameOfContentDisposition(result.contentDisposition)),
            FileTypes.extensionOfMime(result.contentType),
        )
        return FileTypes.resolveExtension(FileTypes.sniff(header), hints, header)
    }

    private fun generatedName(ordinal: Int): String =
        "${clock()}_$ordinal"

    private fun compress(bitmap: Bitmap): ByteArray {
        if (bitmap.isRecycled)
            throw PrepareException("Bitmap is recycled")
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw PrepareException("Failed to encode the bitmap")
        return out.toByteArray()
    }

    companion object {

        /** [name] with the extension [ext]: a known extension it has is replaced, so `a.jpg` becomes `a.png`. */
        fun replaceExtension(name: String, ext: String): String {
            val own = FileTypes.extensionOfName(name)
            val base = if (own != null && own != ext.lowercase()) name.dropLast(own.length + 1) else name
            return FileTypes.withExtension(base, ext)
        }

        /** A hard link where the file system allows it (no copy), a copy otherwise. */
        fun linkOrCopy(src: File, dst: File) {
            dst.delete()
            try {
                android.system.Os.link(src.path, dst.path)
            } catch (_: Throwable) {
            }
            if (!dst.isFile || dst.length() != src.length())
                src.copyTo(dst, overwrite = true)
        }

        fun moveTo(src: File, dst: File) {
            dst.delete()
            if (!src.renameTo(dst)) {
                src.copyTo(dst, overwrite = true)
                src.delete()
            }
        }
    }
}
