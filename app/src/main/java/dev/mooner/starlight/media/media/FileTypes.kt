/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.media

import java.io.File
import java.net.URI
import java.net.URLDecoder

/** Extensions, MIME types and file signatures. */
object FileTypes {

    const val OCTET_STREAM = "application/octet-stream"
    const val FALLBACK_EXT = "bin"

    /** Bytes read from the start of a file to recognize its type. */
    const val HEADER_BYTES = 512

    /** Known extensions; only these are taken from names and URLs. */
    val MIME_BY_EXT: Map<String, String> = linkedMapOf(
        // Images
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif",
        "avif" to "image/avif",
        "tif" to "image/tiff", "tiff" to "image/tiff",
        "tga" to "image/x-tga",
        "psd" to "image/vnd.adobe.photoshop",
        "ai" to "application/postscript",
        // Videos
        "mp4" to "video/mp4", "m4v" to "video/mp4",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "wmv" to "video/x-ms-wmv", "asf" to "video/x-ms-asf",
        "ts" to "video/mp2t",
        "mpg" to "video/mpeg", "mpeg" to "video/mpeg",
        "flv" to "video/x-flv",
        "ogv" to "video/ogg",
        // Audio
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg", "opus" to "audio/opus",
        "wma" to "audio/x-ms-wma",
        "tta" to "audio/x-tta", "tak" to "audio/x-tak",
        // Documents
        "txt" to "text/plain", "log" to "text/plain",
        "md" to "text/markdown",
        "csv" to "text/csv", "tsv" to "text/tab-separated-values",
        "html" to "text/html", "htm" to "text/html",
        "json" to "application/json",
        "xml" to "application/xml",
        "rtf" to "application/rtf",
        "pdf" to "application/pdf",
        "hwp" to "application/x-hwp",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text", "odf" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "wks" to "application/vnd.ms-works",
        "xps" to "application/vnd.ms-xpsdocument",
        "pages" to "application/x-iwork-pages-sffpages",
        "key" to "application/x-iwork-keynote-sffkey",
        "numbers" to "application/x-iwork-numbers-sffnumbers",
        "show" to OCTET_STREAM, "ce" to OCTET_STREAM,
        // Archives
        "zip" to "application/zip",
        "gz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "rar" to "application/x-rar-compressed",
        "7z" to "application/x-7z-compressed",
        "lzh" to "application/x-lzh",
        "alz" to "application/x-alz-compressed",
    )

    private val EXT_BY_MIME: Map<String, String> =
        buildMap {
            for ((ext, mime) in MIME_BY_EXT)
                if (mime != OCTET_STREAM)
                    putIfAbsent(mime, ext)
            // Types servers send that differ from the ones above.
            put("image/jpg", "jpg")
            put("image/pjpeg", "jpg")
            put("image/x-png", "png")
            put("image/x-ms-bmp", "bmp")
            put("audio/mp3", "mp3")
            put("audio/x-wav", "wav")
            put("audio/wave", "wav")
            put("audio/x-m4a", "m4a")
            put("audio/x-flac", "flac")
            put("video/x-m4v", "m4v")
            put("text/xml", "xml")
            put("application/x-zip-compressed", "zip")
            put("application/x-gzip", "gz")
            put("application/vnd.rar", "rar")
            put("application/haansofthwp", "hwp")
            put("application/vnd.hancom.hwp", "hwp")
        }

    /** Query parameters whose value often names the format, as in `?format=jpg`. */
    private val FORMAT_PARAMS = setOf("format", "fm", "ext", "type", "filetype")

    /**
     * A file signature. [exts] lists the types sharing it, most likely first; [secondary] bytes must also match at
     * [secondaryOffset].
     */
    class Signature(
        val exts: List<String>,
        val bytes: IntArray,
        val offset: Int = 0,
        val secondary: IntArray? = null,
        val secondaryOffset: Int = 0,
    )

