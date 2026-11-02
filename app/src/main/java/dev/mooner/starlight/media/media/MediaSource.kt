/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.media

import android.graphics.Bitmap
import dev.mooner.starlight.media.script.ScriptValues
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

/** What a script asked to send, read off the script thread's values. */
sealed interface MediaSource {

    /** Identifies the source for de-duplication; null if every occurrence counts as different. */
    val key: String?

    /** For logs: a URL or path, never the data itself. */
    val label: String

    data class Remote(val url: String) : MediaSource {
        override val key: String get() = "url:$url"
        override val label: String get() = url
    }

    data class Local(val file: File) : MediaSource {
        override val key: String get() = "file:${file.path}"
        override val label: String get() = file.path
    }

    /** Data given directly: a byte array, a data URL or Base64 text. [mimeHint] comes from a data URL. */
    class Bytes(val bytes: ByteArray, val mimeHint: String? = null, override val label: String = "<${bytes.size} bytes>") : MediaSource {
        override val key: String? get() = null
    }

    /** A bitmap, such as `msg.image`; compressed to PNG when prepared. */
    class Image(val bitmap: Bitmap) : MediaSource {
        override val key: String? get() = null
        override val label: String get() = "<bitmap ${bitmap.width}x${bitmap.height}>"
    }
}

class InvalidSourceException(message: String) : IllegalArgumentException(message)

object MediaSources {

    private val BASE64_PREFIX = Regex("^base64[:,]", RegexOption.IGNORE_CASE)
    private val BASE64_CHARS = Regex("^[A-Za-z0-9+/\\-_]+={0,2}$")
    private val WHITESPACE = Regex("\\s+")
    private val SDCARD_PREFIX = Regex("^/?(sdcard|storage/emulated/\\d+)/")

    /** Paths can't be longer than this, so longer text is never checked against the file system. */
    private const val MAX_PATH_LENGTH = 4096
    /** Shorter text is too ambiguous to be taken for Base64 without a `base64:` prefix. */
    private const val MIN_RAW_BASE64_LENGTH = 64

    /**
     * Reads one source or an array of them. Relative paths are resolved against [baseDir] (the project directory).
     * @throws InvalidSourceException if a value can't be sent.
     */
    fun parseAll(value: Any?, baseDir: File?): List<MediaSource> {
        if (ScriptValues.asBytes(value) == null) {
            val list = ScriptValues.asList(value)
            if (list != null) {
                if (list.isEmpty())
                    throw InvalidSourceException("No files to send: the array is empty")
                return list.mapIndexed { i, element ->
                    try {
                        parse(element, baseDir)
                    } catch (e: InvalidSourceException) {
                        throw InvalidSourceException("Item ${i + 1}: ${e.message}")
                    }
                }
            }
        }
        return listOf(parse(value, baseDir))
    }

    fun parse(value: Any?, baseDir: File?): MediaSource {
        if (ScriptValues.isAbsent(value))
            throw InvalidSourceException("No file given (null or undefined)")
        ScriptValues.asBytes(value)?.let { return MediaSource.Bytes(it) }
        return when (val value = ScriptValues.unwrap(value)) {
            is Bitmap       -> MediaSource.Image(value)
            is File         -> MediaSource.Local(value)
            is URL          -> parseText(value.toString(), baseDir)
            is URI          -> parseText(value.toString(), baseDir)
            is CharSequence -> parseText(value.toString(), baseDir)
            else            -> {
                // android.net.Uri and similar: parsed by their text.
                val type = value?.javaClass?.name
                if (type == "android.net.Uri" || type?.startsWith("android.net.Uri$") == true)
                    parseText(value.toString(), baseDir)
                else
                    throw InvalidSourceException("Unsupported value: ${type ?: "null"}")
            }
        }
    }

    fun parseText(text: String, baseDir: File?): MediaSource {
        val text = text.trim()
        if (text.isEmpty())
            throw InvalidSourceException("Empty path")
        val lower = text.lowercase()
        when {
            lower.startsWith("data:")                    -> return parseDataUrl(text)
            BASE64_PREFIX.containsMatchIn(text)          -> return MediaSource.Bytes(decodeBase64(text.substring(7)), label = "<base64>")
            lower.startsWith("http://") || lower.startsWith("https://") -> return MediaSource.Remote(text)
            lower.startsWith("file:")                    -> return MediaSource.Local(
                runCatching { File(URI(text)) }.getOrElse { throw InvalidSourceException("Malformed file URI: $text") }
            )
        }
        localFile(text, baseDir)?.let { return MediaSource.Local(it) }
        if (isLikelyBase64(text))
            return MediaSource.Bytes(decodeBase64(text), label = "<base64>")
        // Not found: reported as a missing file when prepared.
        return MediaSource.Local(resolve(text, baseDir))
    }

    /** An existing file named by [text], or a path on shared storage. */
    private fun localFile(text: String, baseDir: File?): File? {
        if (text.length > MAX_PATH_LENGTH)
            return null
        if (SDCARD_PREFIX.containsMatchIn(text))
            return File(if (text.startsWith("/")) text else "/$text")
        if (baseDir == null && !File(text).isAbsolute)
            return null
        return resolve(text, baseDir).takeIf { it.exists() }
    }

    private fun resolve(path: String, baseDir: File?): File {
        val file = File(path)
        if (file.isAbsolute)
            return file
        // room.sendMedia doesn't know the project; MediaSharer.send does.
        if (baseDir == null)
            throw InvalidSourceException("A relative path needs the project: use MediaSharer.send() or an absolute path: $path")
        return baseDir.resolve(path)
    }

    private fun parseDataUrl(text: String): MediaSource {
        val comma = text.indexOf(',')
        if (comma < 0)
            throw InvalidSourceException("Malformed data URL: no ',' after the header")
        val header = text.substring(5, comma)
        val params = header.split(';').map(String::trim)
        val mime = params.firstOrNull()?.takeIf { it.contains('/') }?.lowercase()
        val payload = text.substring(comma + 1)
        val bytes = if (params.any { it.equals("base64", ignoreCase = true) })
            decodeBase64(payload)
        else
            URLDecoder.decode(payload.replace("+", "%2B"), "UTF-8").toByteArray()
        return MediaSource.Bytes(bytes, mime, label = "<data URL${mime?.let { " $it" }.orEmpty()}>")
    }

    /** Decodes standard or URL-safe Base64, with or without padding and line breaks. */
    fun decodeBase64(text: String): ByteArray {
        var clean = text.replace(WHITESPACE, "").replace('-', '+').replace('_', '/')
        clean = clean.trimEnd('=')
        if (clean.length % 4 == 1)
            throw InvalidSourceException("Malformed Base64: wrong length")
        clean += "=".repeat((4 - clean.length % 4) % 4)
        return try {
            Base64.getDecoder().decode(clean)
        } catch (e: IllegalArgumentException) {
            throw InvalidSourceException("Malformed Base64: ${e.message}")
        }
    }

    private fun isLikelyBase64(text: String): Boolean {
        if (text.length < MIN_RAW_BASE64_LENGTH)
            return false
        val clean = text.replace(WHITESPACE, "")
        return BASE64_CHARS.matches(clean) && clean.trimEnd('=').length % 4 != 1
    }
}
