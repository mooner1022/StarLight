package dev.mooner.starlight.plugincore.widget

enum class WidgetSize(
    val viewType: Int
) {
    Slim(viewType = 0),
    Medium(viewType = 1),
    Large(viewType = 2),
    XLarge(viewType = 3),
    Wrap(viewType = 4),
}