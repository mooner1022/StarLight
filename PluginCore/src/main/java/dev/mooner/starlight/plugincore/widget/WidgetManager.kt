package dev.mooner.starlight.plugincore.widget

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.PluginContext
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.errorTranslated

private val logger = LoggerFactory.logger {  }

class WidgetManager {

    private val definitions: MutableMap<String, WidgetDefinition> = linkedMapOf()
    private val widgetInfo: MutableMap<String, WidgetInfo> = linkedMapOf()

    fun addWidget(context: PluginContext, widget: Class<out Widget>) {
        logger.verbose { "Adding widget ${widget.name} from plugin ${context.name}" }
        addWidget(context, LegacyWidgetDefinition(widget))
    }

    fun addWidget(context: PluginContext, definition: WidgetDefinition) {
        val id = definition.id

        if (id in definitions) {
            logger.errorTranslated {
                Locale.ENGLISH { "Rejecting registration of duplicated widget id: $id" }
                Locale.KOREAN  { "중복된 위젯 id를 가진 위젯의 등록이 거부됨: $id" }
            }
            return
        }

        definitions[id] = definition
        widgetInfo[id] = WidgetInfo(
            pluginName = context.name,
            id = id,
            name = definition.name
        )
    }

    fun getDefinition(id: String): WidgetDefinition? =
        definitions[id]

    fun getDefinitions(): Collection<WidgetDefinition> =
        definitions.values

    @Deprecated("Use getDefinition() instead.", ReplaceWith("getDefinition(id)"))
    fun getWidgetById(id: String): Class<out Widget>? =
        (definitions[id] as? LegacyWidgetDefinition)?.widgetClass

    @Deprecated("Use getDefinitions() instead.", ReplaceWith("getDefinitions()"))
    fun getWidgets(): Map<String, Class<out Widget>> =
        definitions
            .mapNotNull { (id, definition) -> (definition as? LegacyWidgetDefinition)?.let { id to it.widgetClass } }
            .toMap()

    fun getWidgetInfo(id: String): WidgetInfo? =
        widgetInfo[id]

    fun getAllWidgetInfo(): Map<String, WidgetInfo> =
        widgetInfo

    internal fun purge() {
        definitions.clear()
        widgetInfo.clear()
    }
}
