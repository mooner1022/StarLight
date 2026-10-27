package dev.mooner.starlight.plugincore.widget

abstract class WidgetDefinition internal constructor() {

    abstract val id: String

    abstract val name: String

    abstract val spanSpec: WidgetSpanSpec

    abstract val isAutoHeight: Boolean

    abstract val isDevOnly: Boolean

    abstract fun newController(): WidgetController

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
) : WidgetDefinition() {

    override val name: String
        get() = nameProvider()

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

    override fun newController(): WidgetController =
        LegacyWidgetController(widgetClass.getDeclaredConstructor().newInstance())

    private companion object {
        const val LEGACY_COLUMNS = 4
    }
}
