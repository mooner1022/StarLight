package dev.mooner.starlight.plugincore.widget

import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

typealias WidgetSettingsBlock = WidgetSettingsBuilder.() -> Unit

internal sealed interface WidgetSettingItem {
    val id: String
    val title: String
    val description: String?
    val default: JsonPrimitive
}

internal class ToggleSettingItem(
    override val id: String,
    override val title: String,
    override val description: String?,
    default: Boolean,
) : WidgetSettingItem {
    override val default: JsonPrimitive = JsonPrimitive(default)
}

internal class ChoiceSettingItem(
    override val id: String,
    override val title: String,
    override val description: String?,
    val options: List<Pair<String, String>>,
    default: String,
) : WidgetSettingItem {
    override val default: JsonPrimitive = JsonPrimitive(default)
}

@WidgetDsl
class WidgetSettingsBuilder internal constructor() {

    internal val items: MutableList<WidgetSettingItem> = arrayListOf()

    fun toggle(id: String, title: String, description: String? = null, default: Boolean = false) {
        items += ToggleSettingItem(id, title, description, default)
    }

    fun choice(
        id: String,
        title: String,
        options: List<Pair<String, String>>,
        default: String = options.first().first,
        description: String? = null,
    ) {
        require(options.isNotEmpty()) { "Setting '$id' must have at least one option" }
        items += ChoiceSettingItem(id, title, description, options, default)
    }
}

class WidgetSettings internal constructor(
    private val values: Map<String, JsonElement>,
    private val defaults: Map<String, JsonPrimitive>,
) {

    fun boolean(id: String): Boolean =
        primitive(id)?.booleanOrNull ?: false

    fun string(id: String): String? =
        primitive(id)?.contentOrNull

    private fun primitive(id: String): JsonPrimitive? =
        values[id] as? JsonPrimitive ?: defaults[id]

    companion object {
        internal val EMPTY = WidgetSettings(emptyMap(), emptyMap())
    }
}

class WidgetSettingsForm internal constructor(
    private val items: List<WidgetSettingItem>,
    private val values: Map<String, JsonElement>,
) {

    val structure: ConfigStructure by lazy {
        config {
            category {
                id = CATEGORY_ID
                items {
                    for (item in this@WidgetSettingsForm.items) {
                        when (item) {
                            is ToggleSettingItem -> toggle {
                                id = item.id
                                title = item.title
                                description = item.description
                                defaultValue = item.default.booleanOrNull ?: false
                            }
                            is ChoiceSettingItem -> spinner {
                                id = item.id
                                title = item.title
                                description = item.description
                                items = item.options.map { (_, label) -> label }
                                defaultIndex = item.indexOf(item.default.content)
                            }
                        }
                    }
                }
            }
        }
    }

    val data: MutableDataMap
        get() {
            val entries: MutableMap<String, JsonElement> = hashMapOf()
            for (item in items) {
                val value = values[item.id] as? JsonPrimitive ?: continue
                entries[item.id] = when (item) {
                    is ToggleSettingItem -> value
                    is ChoiceSettingItem -> JsonPrimitive(item.indexOf(value.content))
                }
            }
            return hashMapOf(CATEGORY_ID to entries)
        }

    fun toValue(id: String, formValue: JsonElement): JsonElement? =
        when (val item = items.find { it.id == id }) {
            null -> null
            is ToggleSettingItem -> formValue
            is ChoiceSettingItem -> (formValue as? JsonPrimitive)?.intOrNull
                ?.let(item.options::getOrNull)
                ?.let { (value, _) -> JsonPrimitive(value) }
        }

    private fun ChoiceSettingItem.indexOf(value: String): Int =
        options.indexOfFirst { (option, _) -> option == value }.coerceAtLeast(0)

    companion object {
        const val CATEGORY_ID = "widget"
    }
}

internal fun WidgetSettingsBlock.evaluate(): List<WidgetSettingItem> =
    WidgetSettingsBuilder().apply(this).items

internal fun List<WidgetSettingItem>.toSettings(values: Map<String, JsonElement>): WidgetSettings =
    WidgetSettings(values, associate { it.id to it.default })
