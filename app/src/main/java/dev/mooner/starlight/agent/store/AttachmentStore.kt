package dev.mooner.starlight.agent.store

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.provider.AttachmentSource
import dev.mooner.starlight.plugincore.Session
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Files the user attaches, copied into the app's storage right away: the picker's permission to read a content URI
 * ends with the screen, but a run may need the file much later. Images are scaled down to what models look at.
 */
class AttachmentStore(private val root: File) : AttachmentSource {

    /** Where each attachment is, so that requests, which name only the attachment, don't search every folder. */
    private val locations = ConcurrentHashMap<String, File>()

    fun dir(conversationId: String): File =
        File(root, conversationId).apply { mkdirs() }

    /** Files the agent made for the user in [conversationId], as opposed to ones the user attached. */
    fun outbox(conversationId: String): File =
        File(dir(conversationId), OUTBOX).apply { mkdirs() }

    fun file(attachment: Part.Attachment): File? {
        locations[attachment.id]?.takeIf(File::isFile)?.let { return it }
        val found = root.listFiles().orEmpty().firstNotNullOfOrNull { dir -> File(dir, attachment.id).takeIf(File::isFile) }
            ?: return null
        locations[attachment.id] = found
        return found
    }

    /** The file a card points at: in its project, or in the conversation's outbox. */
    fun cardFile(conversationId: String, card: Card): File? {
        val path = card.path ?: return null
        val base = card.project
            ?.let { name -> Session.projectManager.getProjectByName(name)?.directory }
            ?: outbox(conversationId)
        return File(base, path).takeIf(File::isFile)
    }

    fun file(conversationId: String, attachmentId: String): File? {
        if (!attachmentId.matches(ID_REGEX))
            return null
        return File(File(root, conversationId), attachmentId).takeIf(File::isFile)
    }

