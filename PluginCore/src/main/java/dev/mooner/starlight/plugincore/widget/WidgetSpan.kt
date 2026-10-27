package dev.mooner.starlight.plugincore.widget

import kotlinx.serialization.Serializable

@Serializable
data class WidgetSpan(
    val x: Int,
    val y: Int,
) {

    override fun toString(): String =
        "${x}x$y"

    companion object {
        const val MAX_COLUMNS = 8
    }
}

infix fun Int.x(y: Int): WidgetSpan =
    WidgetSpan(this, y)

data class WidgetSpanSpec(
    val default: WidgetSpan,
    val min: WidgetSpan = default,
    val max: WidgetSpan = default,
) {

    init {
        require(min.x in 1..max.x && min.y in 1..max.y) { "Invalid span range: $min..$max" }
        require(default.x in min.x..max.x && default.y in min.y..max.y) { "Default span $default is out of range $min..$max" }
    }

    val isResizable: Boolean
        get() = min != max

    fun resolve(span: WidgetSpan?, columns: Int): WidgetSpan {
        val target = span ?: default
        val maxX = minOf(max.x, columns)
        val minX = minOf(min.x, maxX)
        return WidgetSpan(
            x = target.x.coerceIn(minX, maxX),
            y = target.y.coerceIn(min.y, max.y),
        )
    }
}
