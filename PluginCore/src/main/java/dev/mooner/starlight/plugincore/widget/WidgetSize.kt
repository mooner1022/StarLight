package dev.mooner.starlight.plugincore.widget

enum class WidgetSize(
    val viewType: Int,
    internal val rows: Int,
) {
    Slim(viewType = 0, rows = 1),
    Medium(viewType = 1, rows = 2),
    Large(viewType = 2, rows = 3),
    XLarge(viewType = 3, rows = 7),
    Wrap(viewType = 4, rows = 1),
}