    /**
     * Copies the content at [uri] into [conversationId]'s attachments. [typeHint] is its type for when the provider
     * doesn't say, as the clip a keyboard or the clipboard holds it in does.
     */
    fun import(resolver: ContentResolver, uri: Uri, conversationId: String, typeHint: String? = null): Part.Attachment {
        val found = displayName(resolver, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mimeType = resolver.getType(uri)?.takeUnless { it == OCTET_STREAM }
            ?: typeHint
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(found.substringAfterLast('.', "").lowercase())
            ?: OCTET_STREAM
        // What keyboards send is often named without an extension, such as "inputContent".
        val name = if ('.' in found) found
            else MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { "$found.$it" } ?: found
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0)
                    break
                total += read
                if (total > MAX_FILE_BYTES)
                    throw IOException("$name is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw IOException("Couldn't read $name")
        return add(conversationId, name, mimeType, bytes)
    }

    fun add(conversationId: String, name: String, mimeType: String, bytes: ByteArray): Part.Attachment {
        val id = "att_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val kind = kindOf(name, mimeType, bytes)
        val (storedMime, storedBytes) = if (kind == AttachmentKind.IMAGE) shrinkImage(mimeType, bytes) else mimeType to bytes
        val file = File(dir(conversationId), id)
        file.writeBytes(storedBytes)
        locations[id] = file
        return Part.Attachment(
            id = id,
            name = name,
            mimeType = when (kind) {
                AttachmentKind.PDF  -> "application/pdf"
                AttachmentKind.TEXT -> if (storedMime.startsWith("text/")) storedMime else "text/plain"
                else                -> storedMime
            },
            size = storedBytes.size.toLong(),
            kind = kind,
        )
    }

    fun deleteAll(conversationId: String) {
        val dir = File(root, conversationId)
        locations.values.removeAll { it.parentFile == dir }
        dir.deleteRecursively()
    }

    /** Deletes one attachment of [conversationId], with the pages rendered from it. */
    fun delete(conversationId: String, attachmentId: String) {
        val file = file(conversationId, attachmentId) ?: return
        locations.remove(attachmentId)
        pagesDir(file).deleteRecursively()
        file.delete()
    }

    override fun bytes(attachment: Part.Attachment): ByteArray =
        file(attachment)?.readBytes() ?: throw IOException("The attachment ${attachment.name} is missing")

    override fun pdfPages(attachment: Part.Attachment): List<ByteArray> {
        val file = file(attachment) ?: throw IOException("The attachment ${attachment.name} is missing")
        // Every request sends the whole history, so the pages are rendered once and kept beside the PDF.
        val cache = pagesDir(file)
        readPages(cache)?.let { return it }
        val pages = renderPages(file)
        runCatching { writePages(cache, pages) }
        return pages
    }

    private fun pagesDir(file: File): File =
        File(file.parentFile, file.name + PAGES_SUFFIX)

    private fun readPages(dir: File): List<ByteArray>? {
        val count = File(dir, PAGE_COUNT_FILE).takeIf(File::isFile)?.readText()?.toIntOrNull() ?: return null
        return (0 until count).map { index -> File(dir, "$index.jpg").takeIf(File::isFile)?.readBytes() ?: return null }
    }

    private fun writePages(dir: File, pages: List<ByteArray>) {
        dir.deleteRecursively()
        dir.mkdirs()
        pages.forEachIndexed { index, bytes -> File(dir, "$index.jpg").writeBytes(bytes) }
        // Written last, so that a half-written folder reads as missing.
        File(dir, PAGE_COUNT_FILE).writeText(pages.size.toString())
    }

    private fun renderPages(file: File): List<ByteArray> {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = MAX_IMAGE_EDGE.toFloat() / maxOf(page.width, page.height)
                        val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
                            .also { bitmap.recycle() }
                    }
                }
            }
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun kindOf(name: String, mimeType: String, bytes: ByteArray): AttachmentKind {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mimeType in IMAGE_TYPES                        -> AttachmentKind.IMAGE
            mimeType == "application/pdf" || extension == "pdf" -> AttachmentKind.PDF
            mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS || mimeType in TEXT_TYPES -> AttachmentKind.TEXT
            bytes.size <= MAX_TEXT_SNIFF && bytes.none { it == 0.toByte() } && isUtf8(bytes) -> AttachmentKind.TEXT
            else                                           -> AttachmentKind.FILE
        }
    }

    private fun isUtf8(bytes: ByteArray): Boolean =
        runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)) }.isSuccess

    internal companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
        /** The type of content of no known type; a provider saying it tells nothing that the clip or name couldn't. */
        const val OCTET_STREAM = "application/octet-stream"
        const val MAX_IMAGE_EDGE = 1568
        const val SMALL_IMAGE_BYTES = 500 * 1024
        const val JPEG_QUALITY = 85
        const val MAX_PDF_PAGES = 20
        const val MAX_TEXT_SNIFF = 1024 * 1024
        val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        val TEXT_TYPES = setOf("application/json", "application/javascript", "application/xml", "application/x-javascript")
        val TEXT_EXTENSIONS = setOf("js", "mjs", "ts", "json", "txt", "md", "csv", "xml", "html", "css", "kt", "java", "py", "log", "yml", "yaml")
        val ID_REGEX = Regex("^att_[a-f0-9]+$")
        const val OUTBOX = "out"
        const val PAGES_SUFFIX = ".pages"
        const val PAGE_COUNT_FILE = "count"

        /** Fits an image within [MAX_IMAGE_EDGE] pixels; JPEG unless it has transparency. GIFs are kept as they are. */
        fun shrinkImage(mimeType: String, bytes: ByteArray): Pair<String, ByteArray> {
            if (mimeType == "image/gif")
                return mimeType to bytes
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0)
                return mimeType to bytes
            if (longEdge <= MAX_IMAGE_EDGE && bytes.size <= SMALL_IMAGE_BYTES)
                return mimeType to bytes
            var sample = 1
            while (longEdge / (sample * 2) >= MAX_IMAGE_EDGE)
                sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return mimeType to bytes
            val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
            val scaled = if (scale < 1f)
                decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
            else
                decoded
            val png = scaled.hasAlpha()
            val out = ByteArrayOutputStream()
            scaled.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (scaled !== decoded)
                decoded.recycle()
            scaled.recycle()
            return (if (png) "image/png" else "image/jpeg") to out.toByteArray()
        }
    }
}
