package dev.mooner.starlight.plugincore.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID

@Serializable
data class WidgetPlacement(
    val instanceId: String,
    val widgetId: String,
    val span: WidgetSpan? = null,
    val settings: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class WidgetLayout(
    val version: Int = VERSION,
    val items: List<WidgetPlacement> = emptyList(),
) {

    fun encode(json: Json): String =
        json.encodeToString(this)

    fun encodeLegacyIds(json: Json): String =
        json.encodeToString(items.map(WidgetPlacement::widgetId))

    companion object {

        const val VERSION = 2

        fun newInstanceId(): String =
            UUID.randomUUID().toString()

        fun decode(
            layout: String?,
            legacyIds: String?,
            json: Json,
            newInstanceId: () -> String = ::newInstanceId,
        ): DecodeResult {
            layout
                ?.let { runCatching { json.decodeFromString<WidgetLayout>(it) }.getOrNull() }
                ?.let { return DecodeResult(it.normalized(newInstanceId), isMigrated = false) }

            val ids = legacyIds
                ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
                ?: emptyList()
            val items = ids.map { id -> WidgetPlacement(newInstanceId(), id) }
            return DecodeResult(WidgetLayout(items = items), isMigrated = true)
        }

        private fun WidgetLayout.normalized(newInstanceId: () -> String): WidgetLayout {
            val seen: MutableSet<String> = hashSetOf()
            val items = items.map { item ->
                if (item.instanceId.isBlank() || !seen.add(item.instanceId))
                    item.copy(instanceId = newInstanceId()).also { seen += it.instanceId }
                else
                    item
            }
            return copy(version = VERSION, items = items)
        }
    }

    data class DecodeResult(
        val layout: WidgetLayout,
        val isMigrated: Boolean,
    )
}
