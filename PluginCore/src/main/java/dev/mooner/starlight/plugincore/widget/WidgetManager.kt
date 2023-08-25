package dev.mooner.starlight.plugincore.widget

import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.PluginContext
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.errorTranslated

private val logger = LoggerFactory.logger {  }

class WidgetManager {

    //private val widgets: MutableSet<Widget> = hashSetOf()
    //private val widgets: MutableMap<String, MutableSet<Widget>> = hashMapOf()
    private val widgets: MutableMap<String, Class<out Widget>> = hashMapOf()
    private val widgetInfo: MutableMap<String, WidgetInfo> = hashMapOf()

    fun addWidget(context: PluginContext, widget: Class<out Widget>) {
        logger.verbose { "Adding widget ${widget.name} from plugin ${context.name}" }
        val instance = widget.newInstance()
        val id = instance.id

        if (id in widgets) {
            logger.errorTranslated {
                Locale.ENGLISH { "Rejecting registration of duplicated widget id: $id" }
                Locale.KOREAN  { "중복된 위젯 id를 가진 위젯의 등록이 거부됨: $id" }
            }
            return
        }

        widgets.putIfAbsent(id, widget)
            ?.let { widgets[id] = widget }

        widgetInfo[id] = WidgetInfo(
            pluginName = context.name,
            id = id,
            name = instance.name
        )
    }

    fun getWidgetById(id: String): Class<out Widget>? =
        widgets[id]

    fun getWidgets(): Map<String, Class<out Widget>> =
        widgets

    fun getWidgetInfo(id: String): WidgetInfo? =
        widgetInfo[id]

    fun getAllWidgetInfo(): Map<String, WidgetInfo> =
        widgetInfo

    internal fun purge() {
        widgets.clear()
        widgetInfo.clear()
    }
}