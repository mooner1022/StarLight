package com.mooner.starlight.plugincore.widget

class WidgetManager {

    private val widgets: MutableSet<Widget> = hashSetOf()

    fun addWidget(widget: Widget) {
        widgets += widget
    }

    fun getWidgetById(id: String): Widget? = widgets.find { it.id == id }

    fun getWidgets(): List<Widget> = widgets.toList()

    internal fun purge() {
        widgets.clear()
    }
}