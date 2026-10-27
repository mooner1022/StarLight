package dev.mooner.starlight.plugincore.widget

import dev.mooner.starlight.plugincore.translation.TranslationBuilderBlock
import dev.mooner.starlight.plugincore.translation.translate

fun widget(id: String, block: WidgetBuilder.() -> Unit): WidgetDefinition =
    WidgetBuilder(id).apply(block).build()

@WidgetDsl
class WidgetBuilder internal constructor(
    private val id: String,
) {

    private var nameProvider: (() -> String)? = null
    private var spanSpec: WidgetSpanSpec = WidgetSpanSpec(DEFAULT_SPAN)
    private var content: WidgetContent? = null
    private var preview: WidgetContent? = null

    var autoHeight: Boolean = false

    var devOnly: Boolean = false

    fun name(name: String) {
        nameProvider = { name }
    }

    fun name(block: TranslationBuilderBlock) {
        nameProvider = { translate(block) }
    }

    fun span(default: WidgetSpan, min: WidgetSpan = default, max: WidgetSpan = default) {
        spanSpec = WidgetSpanSpec(default, min, max)
    }

    fun content(block: WidgetContent) {
        content = block
    }

    fun preview(block: WidgetContent) {
        preview = block
    }

    internal fun build(): WidgetDefinition =
        DslWidgetDefinition(
            id = id,
            nameProvider = requireNotNull(nameProvider) { "Widget '$id' must have a name" },
            spanSpec = spanSpec,
            isAutoHeight = autoHeight,
            isDevOnly = devOnly,
            content = requireNotNull(content) { "Widget '$id' must have content" },
            preview = preview,
        )

    private companion object {
        val DEFAULT_SPAN = 4 x 1
    }
}