    private fun sig(vararg exts: String, offset: Int = 0, secondary: IntArray? = null, secondaryOffset: Int = 0, bytes: () -> IntArray) =
        Signature(exts.toList(), bytes(), offset, secondary, secondaryOffset)

    private fun ascii(text: String): IntArray =
        IntArray(text.length) { text[it].code }

    val SIGNATURES: List<Signature> = listOf(
        sig("jpg", "jpeg") { intArrayOf(0xFF, 0xD8, 0xFF) },
        sig("png") { intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) },
        sig("gif") { ascii("GIF8") },
        sig("webp", secondary = ascii("WEBP"), secondaryOffset = 8) { ascii("RIFF") },
        sig("avi", secondary = ascii("AVI "), secondaryOffset = 8) { ascii("RIFF") },
        sig("wav", secondary = ascii("WAVE"), secondaryOffset = 8) { ascii("RIFF") },
        // "BM" alone is too common at the start of text; the reserved header field is zero.
        sig("bmp", secondary = intArrayOf(0, 0, 0, 0), secondaryOffset = 6) { ascii("BM") },
        sig("tif", "tiff") { intArrayOf(0x49, 0x49, 0x2A, 0x00) },
        sig("tif", "tiff") { intArrayOf(0x4D, 0x4D, 0x00, 0x2A) },
        sig("psd") { ascii("8BPS") },
        sig("pdf") { ascii("%PDF") },
        sig("ai") { ascii("%!") },
        sig("hwp") { ascii("HWP Document File") },
        sig("doc", "xls", "ppt", "hwp", "wks") { intArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) },
        sig("rtf") { ascii("{\\rtf1") },
        sig("zip", "docx", "xlsx", "pptx", "odt", "odf", "ods", "odp", "pages", "key", "numbers", "xps") { intArrayOf(0x50, 0x4B, 0x03, 0x04) },
        sig("rar") { intArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) },
        sig("7z") { intArrayOf(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) },
        sig("gz") { intArrayOf(0x1F, 0x8B) },
        sig("bz2") { ascii("BZh") },
        sig("alz") { intArrayOf(0x41, 0x4C, 0x5A, 0x01) },
        sig("lzh", offset = 2) { ascii("-lh") },
        sig("mkv", "webm") { intArrayOf(0x1A, 0x45, 0xDF, 0xA3) },
        sig("flv") { intArrayOf(0x46, 0x4C, 0x56, 0x01) },
        sig("wmv", "asf", "wma") { intArrayOf(0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11, 0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C) },
        sig("mpg", "mpeg") { intArrayOf(0x00, 0x00, 0x01, 0xBA) },
        // A transport stream packet is 188 bytes long and starts with 0x47 ('G', which also starts plenty of text).
        sig("ts", secondary = intArrayOf(0x47), secondaryOffset = 188) { intArrayOf(0x47) },
        sig("ogg", "ogv", "opus") { ascii("OggS") },
        sig("flac") { ascii("fLaC") },
        sig("mp3") { ascii("ID3") },
        sig("mp3") { intArrayOf(0xFF, 0xFB) },
        sig("mp3") { intArrayOf(0xFF, 0xFA) },
        sig("mp3") { intArrayOf(0xFF, 0xF3) },
        sig("mp3") { intArrayOf(0xFF, 0xF2) },
        sig("aac") { intArrayOf(0xFF, 0xF1) },
        sig("aac") { intArrayOf(0xFF, 0xF9) },
    )

    // Most files are told apart by their first byte; the rest are checked after.
    private val BY_FIRST_BYTE: Map<Int, List<Signature>> =
        SIGNATURES.filter { it.offset == 0 }.groupBy { it.bytes[0] }
    private val WITH_OFFSET: List<Signature> =
        SIGNATURES.filter { it.offset != 0 }

    private val FTYP = ascii("ftyp")
    private val AVIF_BRANDS = setOf("avif", "avis")
    private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")

    /** A recognized type: [exts] share the signature, the first being the most likely. */
    data class Match(val exts: List<String>) {
        val ext: String get() = exts.first()
    }

    /** Recognizes a file from its first bytes. */
    fun sniff(header: ByteArray, length: Int = header.size): Match? {
        val length = minOf(length, header.size)
        if (length <= 0)
            return null
        isoMediaType(header, length)?.let { return it }
        BY_FIRST_BYTE[header[0].toInt() and 0xFF]
            ?.firstOrNull { matches(header, length, it) }
            ?.let { return Match(it.exts) }
        return WITH_OFFSET.firstOrNull { matches(header, length, it) }?.let { Match(it.exts) }
    }

    fun sniff(file: File): Match? {
        val header = readHeader(file)
        return sniff(header, header.size)
    }

    fun readHeader(file: File): ByteArray =
        file.inputStream().use { input ->
            val buffer = ByteArray(HEADER_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            buffer.copyOf(read)
        }

    /**
     * Whether the start of a file reads as text: no NUL or other control bytes than whitespace. Text in any charset
     * passes, since Korean text files are often not UTF-8.
     */
    fun looksLikeText(header: ByteArray, length: Int = header.size): Boolean {
        val length = minOf(length, header.size)
        if (length == 0)
            return false
        for (i in 0 until length) {
            val c = header[i].toInt() and 0xFF
            if (c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0C && c != 0x0D && c != 0x1B)
                return false
            if (c == 0x7F)
                return false
        }
        return true
    }

    /**
     * Picks an extension: the recognized type, narrowed by [hints] (from names, URLs or MIME types) when they agree
     * with it; the first hint when the data has no known signature; text or [FALLBACK_EXT] otherwise.
     */
    fun resolveExtension(match: Match?, hints: List<String?>, header: ByteArray? = null): String {
        val known = hints.filterNotNull().map { it.lowercase() }.filter { it in MIME_BY_EXT }
        if (match != null)
            return known.firstOrNull { it in match.exts } ?: match.ext
        known.firstOrNull()?.let { return it }
        if (header != null && looksLikeText(header))
            return "txt"
        return FALLBACK_EXT
    }

    fun mimeOf(ext: String): String =
        MIME_BY_EXT[ext.lowercase()] ?: OCTET_STREAM

    fun extensionOfMime(mime: String?): String? =
        mime?.substringBefore(';')?.trim()?.lowercase()?.let(EXT_BY_MIME::get)

    /** The extension of a file name, if it is a known one. */
    fun extensionOfName(name: String?): String? {
        if (name.isNullOrEmpty())
            return null
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        if (dot < 0 || dot == base.length - 1)
            return null
        return base.substring(dot + 1).lowercase().takeIf { it in MIME_BY_EXT }
    }

    /** A known extension named by a URL's path, a `name.ext` query value or a `format=ext` query parameter. */
    fun extensionOfUrl(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull()
        val path = uri?.rawPath ?: url.substringBefore('?').substringBefore('#')
        extensionOfName(decode(path))?.let { return it }
        val query = uri?.rawQuery ?: url.substringAfter('?', "").substringBefore('#')
        for (param in query.split('&')) {
            val key = decode(param.substringBefore('=')).lowercase()
            val value = decode(param.substringAfter('=', ""))
            if (key in FORMAT_PARAMS)
                value.lowercase().takeIf { it in MIME_BY_EXT }?.let { return it }
            extensionOfName(value)?.let { return it }
        }
        return null
    }

    /** The last segment of a URL's path, if it names a file with a known extension. */
    fun fileNameOfUrl(url: String): String? {
        val path = runCatching { URI(url).rawPath }.getOrNull() ?: url.substringBefore('?').substringBefore('#')
        val name = decode(path.substringAfterLast('/'))
        return name.takeIf { extensionOfName(it) != null }?.let(::sanitizeFileName)
    }

    /** The file name of a `Content-Disposition` header. */
    fun fileNameOfContentDisposition(header: String?): String? {
        if (header.isNullOrBlank())
            return null
        val extended = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)
        if (extended != null) {
            val charset = extended.groupValues[1].ifBlank { "UTF-8" }
            val name = runCatching { URLDecoder.decode(extended.groupValues[2].trim().replace("+", "%2B"), charset) }.getOrNull()
            if (!name.isNullOrBlank())
                return sanitizeFileName(name)
        }
        val plain = Regex("""filename\s*=\s*("([^"]*)"|[^;]+)""", RegexOption.IGNORE_CASE).find(header)
            ?: return null
        val name = plain.groupValues[2].ifEmpty { plain.groupValues[1] }.trim()
        return name.takeIf { it.isNotEmpty() }?.let(::sanitizeFileName)
    }

    /** Whether [name] can be used as is: no path separators or characters Android and Windows reject. */
    fun isValidFileName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it in INVALID_CHARS || it.code < 0x20 } &&
            name.toByteArray().size <= MAX_NAME_BYTES

    /** [name] with rejected characters replaced and shortened to fit, keeping its extension. */
    fun sanitizeFileName(name: String): String {
        var clean = name.map { if (it in INVALID_CHARS || it.code < 0x20) '_' else it }.joinToString("").trim().trim('.')
        if (clean.isEmpty())
            clean = "file"
        if (clean.toByteArray().size <= MAX_NAME_BYTES)
            return clean
        val ext = clean.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 10 }
        val base = if (ext != null) clean.dropLast(ext.length + 1) else clean
        var cut = base
        while (cut.isNotEmpty() && (cut + (ext?.let { ".$it" } ?: "")).toByteArray().size > MAX_NAME_BYTES)
            cut = cut.dropLast(1)
        return cut + (ext?.let { ".$it" } ?: "")
    }

    /** [name] ending in `.ext`, unless it already does. */
    fun withExtension(name: String, ext: String): String =
        if (name.lowercase().endsWith(".${ext.lowercase()}")) name else "$name.$ext"

    private fun matches(bytes: ByteArray, length: Int, sig: Signature): Boolean {
        if (!matchesAt(bytes, length, sig.offset, sig.bytes))
            return false
        val secondary = sig.secondary ?: return true
        return matchesAt(bytes, length, sig.secondaryOffset, secondary)
    }

    private fun matchesAt(bytes: ByteArray, length: Int, offset: Int, expected: IntArray): Boolean {
        if (length < offset + expected.size)
            return false
        for (i in expected.indices)
            if ((bytes[offset + i].toInt() and 0xFF) != expected[i])
                return false
        return true
    }

    /** MP4-family files (`ftyp` box): told apart by their major and compatible brands. */
    private fun isoMediaType(bytes: ByteArray, length: Int): Match? {
        if (!matchesAt(bytes, length, 4, FTYP) || length < 12)
            return null
        val boxSize = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val end = boxSize.coerceIn(12, length)
        val major = brand(bytes, 8)
        val brands = buildList {
            add(major)
            var i = 16
            while (i + 4 <= end) {
                add(brand(bytes, i))
                i += 4
            }
        }
        return when {
            brands.any { it in AVIF_BRANDS } -> Match(listOf("avif"))
            brands.any { it in HEIC_BRANDS } -> Match(listOf("heic", "heif"))
            major == "qt  "                  -> Match(listOf("mov", "mp4"))
            major == "M4A " || major == "M4B " -> Match(listOf("m4a", "mp4"))
            major == "M4V "                  -> Match(listOf("m4v", "mp4"))
            else                             -> Match(listOf("mp4", "m4v", "m4a", "mov"))
        }
    }

    private fun brand(bytes: ByteArray, offset: Int): String =
        String(CharArray(4) { (bytes[offset + it].toInt() and 0xFF).toChar() })

    private fun decode(text: String): String =
        runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    private const val INVALID_CHARS = "\\/:*?\"<>|"
    private const val MAX_NAME_BYTES = 200
}
