package dev.mooner.starlight.ui.plugins.store

import dev.mooner.starlight.plugincore.plugin.PluginDependency
import dev.mooner.starlight.plugincore.version.Version
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.Locale

@Serializable(with = LocalizedTextSerializer::class)
class LocalizedText(private val values: Map<String, String>) {

    fun get(): String =
        values[Locale.getDefault().language] ?: values[""] ?: values["en"] ?: values.values.firstOrNull().orEmpty()

    override fun toString(): String = get()
}

object LocalizedTextSerializer : KSerializer<LocalizedText> {

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): LocalizedText =
        when (val element = (decoder as JsonDecoder).decodeJsonElement()) {
            is JsonObject -> LocalizedText(element.mapValues { it.value.jsonPrimitive.content })
            else          -> LocalizedText(mapOf("" to element.jsonPrimitive.content))
        }

    override fun serialize(encoder: Encoder, value: LocalizedText) =
        JsonElement.serializer().serialize(encoder, JsonPrimitive(value.get()))
}

@Serializable
data class StoreCategory(
    val id: String,
    val name: LocalizedText,
)

@Serializable
data class StoreFeature(
    @SerialName("plugin")
    val pluginId: String,
    val headline: LocalizedText,
)

@Serializable
data class StoreAuthor(
    val name: String,
    val url: String? = null,
    val verified: Boolean = false,
)

@Serializable
data class StoreScreenshot(
    val image: String,
    val caption: String = "",
)

@Serializable
data class StoreRelease(
    val version: Version,
    val apiVersion: Version,
    val releasedAt: String,
    val changelog: String = "",
    val url: String,
    val size: Long,
    val sha256: String,
    val dependency: List<PluginDependency> = emptyList(),
    val nativeLibrary: Boolean = false,
) {
    val releasedAtMillis: Long
        get() = parseInstant(releasedAt)

    val fileName: String
        get() = url.substringAfterLast('/')
}

@Serializable
data class StorePlugin(
    val id: String,
    val name: String,
    val summary: String,
    val description: String = "",
    val authors: List<StoreAuthor>,
    val category: String,
    val tags: List<String> = emptyList(),
    val icon: String? = null,
    val screenshots: List<StoreScreenshot> = emptyList(),
    val license: String? = null,
    val source: String? = null,
    val publishedAt: String,
    val releases: List<StoreRelease>,
) {
    val latest: StoreRelease
        get() = releases.first()

    val isVerified: Boolean
        get() = authors.any(StoreAuthor::verified)

    val publishedAtMillis: Long
        get() = parseInstant(publishedAt)
}

@Serializable
data class StoreIndex(
    val schemaVersion: Int,
    val name: String,
    val description: String? = null,
    val generatedAt: String,
    val categories: List<StoreCategory> = emptyList(),
    val featured: List<StoreFeature> = emptyList(),
    val plugins: List<StorePlugin> = emptyList(),
) {
    val generatedAtMillis: Long
        get() = parseInstant(generatedAt)

    fun find(id: String): StorePlugin? =
        plugins.find { it.id == id }
}

private fun parseInstant(value: String): Long =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
