/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.media

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads URLs into files with [HttpURLConnection]. */
class Downloader(private val userAgent: String = DEFAULT_USER_AGENT) {

    data class Result(
        val file: File,
        /** The URL after redirects. */
        val url: String,
        val contentType: String?,
        val contentDisposition: String?,
    )

    class DownloadException(message: String) : IOException(message)

    /**
     * Downloads [url] into [dest], following redirects (also from http to https, which [HttpURLConnection] doesn't).
     * [timeoutMillis] limits connecting and each wait for data, not the whole download.
     */
    fun download(url: String, dest: File, timeoutMillis: Int, headers: Map<String, String>, maxBytes: Long): Result {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            requireHttp(current)
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = timeoutMillis
                conn.readTimeout = timeoutMillis
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "*/*")
                for ((name, value) in headers)
                    conn.setRequestProperty(name, value)

                val code = conn.responseCode
                if (code in REDIRECT_CODES) {
                    val location = conn.getHeaderField("Location")
                        ?: throw DownloadException("HTTP $code without a Location header: $current")
                    current = URL(current, location)
                    return@repeat
                }
                if (code !in 200..299)
                    throw DownloadException("HTTP $code ${conn.responseMessage.orEmpty()}: $current".trim())

                val length = conn.contentLengthLong
                if (length > maxBytes)
                    throw DownloadException("File is too large (${length} bytes, limit $maxBytes): $current")
                conn.inputStream.use { input ->
                    dest.outputStream().buffered(BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes)
                                throw DownloadException("File is larger than the limit ($maxBytes bytes): $current")
                            output.write(buffer, 0, n)
                        }
                    }
                }
                return Result(dest, current.toString(), conn.contentType, conn.getHeaderField("Content-Disposition"))
            } catch (e: Throwable) {
                dest.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }
        throw DownloadException("Too many redirects: $url")
    }

    private fun requireHttp(url: URL) {
        if (url.protocol != "http" && url.protocol != "https")
            throw DownloadException("Unsupported protocol '${url.protocol}': $url")
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android) StarLight-MediaSharer"
        private const val MAX_REDIRECTS = 5
        private const val BUFFER_SIZE = 16 * 1024
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
