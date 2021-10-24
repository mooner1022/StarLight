package com.mooner.starlight.plugincore.widget

class WidgetManager {

    private val widgets: MutableSet<IWidget> = hashSetOf()

    fun addWidget(widget: IWidget) {
        widgets += widget
    }

    fun getWidgetById(id: String): IWidget? = widgets.find { it.id == id }

    fun getWidgets(): List<IWidget> = widgets.toList()
}