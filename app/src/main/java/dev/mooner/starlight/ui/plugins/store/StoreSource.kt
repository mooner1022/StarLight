package dev.mooner.starlight.ui.plugins.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
        RAW_BASE + path.removePrefix("/")

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
