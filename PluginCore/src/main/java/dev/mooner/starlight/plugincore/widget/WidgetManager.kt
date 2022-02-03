package dev.mooner.starlight.plugincore.widget

import dev.mooner.starlight.plugincore.logger.Logger

class WidgetManager {

    companion object {
        private const val T = "WidgetManager"
    }

    //private val widgets: MutableSet<Widget> = hashSetOf()
    private val widgets: MutableMap<String, MutableSet<Widget>> = hashMapOf()
    private val widgetIds: MutableSet<String> = hashSetOf()

    fun addWidget(pluginName: String, widget: Widget) {
        if (widget.id in widgetIds) {
            Logger.e(T, "Rejecting registration of duplicated widget id: ${widget.id}")
            return
        }

        if (pluginName in widgets)
            widgets[pluginName]!! += widget
        else
            widgets[pluginName] = hashSetOf(widget)

        widgetIds += widget.id
    }

    fun getWidgetById(id: String): Widget? {
        if (id !in widgetIds) return null

        for ((_, _widgets) in widgets) {
            for (widget in _widgets) {
                if (widget.id == id) return widget
            }
        }
        return null
    }

    fun getWidgets(): Map<String, Set<Widget>> = widgets

    internal fun purge() {
        widgets.clear()
    }
}