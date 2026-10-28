package dev.mooner.starlight.ui.plugins.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object StoreSource {

    const val REPOSITORY_URL = "https://github.com/mooner1022/starlight-plugins"
    const val BRANCH = "main"

    private const val RAW_BASE = "https://raw.githubusercontent.com/mooner1022/starlight-plugins/$BRANCH/"
    private const val SUPPORTED_SCHEMA = 1
    private const val TIMEOUT_MILLIS = 10_000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun resolve(path: String): String =
        if (path.startsWith("https://") || path.startsWith("http://")) path
        else RAW_BASE + path.removePrefix("/")

    suspend fun download(url: String, dest: File, expectedSize: Long, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK)
                    throw IOException("HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
                var read = 0L
                var reported = -1
                connection.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0)
                                break
                            output.write(buffer, 0, count)
                            read += count
                            val progress = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else 0
                            if (progress != reported) {
                                reported = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
                if (expectedSize > 0 && read != expectedSize)
                    throw IOException("Expected $expectedSize bytes, got $read")
            } finally {
                connection.disconnect()
            }
        }

    suspend fun fetchIndex(): StoreIndex =
        withContext(Dispatchers.IO) {
            val connection = URL(resolve("index.json")).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK)
                    throw IOException("HTTP $code")
                val index = json.decodeFromString<StoreIndex>(connection.inputStream.bufferedReader().use { it.readText() })
                if (index.schemaVersion > SUPPORTED_SCHEMA)
                    throw IOException("Unsupported index schema ${index.schemaVersion}")
                index
            } finally {
                connection.disconnect()
            }
        }
}
