package dev.mooner.starlight.plugincore.widget

import kotlinx.serialization.json.JsonElement

abstract class WidgetDefinition internal constructor() {

    abstract val id: String

    abstract val name: String

    abstract val spanSpec: WidgetSpanSpec

    abstract val isAutoHeight: Boolean

    abstract val isDevOnly: Boolean

    abstract val hasSettings: Boolean

    abstract fun newController(): WidgetController

    open fun createSettingsForm(values: Map<String, JsonElement>): WidgetSettingsForm? =
        null

    override fun equals(other: Any?): Boolean =
        other is WidgetDefinition && other.id == id

    override fun hashCode(): Int =
        id.hashCode()
}

internal class DslWidgetDefinition(
    override val id: String,
    private val nameProvider: () -> String,
    override val spanSpec: WidgetSpanSpec,
    override val isAutoHeight: Boolean,
    override val isDevOnly: Boolean,
    val content: WidgetContent,
    val preview: WidgetContent?,
    private val settingsBlock: WidgetSettingsBlock?,
) : WidgetDefinition() {

    override val name: String
        get() = nameProvider()

    override val hasSettings: Boolean
        get() = settingsBlock != null

    override fun createSettingsForm(values: Map<String, JsonElement>): WidgetSettingsForm? =
        settingsBlock?.let { WidgetSettingsForm(it.evaluate(), values) }

    fun settingsOf(values: Map<String, JsonElement>): WidgetSettings =
        settingsBlock?.evaluate()?.toSettings(values) ?: WidgetSettings(values, emptyMap())

    override fun newController(): WidgetController =
        DslWidgetController(this)
}

internal class LegacyWidgetDefinition(
    val widgetClass: Class<out Widget>,
) : WidgetDefinition() {

    private val sample: Widget = widgetClass.getDeclaredConstructor().newInstance()

    override val id: String = sample.id

    override val name: String
        get() = sample.name

    override val spanSpec: WidgetSpanSpec = sample.size.rows.let { rows ->
        WidgetSpanSpec(
            default = LEGACY_COLUMNS x rows,
            min = LEGACY_COLUMNS x rows,
            max = WidgetSpan.MAX_COLUMNS x rows,
        )
    }

    override val isAutoHeight: Boolean = sample.size == WidgetSize.Wrap

    override val isDevOnly: Boolean = false

    override val hasSettings: Boolean = false

    override fun newController(): WidgetController =
        LegacyWidgetController(widgetClass.getDeclaredConstructor().newInstance())

    private companion object {
        const val LEGACY_COLUMNS = 4
    }
}
